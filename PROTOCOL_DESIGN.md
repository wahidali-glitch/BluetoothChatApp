# 📡 Protocol Design Document
## BlinkChat — Bluetooth Chat Application

<div align="center">

| Field | Detail |
|---|---|
| **Course** | Computer Networks & Data Communications (CNDC) |
| **Section** | BSCS-6B |
| **Institute** | SZABIST Islamabad |
| **Author** | Wahid Ali |
| **Technology** | Bluetooth Classic — RFCOMM |
| **Language** | Kotlin (Android) |

</div>

---

## 1. Overview

BlinkChat uses **Bluetooth Classic RFCOMM** (Radio Frequency Communication) to establish a reliable, full-duplex byte-stream channel between two Android devices. RFCOMM emulates a serial port over Bluetooth, providing an ordered, error-checked stream — similar to TCP but without requiring any network infrastructure.

All communication occurs over a **single persistent socket** identified by a fixed UUID:

```
UUID: 8ce255c0-200a-11e0-ac64-0800200c9a66
Service Name: BluetoothChatApp
```

---

## 2. Connection Model

### 2.1 Roles

| Role | Behaviour |
|---|---|
| **Server** | Calls `listenUsingRfcommWithServiceRecord()` and blocks on `accept()` waiting for one incoming connection |
| **Client** | Calls `createRfcommSocketToServiceRecord()` and `connect()` to initiate the connection |

### 2.2 Handshake Flow

```
Server Device                        Client Device
      │                                    │
      │── listenUsingRfcomm(UUID) ──▶      │
      │   (blocking — waits)               │── createRfcommSocket(UUID)
      │                                    │── connect()  ──────────────▶
      │◀────────────── accept() ───────────│
      │                                    │
      │         RFCOMM CHANNEL OPEN        │
      │◀══════════ packets flow ══════════▶│
      │                                    │
      │         (either side disconnects)  │
      │── socket.close() ─────────────────▶│
```

### 2.3 Reconnection Strategy

- **Client** automatically retries once after **3 seconds** if the initial `connect()` fails.
- If the socket drops mid-session, `onStatusChanged("Disconnected")` fires and the app waits **4 seconds** before attempting a full reconnect using the last known device.

---

## 3. Packet Structure

Every packet transmitted over the RFCOMM stream follows a strict **length-prefixed** format:

```
┌─────────────┬──────────────────┬──────────────────────────────┐
│   1 byte    │     4 bytes      │           N bytes            │
│    TYPE     │  PAYLOAD LENGTH  │           PAYLOAD            │
│  (byte)     │   (int, big-     │     (format per type)        │
│             │    endian)       │                              │
└─────────────┴──────────────────┴──────────────────────────────┘
```

- **TYPE** — 1-byte identifier that tells the receiver how to interpret the payload.
- **PAYLOAD LENGTH** — A 4-byte big-endian signed integer giving the exact byte count of the payload that follows. This allows the receiver to call `readFully()` and wait for every byte before processing.
- **PAYLOAD** — Variable-length data whose format depends on TYPE.

### Why Length-Prefixed?

Bluetooth RFCOMM is a stream protocol — it has no built-in message boundaries. Without length prefixes, a 100 KB image arrives in many small TCP-like segments. The receiver cannot know where one "message" ends and the next begins. Length prefixing solves this by telling the receiver exactly how many bytes to read before dispatching.

---

## 4. Packet Types

### 4.1 TEXT — `0x01`

Sent when the user types and submits a chat message.

```
┌──────┬──────────────┬─────────────────────────────┐
│ 0x01 │  Length (4B) │  UTF-8 encoded message text  │
└──────┴──────────────┴─────────────────────────────┘
```

**Example** — Sending "hello" (5 bytes):
```
Hex: 01  00 00 00 05  68 65 6C 6C 6F
      ↑   ←length→   ←   "hello"  →
    TYPE     5          payload
```

**On Receive:**
1. Receiver decodes payload as UTF-8 string.
2. Displays message bubble on the left side of the screen.
3. Immediately sends an ACK (`0x05`) packet back.

---

### 4.2 FILE_INFO — `0x02`

Sent once before file chunks begin. Announces the file name and total size to the receiver so it can prepare storage and show a progress bar.

```
┌──────┬──────────────┬──────────────────────┬─────────────────────┐
│ 0x02 │  Length (4B) │  File Size (8 bytes) │  File Name (UTF-8)  │
│      │              │  (long, big-endian)  │  (remaining bytes)  │
└──────┴──────────────┴──────────────────────┴─────────────────────┘
```

**Payload Breakdown:**

| Bytes | Field | Description |
|---|---|---|
| 0 – 7 | File Size | 64-bit big-endian signed long — total file size in bytes |
| 8 – N | File Name | UTF-8 encoded file name string (no null terminator) |

**Example** — Sending "report.pdf" (128,000 bytes):
```
Payload bytes 0–7:  00 00 00 00 00 01 F4 00   → 128,000 (decimal)
Payload bytes 8–17: 72 65 70 6F 72 74 2E 70 64 66  → "report.pdf"
```

---

### 4.3 FILE_CHUNK — `0x03`

Sent repeatedly after FILE_INFO, carrying raw file bytes in blocks of up to **8192 bytes (8 KB)**.

```
┌──────┬──────────────┬──────────────────────────────────┐
│ 0x03 │  Length (4B) │  Raw file bytes (up to 8192 B)   │
└──────┴──────────────┴──────────────────────────────────┘
```

