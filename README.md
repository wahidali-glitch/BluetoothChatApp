# Bluetooth Chat App

Bluetooth Chat App is an Android-based Bluetooth communication project developed in Kotlin. The application allows two Android devices to discover nearby Bluetooth devices, connect with each other, exchange real-time text messages, and transfer files such as images or PDF documents.

The project is designed with a simple messaging-style interface similar to modern chat applications. It includes device scanning, connection status updates, message bubbles, timestamps, file attachment support, and file transfer progress handling.

---

## Project Overview

This project demonstrates peer-to-peer communication between two Android devices using Bluetooth. The application focuses on building a practical communication system where one device can connect to another device and exchange both text and file data without using the internet.

The main goal of this project is to understand how Bluetooth communication works in Android applications, including device discovery, pairing, socket-based communication, message handling, and file transfer.

---

## Key Features

### Device Discovery
- Scans nearby Bluetooth devices.
- Displays available paired Bluetooth devices.
- Allows the user to select and connect to a device.
- Shows scanning and connection status clearly.

### Text Messaging
- Sends and receives text messages in real time.
- Displays sent messages on the right side.
- Displays received messages on the left side.
- Shows message timestamps.
- Provides a clean chat-style user interface.

### File Transfer
- Allows the user to select a file from the device.
- Supports file sharing such as images and PDF files.
- Transfers files between connected Bluetooth devices.
- Shows file transfer progress during sending and receiving.

### Connection Status
- Displays connection states such as scanning, connected, disconnected, and failed.
- Handles Bluetooth connection updates.
- Provides feedback to the user during communication.

---

## Technologies Used

- Kotlin
- Android Studio
- Android SDK
- XML Layout Design
- Bluetooth Classic API
- Gradle Kotlin DSL

---

## Application Screenshots

The screenshot below shows the working interface of the Bluetooth Chat App.

| Bluetooth Chat App Interface |
|---|
| ![Bluetooth Chat App Screenshot](screenshot-1.png) |

---

## Demo Video

A short demo video is included to show the working project on real Android devices.

The video demonstrates:

- Opening the project in Android Studio
- Running the application on Android devices
- Bluetooth device discovery
- Connecting two real devices
- Sending and receiving text messages
- Sending and receiving a file

> Demo video file path:

```text
demo/demo-video.mp4
