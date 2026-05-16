# Bluetooth Chat App

Bluetooth Chat App is a Kotlin-based Android application that enables two Android devices to communicate over Bluetooth. The application supports Bluetooth device discovery, peer-to-peer connection, real-time text messaging, and file transfer between connected devices.

The project is designed with a clean messaging-style interface similar to modern chat applications. It demonstrates practical Bluetooth communication on real Android devices without requiring an internet connection.

---

## Overview

This project demonstrates how two Android devices can connect through Bluetooth and exchange data directly. The application allows users to scan available Bluetooth devices, connect to a selected device, send and receive text messages, and transfer files such as images or PDF documents.

The main purpose of this project is to understand Bluetooth-based communication in Android, including device discovery, socket connection, message exchange, file handling, and connection status management.

---
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
├── screenshot-1.png
├── demo/
│   └── demo-video.mp4
├── PROTOCOL_DESIGN.md
├── README.md
├── settings.gradle.kts
└── build.gradle.kts

## Features

### Device Discovery
- Scan nearby Bluetooth devices.
- Display available paired Bluetooth devices.
- Select a device and establish a Bluetooth connection.
- Show scanning and connection status to the user.

### Real-Time Text Messaging
- Send and receive text messages between two connected Android devices.
- Display sent messages on the right side.
- Display received messages on the left side.
- Show timestamps with messages.
- Provide a simple chat-style user experience.

### File Transfer
- Select files from the Android device.
- Send files such as images and PDF documents.
- Receive files on the connected device.
- Display transfer progress during file sending and receiving.

### Connection Status Handling
- Show connected, disconnected, scanning, and failed states.
- Provide clear feedback during Bluetooth operations.
- Support basic connection management between two devices.

---

## Technologies Used

- Kotlin
- Android Studio
- Android SDK
- XML Layout Design
- Bluetooth Classic API
- Gradle Kotlin DSL

---

## Screenshot

The screenshot below shows the working interface of the Bluetooth Chat App.

<p align="center">
  <img src="screenshot-1.png" alt="Bluetooth Chat App Screenshot" width="350"/>
</p>

---

## Demo Video

A demo video was recorded to show the complete working project on real Android devices.

The video demonstrates:

- Project files in Android Studio
- Application running on Android devices
- Bluetooth device discovery
- Connection between two real devices
- Sending and receiving text messages
- File transfer between devices

> Note: The demo video is larger than GitHub browser upload limit, so it can be submitted separately or uploaded through a compressed version / external drive link.

Expected demo video path if added later:



```text
demo/demo-video.mp4
