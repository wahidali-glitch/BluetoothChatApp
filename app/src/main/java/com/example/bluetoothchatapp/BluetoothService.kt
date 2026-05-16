package com.example.bluetoothchatapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

/**
 * BluetoothService — Bluetooth Classic RFCOMM communication.
 *
 * PROTOCOL (length-prefixed, every field exact):
 *
 *   Each packet:
 *   ┌────────────┬──────────────┬──────────────────────────┐
 *   │  1 byte    │  4 bytes     │  N bytes                 │
 *   │  TYPE      │  LENGTH (int)│  PAYLOAD                 │
 *   └────────────┴──────────────┴──────────────────────────┘
 *
 *   Types:
 *     0x01  TEXT      payload = UTF-8 string
 *     0x02  FILE_INFO payload = [8-byte long fileSize][UTF-8 fileName]
 *     0x03  FILE_CHUNK payload = raw bytes (up to CHUNK_SIZE)
 *     0x04  FILE_END  payload = empty (length = 0)
 *     0x05  ACK       payload = UTF-8 original message
 *
 *   DataInputStream.readFully() guarantees EXACT byte counts — no corruption.
 */
class BluetoothService(
    private val context: Context,
    private val listener: BluetoothListener
) {

    companion object {
        const val TYPE_TEXT: Byte       = 0x01
        const val TYPE_FILE_INFO: Byte  = 0x02
        const val TYPE_FILE_CHUNK: Byte = 0x03
        const val TYPE_FILE_END: Byte   = 0x04
        const val TYPE_ACK: Byte        = 0x05
        const val CHUNK_SIZE            = 8192   // 8 KB per chunk
    }

    interface BluetoothListener {
        fun onStatusChanged(status: String)
        fun onMessageReceived(message: String)
        fun onDeliveryReceipt(originalMessage: String)
        fun onFileReceiveStart(fileName: String, fileSize: Long)
        fun onFileChunkReceived(bytesReceived: Long, totalBytes: Long)
        fun onFileReceiveComplete(fileName: String, data: ByteArray)
        fun onDeviceFound(device: BluetoothDevice)
        fun onScanFinished()
        fun onFileSendProgress(bytesSent: Long, totalBytes: Long)
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var serverThread: ServerThread? = null
    private var clientThread: ClientThread? = null
    private var connectedThread: ConnectedThread? = null
    private var lastDevice: BluetoothDevice? = null
    private var isServer = false
    private val appName = "BluetoothChatApp"
    private val appUUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")

    // ── Discovery receiver ────────────────────────────────────────────────────
    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let { listener.onDeviceFound(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> listener.onScanFinished()
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun startServer() {
        isServer = true
        stopAll()
        serverThread = ServerThread().also { it.start() }
    }

    fun connectToDevice(device: BluetoothDevice) {
        isServer = false
        lastDevice = device
        stopAll()
        clientThread = ClientThread(device).also { it.start() }
    }

    /**
     * Send a text message.
     * Packet: [0x01][4-byte length][UTF-8 bytes]
     */
    fun sendMessage(message: String) {
        val payload = message.toByteArray(Charsets.UTF_8)
        connectedThread?.writePacket(TYPE_TEXT, payload)
    }

    /**
     * Send a file in chunks.
     * 1. FILE_INFO  packet: [0x02][4-byte len][ 8-byte fileSize + fileName bytes ]
     * 2. FILE_CHUNK packets: [0x03][4-byte len][ up to CHUNK_SIZE bytes ]
     * 3. FILE_END   packet: [0x04][4-byte 0]
     */
    fun sendFile(fileName: String, fileBytes: ByteArray) {
        Thread {
            try {
                // 1 — FILE_INFO
                val nameBytes = fileName.toByteArray(Charsets.UTF_8)
                val infoPayload = ByteArray(8 + nameBytes.size)
                val size = fileBytes.size.toLong()
                for (i in 0..7) infoPayload[i] = ((size shr (56 - i * 8)) and 0xFF).toByte()
                nameBytes.copyInto(infoPayload, 8)
                connectedThread?.writePacket(TYPE_FILE_INFO, infoPayload)

                // 2 — FILE_CHUNKs
                var offset = 0
                val total = fileBytes.size.toLong()
                while (offset < fileBytes.size) {
                    val end = minOf(offset + CHUNK_SIZE, fileBytes.size)
                    val chunk = fileBytes.copyOfRange(offset, end)
                    connectedThread?.writePacket(TYPE_FILE_CHUNK, chunk)
                    offset = end
                    listener.onFileSendProgress(offset.toLong(), total)
                    Thread.sleep(5)
                }

                // 3 — FILE_END
                connectedThread?.writePacket(TYPE_FILE_END, ByteArray(0))

            } catch (e: Exception) {
                listener.onStatusChanged("File send error: ${e.message}")
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (!hasConnectPermission()) {
            listener.onStatusChanged("Permission required for scanning")
            return
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, filter)
        bluetoothAdapter?.cancelDiscovery()
        bluetoothAdapter?.startDiscovery()
        listener.onStatusChanged("Scanning for devices...")
    }

    fun stopDiscovery() {
        try { context.unregisterReceiver(discoveryReceiver) } catch (_: Exception) {}
        if (hasConnectPermission()) {
            @SuppressLint("MissingPermission")
            bluetoothAdapter?.cancelDiscovery()
        }
    }

    fun reconnect() {
        val device = lastDevice
        if (device != null) {
            listener.onStatusChanged("Reconnecting...")
            if (isServer) startServer() else connectToDevice(device)
        } else {
            listener.onStatusChanged("No previous device to reconnect")
        }
    }

    fun stopAll() {
        serverThread?.cancel(); serverThread = null
        clientThread?.cancel(); clientThread = null
        connectedThread?.cancel(); connectedThread = null
    }

    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        else true

    // ── Server Thread ─────────────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    private inner class ServerThread : Thread() {
        private var serverSocket: BluetoothServerSocket? = null
        override fun run() {
            if (!hasConnectPermission()) { listener.onStatusChanged("Permission missing"); return }
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(appName, appUUID)
                listener.onStatusChanged("Waiting for connection...")
                val socket = serverSocket?.accept()
                if (socket != null) {
                    listener.onStatusChanged("Connected")
                    serverSocket?.close()
                    startConnectedThread(socket)
                }
            } catch (e: IOException) {
                listener.onStatusChanged("Server error: ${e.message}")
            }
        }
        fun cancel() { try { serverSocket?.close() } catch (_: IOException) {} }
    }

    // ── Client Thread ─────────────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    private inner class ClientThread(private val device: BluetoothDevice) : Thread() {
        private var socket: BluetoothSocket? = null
        override fun run() {
            if (!hasConnectPermission()) { listener.onStatusChanged("Permission missing"); return }
            try {
                listener.onStatusChanged("Connecting...")
                bluetoothAdapter?.cancelDiscovery()
                socket = device.createRfcommSocketToServiceRecord(appUUID)
                socket!!.connect()
                listener.onStatusChanged("Connected to ${device.name ?: "device"}")
                startConnectedThread(socket!!)
            } catch (e: IOException) {
                listener.onStatusChanged("Connection failed — retrying in 3s...")
                try { socket?.close() } catch (_: IOException) {}
                try {
                    Thread.sleep(3000)
                    socket = device.createRfcommSocketToServiceRecord(appUUID)
                    socket!!.connect()
                    listener.onStatusChanged("Connected to ${device.name ?: "device"}")
                    startConnectedThread(socket!!)
                } catch (e2: IOException) {
                    listener.onStatusChanged("Reconnection failed: ${e2.message}")
                }
            }
        }
        fun cancel() { try { socket?.close() } catch (_: IOException) {} }
    }

    private fun startConnectedThread(socket: BluetoothSocket) {
        connectedThread?.cancel()
        connectedThread = ConnectedThread(socket).also { it.start() }
    }

    // ── Connected Thread ──────────────────────────────────────────────────────
    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {

        private val din  = DataInputStream(socket.inputStream)
        private val dout = DataOutputStream(socket.outputStream)

        // File reassembly state
        private var receivingFile = false
        private var fileName      = ""
        private var fileSize      = 0L
        private var fileReceived  = 0L
        private val fileBuffer    = ByteArrayOutputStream()

        override fun run() {
            while (true) {
                try {
                    // ── Read header: 1-byte type + 4-byte payload length ──────
                    val type   = din.readByte()          // blocks until 1 byte arrives
                    val length = din.readInt()           // blocks until 4 bytes arrive
                    val payload = ByteArray(length)
                    if (length > 0) din.readFully(payload) // blocks until ALL bytes arrive

                    when (type) {

                        // ── TEXT ─────────────────────────────────────────────
                        TYPE_TEXT -> {
                            val message = String(payload, Charsets.UTF_8)
                            listener.onMessageReceived(message)
                            // Send ACK
                            writePacket(TYPE_ACK, payload)
                        }

                        // ── FILE INFO ────────────────────────────────────────
                        TYPE_FILE_INFO -> {
                            fileSize = 0L
                            for (i in 0..7) fileSize = (fileSize shl 8) or (payload[i].toLong() and 0xFF)
                            fileName = String(payload, 8, payload.size - 8, Charsets.UTF_8)
                            fileReceived = 0L
                            fileBuffer.reset()
                            receivingFile = true
                            listener.onFileReceiveStart(fileName, fileSize)
                        }

                        // ── FILE CHUNK ───────────────────────────────────────
                        TYPE_FILE_CHUNK -> {
                            if (receivingFile) {
                                fileBuffer.write(payload)
                                fileReceived += payload.size
                                listener.onFileChunkReceived(fileReceived, fileSize)
                            }
                        }

                        // ── FILE END ─────────────────────────────────────────
                        TYPE_FILE_END -> {
                            if (receivingFile) {
                                receivingFile = false
                                listener.onFileReceiveComplete(fileName, fileBuffer.toByteArray())
                                fileBuffer.reset()
                            }
                        }

                        // ── ACK ──────────────────────────────────────────────
                        TYPE_ACK -> {
                            listener.onDeliveryReceipt(String(payload, Charsets.UTF_8))
                        }
                    }

                } catch (e: IOException) {
                    listener.onStatusChanged("Disconnected")
                    break
                }
            }
        }

        /**
         * Write a length-prefixed packet:
         * [1-byte type][4-byte payload length][payload bytes]
         */
        fun writePacket(type: Byte, payload: ByteArray) {
            try {
                synchronized(dout) {
                    dout.writeByte(type.toInt())
                    dout.writeInt(payload.size)
                    if (payload.isNotEmpty()) dout.write(payload)
                    dout.flush()
                }
            } catch (e: IOException) {
                listener.onStatusChanged("Send failed: ${e.message}")
            }
        }

        fun cancel() { try { socket.close() } catch (_: IOException) {} }
    }
}