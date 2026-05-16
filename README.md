# Bluetooth Chat App

## Overview

Bluetooth Chat App is an Android application developed for the CNDC course Tasks. The app allows two Android devices to connect using Bluetooth, exchange real-time text messages, and transfer files such as images and PDFs.

The interface is designed to look like a modern messaging application, with message bubbles, timestamps, connection status, device list, file attachment support, and file transfer progress.

## Course Information

- Course: CNDC
- Section: BSCS-6B
- Institute: SZABIST Islamabad
- Project Type: Bluetooth Communication Application
- Platform: Android
- Language: Kotlin
- IDE: Android Studio

## Features

### Core Features

- Bluetooth device discovery
- Display paired Bluetooth devices
- Connect two real Android devices
- Real-time text messaging
- Sent messages shown on the right side
- Received messages shown on the left side
- File attachment support
- Image/PDF/file transfer between devices
- File transfer progress indicator
- Connection status display
- Scanning and disconnected state handling

### Additional Features

- Message timestamps
- Modern chat-style user interface
- Permission handling for Android Bluetooth
- Tested on two real Android phones

## Technologies Used

- Kotlin
- Android Studio
- Android SDK
- Bluetooth Classic API
- XML Layouts
- Gradle

## Project Structure

```text
BluetoothChatApp/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/bluetoothchatapp/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   └── BluetoothService.kt
│   │   │   ├── res/layout/
│   │   │   │   └── activity_main.xml
│   │   │   └── AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
└── PROTOCOL_DESIGN.md
