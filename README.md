# Bluetooth Chat App

A modern Android Bluetooth Chat Application built with **Kotlin** and **Bluetooth Classic**.  
The application allows two Android devices to connect over Bluetooth, exchange real-time text messages, and transfer files such as images, PDFs, and documents.

This project demonstrates practical device-to-device communication using Android Bluetooth APIs, real-time stream handling, file transfer through byte chunks, and a clean messaging-style user interface.

---

## Project Overview

Bluetooth Chat App is an Android application designed to enable direct communication between two nearby Android devices without using the internet.

The app works by creating a Bluetooth connection between two paired devices. After connection, users can send and receive text messages in real time and transfer files through the Bluetooth socket connection.

The interface is designed like a real chat application, with sent and received messages displayed separately, timestamps, a device list, connection status, file attachment option, and transfer progress indicator.

---

## Main Purpose

The purpose of this project is to demonstrate:

- Bluetooth device discovery
- Bluetooth socket connection
- Real-time text communication
- File transfer between Android devices
- Android permission handling
- Stream-based data transmission
- Chat-style mobile UI design
- Testing on real Android devices

---

## Key Features

### Device Discovery and Connection

- Shows paired Bluetooth devices
- Allows the user to select a device
- Connects two Android devices using Bluetooth Classic
- Displays connection status clearly
- Handles connected, disconnected, and scanning states

### Real-Time Text Messaging

- Sends text messages from one device to another
- Receives messages instantly on the connected device
- Displays sent messages on the right side
- Displays received messages on the left side
- Shows timestamps with messages
- Uses a scrollable chat area

### File Transfer

- Allows the user to attach files
- Supports sending images, PDFs, and other files
- Transfers files through Bluetooth streams
- Sends file data in byte chunks
- Shows file transfer progress
- Handles file sending and receiving between two devices

### User Interface

- Clean messaging-style layout
- Modern chat screen design
- Device list section
- Connection status display
- Message input field
- Attachment button
- Send button
- Progress bar for file transfer
- Mobile-friendly design

---

## Technology Stack

| Category | Technology |
|---|---|
| Programming Language | Kotlin |
| Platform | Android |
| IDE | Android Studio |
| UI Design | XML Layouts |
| Communication | Bluetooth Classic |
| Build System | Gradle Kotlin DSL |
| Minimum SDK | Android API 24 |
| Testing Devices | Real Android Phones |

---

## How the App Works

The app uses Bluetooth Classic communication to connect two Android devices.

### Working Flow

1. Bluetooth is enabled on both Android devices.
2. Both devices are paired from Android Bluetooth settings.
3. The app displays paired Bluetooth devices.
4. The user selects a device from the list.
5. A Bluetooth socket connection is created.
6. Text messages are sent through the output stream.
7. Incoming messages are received through the input stream.
8. Files are selected from the device storage.
9. File data is divided into byte chunks.
10. Chunks are transferred through the Bluetooth connection.
11. Transfer progress is displayed using a progress bar.

---

## Core Modules

### MainActivity.kt

`MainActivity.kt` handles the main user interface and user interactions.

It is responsible for:

- Requesting Bluetooth permissions
- Showing paired devices
- Handling button clicks
- Displaying messages
- Sending text messages
- Selecting files
- Updating connection status
- Updating file transfer progress

### BluetoothService.kt

`BluetoothService.kt` handles the Bluetooth communication logic.

It is responsible for:

- Creating Bluetooth socket connections
- Managing client/server communication
- Reading incoming data
- Writing outgoing data
- Handling text messages
- Handling file transfer
- Managing connection state

### activity_main.xml

`activity_main.xml` contains the complete user interface layout.

It includes:

- App title
- Connection status
- Device list
- Chat message area
- File progress bar
- Message input field
- Attach button
- Send button

---

## Project Structure

```text
BluetoothChatApp/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/bluetoothchatapp/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   └── BluetoothService.kt
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   └── activity_main.xml
│   │   │   │   ├── drawable/
│   │   │   │   ├── mipmap/
│   │   │   │   └── values/
│   │   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
├── screenshots/
│   ├── screenshot-1.png
│   └── screenshot-2.png
├── demo/
│   └── demo-video.mp4
├── PROTOCOL_DESIGN.md
├── README.md
├── settings.gradle.kts
└── build.gradle.kts
