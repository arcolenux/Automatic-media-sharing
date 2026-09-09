# PENGUIN 🐧

> **Share moments, not files.**  
> Automatic, privacy-first peer-to-peer group photo sharing for Android.

---

## Overview

**PENGUIN** is a native Android application designed for effortless, real-time group photo sharing during trips, events, and hangouts. 

Instead of manually selecting, uploading, and messaging photos at the end of the day, PENGUIN allows participants in the same physical space to automatically share new camera photos with the group as they happen—directly device-to-device, with zero cloud dependency and strict privacy controls.

---

## Key Features

- 🔒 **Privacy-First & Camera-Scoped**: Only detects photos taken by your camera (`DCIM/Camera`) while Auto-Share is actively turned on. Never scans your gallery or shares screenshots, downloads, or messaging media.
- ⏱️ **5-Second Opt-Out Window**: Every newly detected photo triggers a heads-up notification with a **"Cancel Sync"** action. You have 5 seconds to opt out before the photo is shared.
- 📡 **Direct Peer-to-Peer**: Powered by **Google Nearby Connections** (`P2P_CLUSTER`). Photos transfer directly between nearby devices over high-speed local Wi-Fi/Bluetooth without uploading to any remote server.
- ⚡ **Offline Resilience & Auto-Sync**: Participants stay part of the session even when temporarily moving out of range. When you reconnect, missing photos automatically synchronize.
- 📲 **Instant QR & Room Code Pairing**: Start a session in seconds. Friends can join by scanning the host's QR code or entering an unambiguous 6-character room code.
- 🖼️ **Shared Gallery**: Modern 3-column photo grid with real-time transfer status badges (`Syncing`, `Shared`, `Failed`, `Queued`).

---

## How It Works

```text
Host creates session (QR / Room Code)
                 │
      Friends join nearby (P2P)
                 │
       Turn Auto-Share ON
                 │
      Snap photo with Camera
                 │
   5-second notification countdown
    ┌────────────┴────────────┐
    ▼                         ▼
[Cancel Sync]             [Approved]
(Never shared)                │
                    Direct P2P Transfer
                              │
                    Shared Gallery Updated
```

---

## Tech Stack

| Component | Technology |
|---|---|
| **Platform** | Android (API 26+ / Android 8.0 to Android 14) |
| **Language** | Java 17 |
| **UI Design** | XML Layouts + Material Design 3 + View Binding |
| **Networking** | Google Nearby Connections API |
| **Database** | AndroidX Room Database |
| **Image Loading** | Glide |
| **QR Code** | ZXing Core & Embedded Scanner |
| **Background Service** | Android Foreground Service (`dataSync`) |

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17+ (or JDK 21)
- Android SDK 34 (Android 14)

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/penguin.git
   cd penguin
   ```

2. Build debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

3. Run unit tests:
   ```bash
   ./gradlew test
   ```

The compiled APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Permissions & Privacy

PENGUIN requests only permissions necessary for local operation:
- **Camera**: For scanning host QR codes.
- **Photos / Media**: To detect new camera captures while Auto-Share is enabled.
- **Nearby Devices & Location**: Required by Android for local Bluetooth & Wi-Fi device discovery and direct data transfer.
- **Notifications**: To present the 5-second opt-out notification and foreground service status.

PENGUIN contains **zero third-party trackers, analytics backends, or cloud storage sync**. Your photos remain entirely on participating devices.

---

## License

This project is licensed under the Apache License 2.0.