- The sender sleeps **5 ms** between chunks to prevent socket buffer overflow.
- The receiver appends each chunk to a `ByteArrayOutputStream`.
- Progress is calculated as: `progress = (bytesReceived / totalBytes) × 100`

**Chunking Example** — 20,000 byte file:
```
Chunk 1: bytes 0     – 8191   (8192 bytes)
Chunk 2: bytes 8192  – 16383  (8192 bytes)
Chunk 3: bytes 16384 – 19999  (3616 bytes)
```

---

### 4.4 FILE_END — `0x04`

Sent once after all chunks have been transmitted. Signals the receiver to finalize the file.

```
┌──────┬──────────────┐
│ 0x04 │  00 00 00 00 │   (length = 0, no payload)
└──────┴──────────────┘
```

**On Receive:**
1. Receiver calls `ByteArrayOutputStream.toByteArray()` to get the complete file.
2. File is saved to `Downloads/BlinkChat/<filename>` using MediaStore (Android 10+) or FileProvider (Android 9 and below).
3. A file bubble with "👆 Tap to open" is shown in the chat.
4. The system viewer (PDF reader, image gallery, etc.) is launched on tap.

---

### 4.5 ACK (Delivery Receipt) — `0x05`

Automatically sent by the receiver immediately after receiving a TEXT packet. The payload is a copy of the original message, allowing the sender to match it and update the tick indicator.

```
┌──────┬──────────────┬─────────────────────────────────────────┐
│ 0x05 │  Length (4B) │  UTF-8 copy of the original message     │
└──────┴──────────────┴─────────────────────────────────────────┘
```

**Delivery Indicator States:**

| Display | Meaning |
|---|---|
| ✓ (single grey tick) | Message sent over Bluetooth socket |
| ✓✓ (double grey tick) | ACK received — other device confirmed receipt |

---

## 5. Complete File Transfer Flow

```
Sender                                  Receiver
  │                                         │
  │── [0x02] FILE_INFO ───────────────────▶ │  fileName="photo.jpg", size=204,800
  │                                         │  → shows progress bar at 0%
  │── [0x03] FILE_CHUNK (8192 B) ─────────▶ │  → 4% received
  │── [0x03] FILE_CHUNK (8192 B) ─────────▶ │  → 8% received
  │   …  (25 chunks total for 204 KB)  …    │  → progress bar fills
  │── [0x03] FILE_CHUNK (last chunk) ──────▶│  → 99% received
  │── [0x04] FILE_END ─────────────────────▶│  → 100%
  │                                         │  → saves to Downloads/BlinkChat/
  │                                         │  → shows image thumbnail in chat
  │                                         │  → toast: "✅ Saved: photo.jpg"
```

---

## 6. Complete Text Message Flow

```
Sender                                  Receiver
  │                                         │
  │── [0x01] TEXT "hello" ────────────────▶ │
  │   → shows bubble with ✓ tick            │  → shows bubble on left side
  │                                         │── [0x05] ACK "hello" ──────▶
  │◀── ACK received ────────────────────────│
  │   → updates tick to ✓✓                  │
```

---

## 7. Reading Strategy — `DataInputStream.readFully()`

The core of the protocol's reliability is the use of `DataInputStream` with `readFully()`:

```kotlin
val type   = din.readByte()       // blocks until exactly 1 byte arrives
val length = din.readInt()        // blocks until exactly 4 bytes arrive
val payload = ByteArray(length)
din.readFully(payload)            // blocks until ALL 'length' bytes arrive
```

This guarantees that:
- No partial reads occur
- Large files are never corrupted
- The type byte of packet N is never mistaken for data of packet N-1

---

## 8. Supported File Types

| Extension | MIME Type | Opens With |
|---|---|---|
| `.jpg`, `.jpeg`, `.png` | `image/jpeg`, `image/png` | Gallery / Photos |
| `.gif`, `.webp` | `image/gif`, `image/webp` | Gallery |
| `.pdf` | `application/pdf` | PDF Viewer |
| `.doc`, `.docx` | `application/msword` | Word / Docs |
| `.xls`, `.xlsx` | `application/vnd.ms-excel` | Excel / Sheets |
| `.mp4` | `video/mp4` | Video Player |
| `.mp3` | `audio/mpeg` | Music Player |
| `.zip` | `application/zip` | File Manager |
| Any other | `application/octet-stream` | System chooser |

---

## 9. Security Considerations

- The RFCOMM channel is encrypted by the Bluetooth stack at the hardware level using **E0 cipher** (Bluetooth 2.x) or **AES-CCM** (Bluetooth 4.x+).
- Device pairing requires physical proximity and user confirmation on both devices — preventing unauthorised connections.
- `FileProvider` is used instead of raw file URIs to prevent path traversal attacks when opening files.
- The app does not transmit any data outside the local Bluetooth piconet — no internet, no cloud, no server.

---

## 10. Limitations

| Limitation | Detail |
|---|---|
| Range | Bluetooth Classic range ~10 metres (Class 2 device) |
| One connection | App supports one peer at a time |
| File size | Practically limited to ~50 MB due to in-memory byte array assembly |
| iOS | Not supported — Bluetooth Classic RFCOMM is blocked on iOS |
| Discovery | `startDiscovery()` only finds discoverable devices; pairing must be done in OS settings for reliable connection |

---

*Prepared for CNDC Assignment — BSCS-6B — SZABIST Islamabad*  
*Author: Wahid Ali | GitHub: [wahidali-glitch/BluetoothChatApp](https://github.com/wahidali-glitch/BluetoothChatApp)*
