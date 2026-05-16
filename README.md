# Bluetooth Chat App

Bluetooth Chat App is a Kotlin-based Android application that enables two Android devices to communicate over Bluetooth. The application supports Bluetooth device discovery, peer-to-peer connection, real-time text messaging, and file transfer between connected devices.

The project is designed with a clean messaging-style interface similar to modern chat applications. It demonstrates practical Bluetooth communication on real Android devices without requiring an internet connection.

---

## Overview

This project demonstrates how two Android devices can connect through Bluetooth and exchange data directly. The application allows users to scan available Bluetooth devices, connect to a selected device, send and receive text messages, and transfer files such as images or PDF documents.

The main purpose of this project is to understand Bluetooth-based communication in Android, including device discovery, socket connection, message exchange, file handling, and connection status management.

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
