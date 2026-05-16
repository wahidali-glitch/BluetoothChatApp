package com.example.bluetoothchatapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), BluetoothService.BluetoothListener {

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var tvStatus: TextView
    private lateinit var btnShowDevices: Button
    private lateinit var btnScan: Button
    private lateinit var listDevices: ListView
    private lateinit var chatContainer: LinearLayout
    private lateinit var scrollChat: ScrollView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button
    private lateinit var btnAttach: Button
    private lateinit var progressFile: ProgressBar

    // ── Bluetooth ─────────────────────────────────────────────────────────────
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var bluetoothService: BluetoothService

    // ── Device list ───────────────────────────────────────────────────────────
    private val deviceNames   = ArrayList<String>()
    private val devices       = ArrayList<BluetoothDevice>()
    private val seenAddresses = HashSet<String>()

    // ── Request codes ─────────────────────────────────────────────────────────
    private val REQUEST_FILE_PICK      = 200
    private val REQUEST_BT_PERMISSIONS = 100

    // ── State ─────────────────────────────────────────────────────────────────
    private var isConnected = false

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus       = findViewById(R.id.tvStatus)
        btnShowDevices = findViewById(R.id.btnShowDevices)
        btnScan        = findViewById(R.id.btnScan)
        listDevices    = findViewById(R.id.listDevices)
        chatContainer  = findViewById(R.id.chatContainer)
        scrollChat     = findViewById(R.id.scrollChat)
        etMessage      = findViewById(R.id.etMessage)
        btnSend        = findViewById(R.id.btnSend)
        btnAttach      = findViewById(R.id.btnAttach)
        progressFile   = findViewById(R.id.progressFile)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        bluetoothService = BluetoothService(this, this)

        if (bluetoothAdapter == null) {
            updateStatus("Bluetooth not supported", connected = false)
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            return
        }

        requestBluetoothPermissions()
        setupButtons()
    }

    // ── Button wiring ─────────────────────────────────────────────────────────
    private fun setupButtons() {

        btnShowDevices.setOnClickListener {
            bluetoothService.stopDiscovery()
            showPairedDevices()
        }

        btnScan.text = "Start Server"
        btnScan.setOnClickListener {
            if (btnScan.text == "Start Server") {
                updateStatus("Waiting for connection...", connected = false)
                bluetoothService.startServer()
                btnScan.text = "Scan Devices"
            } else {
                seenAddresses.clear()
                deviceNames.clear()
                devices.clear()
                listDevices.adapter = null
                bluetoothService.startDiscovery()
                btnScan.text = "Start Server"
            }
        }

        listDevices.setOnItemClickListener { _, _, position, _ ->
            val selectedDevice = devices[position]
            updateStatus("Connecting to ${deviceNames[position].split("\n")[0]}...", connected = false)
            bluetoothService.connectToDevice(selectedDevice)
        }

        btnSend.setOnClickListener {
            val message = etMessage.text.toString().trim()
            if (message.isEmpty()) {
                Toast.makeText(this, "Type a message first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isConnected) {
                Toast.makeText(this, "Not connected to any device", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            bluetoothService.sendMessage(message)
            addTextBubble(message, isSent = true, delivered = false)
            etMessage.text.clear()
        }

        btnAttach.setOnClickListener {
            if (!isConnected) {
                Toast.makeText(this, "Connect to a device first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(intent, "Select file to send"), REQUEST_FILE_PICK)
        }
    }

    // ── File picker result ────────────────────────────────────────────────────
    @Deprecated("Kept for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FILE_PICK && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            val fileName  = getFileName(uri)
            val fileBytes = readFileBytes(uri) ?: run {
                Toast.makeText(this, "Could not read file", Toast.LENGTH_SHORT).show()
                return
            }
            addFileBubble(fileName, fileBytes.size.toLong(), isSent = true, fileBytes = null)
            runOnUiThread {
                progressFile.visibility = ProgressBar.VISIBLE
                progressFile.progress   = 0
            }
            bluetoothService.sendFile(fileName, fileBytes)
        }
    }

    // ── Paired devices ────────────────────────────────────────────────────────
    @SuppressLint("MissingPermission")
    private fun showPairedDevices() {
        if (!hasBluetoothConnectPermission()) {
            Toast.makeText(this, "Bluetooth permission required", Toast.LENGTH_SHORT).show()
            requestBluetoothPermissions()
            return
        }
        val paired = bluetoothAdapter?.bondedDevices
        deviceNames.clear(); devices.clear(); seenAddresses.clear()

        if (paired.isNullOrEmpty()) {
            updateStatus("No paired devices found", connected = false)
            Toast.makeText(this, "No paired devices. Pair in Bluetooth settings first.", Toast.LENGTH_LONG).show()
            return
        }
        for (device in paired) {
            val name = device.name ?: "Unknown"
            deviceNames.add("$name\n${device.address}")
            devices.add(device)
            seenAddresses.add(device.address)
        }
        listDevices.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames)
        updateStatus("${devices.size} paired device(s) found", connected = false)
    }

    // ── BluetoothService callbacks ────────────────────────────────────────────

    override fun onStatusChanged(status: String) {
        runOnUiThread {
            val connected = status.startsWith("Connected")
            isConnected   = connected
            updateStatus(status, connected)
            if (status == "Disconnected") {
                scrollChat.postDelayed({
                    if (!isConnected) {
                        Toast.makeText(this, "Disconnected. Attempting reconnect...", Toast.LENGTH_SHORT).show()
                        bluetoothService.reconnect()
                    }
                }, 4000)
            }
        }
    }

    override fun onMessageReceived(message: String) {
        runOnUiThread { addTextBubble(message, isSent = false, delivered = true) }
    }

    override fun onDeliveryReceipt(originalMessage: String) {
        runOnUiThread { markLastSentDelivered(originalMessage) }
    }

    override fun onFileReceiveStart(fileName: String, fileSize: Long) {
        runOnUiThread {
            progressFile.visibility = ProgressBar.VISIBLE
            progressFile.progress   = 0
            Toast.makeText(this, "Receiving: $fileName (${fileSize / 1024} KB)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onFileChunkReceived(bytesReceived: Long, totalBytes: Long) {
        runOnUiThread {
            val pct = if (totalBytes > 0) ((bytesReceived * 100) / totalBytes).toInt() else 0
            progressFile.progress = pct
        }
    }

    override fun onFileReceiveComplete(fileName: String, data: ByteArray) {
        // Save file to device storage first, then show bubble with open action
        val savedUri = saveFileToStorage(fileName, data)
        runOnUiThread {
            progressFile.visibility = ProgressBar.GONE
            progressFile.progress   = 0
            addFileBubble(
                fileName  = fileName,
                fileSize  = data.size.toLong(),
                isSent    = false,
                fileBytes = data,
                savedUri  = savedUri
            )
            if (savedUri != null) {
                Toast.makeText(this, "✅ Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDeviceFound(device: BluetoothDevice) {
        runOnUiThread {
            if (!hasBluetoothConnectPermission()) return@runOnUiThread
            val address = device.address
            if (seenAddresses.contains(address)) return@runOnUiThread
            seenAddresses.add(address)
            @SuppressLint("MissingPermission")
            val name = try { device.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" }
            deviceNames.add("$name\n$address")
            devices.add(device)
            listDevices.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames)
        }
    }

    override fun onScanFinished() {
        runOnUiThread {
            updateStatus("Scan complete — ${devices.size} device(s) found", connected = false)
            Toast.makeText(this, "Scan finished", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onFileSendProgress(bytesSent: Long, totalBytes: Long) {
        runOnUiThread {
            val pct = if (totalBytes > 0) ((bytesSent * 100) / totalBytes).toInt() else 0
            progressFile.progress = pct
            if (pct >= 100) {
                progressFile.postDelayed({
                    progressFile.visibility = ProgressBar.GONE
                    progressFile.progress   = 0
                }, 800)
            }
        }
    }

    // ── Save received file to Downloads folder ────────────────────────────────
    /**
     * Saves bytes to Downloads/BlinkChat/filename.
     * Returns a content Uri that can be used to open the file.
     */
    private fun saveFileToStorage(fileName: String, data: ByteArray): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ — use MediaStore (no storage permission needed)
                val mimeType = getMimeType(fileName)
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/BlinkChat")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    resolver.openOutputStream(it)?.use { os -> os.write(data) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(it, values, null, null)
                }
                uri
            } else {
                // Android 9 and below — write directly to Downloads
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "BlinkChat"
                )
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(data) }
                FileProvider.getUriForFile(this, "$packageName.provider", file)
            }
        } catch (e: Exception) {
            null
        }
    }

    // ── Open a saved file with the system viewer ──────────────────────────────
    private fun openFile(uri: Uri, fileName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(fileName))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "Open $fileName with..."))
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open this file type", Toast.LENGTH_SHORT).show()
        }
    }

    // ── MIME type helper ──────────────────────────────────────────────────────
    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf"              -> "application/pdf"
            "jpg", "jpeg"     -> "image/jpeg"
            "png"             -> "image/png"
            "gif"             -> "image/gif"
            "webp"            -> "image/webp"
            "mp4"             -> "video/mp4"
            "mp3"             -> "audio/mpeg"
            "doc"             -> "application/msword"
            "docx"            -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls"             -> "application/vnd.ms-excel"
            "xlsx"            -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "txt"             -> "text/plain"
            "zip"             -> "application/zip"
            else              -> "application/octet-stream"
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun updateStatus(status: String, connected: Boolean) {
        tvStatus.text = "Status: $status"
        tvStatus.setTextColor(
            if (connected) 0xFF1DE9B6.toInt() else 0xFF90A4AE.toInt()
        )
    }

    private fun addTextBubble(message: String, isSent: Boolean, delivered: Boolean) {
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 6, 8, 6) }
            gravity = if (isSent) Gravity.END else Gravity.START
        }

        val bubble = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(0xFFECEFF1.toInt())
            setPadding(28, 16, 28, 16)
            setBackgroundColor(if (isSent) 0xFF00332A.toInt() else 0xFF1A2840.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity   = if (isSent) Gravity.END else Gravity.START
                maxWidth  = resources.displayMetrics.widthPixels * 3 / 4
            }
            tag = "bubble_$message"
        }

        val meta = TextView(this).apply {
            val tick = if (isSent) (if (delivered) " ✓✓" else " ✓") else ""
            text = "$time$tick"
            textSize = 9f
            setTextColor(0xFF546E7A.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isSent) Gravity.END else Gravity.START
                setMargins(4, 2, 4, 0)
            }
            tag = "meta_$message"
        }

        wrapper.addView(bubble)
        wrapper.addView(meta)
        chatContainer.addView(wrapper)
        scrollChat.post { scrollChat.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun markLastSentDelivered(originalMessage: String) {
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        for (i in chatContainer.childCount - 1 downTo 0) {
            val wrapper = chatContainer.getChildAt(i) as? LinearLayout ?: continue
            val meta    = wrapper.findViewWithTag<TextView>("meta_$originalMessage") ?: continue
            meta.text   = "$time ✓✓"
            break
        }
    }

    /**
     * Add a file bubble.
     * - Images   → thumbnail shown, tap opens full image
     * - PDF/docs → file card shown, tap opens with system viewer
     * - savedUri → set when file was received and saved; null when we just sent
     */
    private fun addFileBubble(
        fileName: String,
        fileSize: Long,
        isSent: Boolean,
        fileBytes: ByteArray?,
        savedUri: Uri? = null
    ) {
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(8, 6, 8, 6) }
            gravity = if (isSent) Gravity.END else Gravity.START
        }

        val isImage = fileName.substringAfterLast('.', "").lowercase()
            .let { it == "jpg" || it == "jpeg" || it == "png" || it == "gif" || it == "webp" }

        if (isImage && fileBytes != null) {
            // ── Image thumbnail ───────────────────────────────────────────────
            val bmp = BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.size)
            if (bmp != null) {
                val iv = ImageView(this).apply {
                    setImageBitmap(bmp)
                    adjustViewBounds = true
                    layoutParams = LinearLayout.LayoutParams(
                        resources.displayMetrics.widthPixels * 2 / 3,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { gravity = if (isSent) Gravity.END else Gravity.START }
                    setBackgroundColor(if (isSent) 0xFF00332A.toInt() else 0xFF1A2840.toInt())
                    setPadding(8, 8, 8, 8)
                    // Tap to open full image
                    if (savedUri != null) {
                        setOnClickListener { openFile(savedUri, fileName) }
                    }
                }
                wrapper.addView(iv)
            }
        } else {
            // ── Generic file card (PDF, DOC, ZIP, etc.) ───────────────────────
            val sizeKb = fileSize / 1024
            val card = TextView(this).apply {
                text = "📎 $fileName\n$sizeKb KB"
                textSize = 13f
                setTextColor(0xFF00E5FF.toInt())
                setPadding(28, 16, 28, 16)
                setBackgroundColor(if (isSent) 0xFF00332A.toInt() else 0xFF1A2840.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = if (isSent) Gravity.END else Gravity.START }

                // ── TAP TO OPEN — only for received files that were saved ─────
                if (savedUri != null) {
                    setTextColor(0xFF00E5FF.toInt())
                    // Add "Tap to open" hint
                    text = "📎 $fileName\n$sizeKb KB\n\n👆 Tap to open"
                    setOnClickListener { openFile(savedUri, fileName) }
                }
            }
            wrapper.addView(card)
        }

        val meta = TextView(this).apply {
            text = "${time} ${if (isSent) "✓" else "📥 Saved"}"
            textSize = 9f
            setTextColor(if (!isSent && savedUri != null) 0xFF1DE9B6.toInt() else 0xFF546E7A.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isSent) Gravity.END else Gravity.START
                setMargins(4, 2, 4, 0)
            }
        }
        wrapper.addView(meta)
        chatContainer.addView(wrapper)
        scrollChat.post { scrollChat.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // ── File utilities ────────────────────────────────────────────────────────

    private fun getFileName(uri: Uri): String {
        var name = "file_${System.currentTimeMillis()}"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx)
        }
        return name
    }

    private fun readFileBytes(uri: Uri): ByteArray? = try {
        contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) { null }

    // ── Permissions ───────────────────────────────────────────────────────────

    private fun requestBluetoothPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
            perms += Manifest.permission.BLUETOOTH_ADVERTISE
        } else {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), REQUEST_BT_PERMISSIONS)
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        else true

    // ── Cleanup ───────────────────────────────────────────────────────────────
    override fun onDestroy() {
        super.onDestroy()
        bluetoothService.stopDiscovery()
        bluetoothService.stopAll()
    }
}