<div align="center">

# PENGUIN

### Privacy-First, Decentralized Group Photo Sharing for Android

[![Platform](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Java_17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org)
[![Build](https://img.shields.io/badge/Build-Gradle_8.6-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org)
[![UI](https://img.shields.io/badge/Design-Material_3_(Dark)-72CFC2?style=flat-square&logo=material-design&logoColor=black)](https://m3.material.io)
[![Networking](https://img.shields.io/badge/P2P-Google_Nearby_Connections-4285F4?style=flat-square&logo=google&logoColor=white)](https://developers.google.com/nearby/connections/overview)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=flat-square)](LICENSE)

*Effortlessly synchronize group photos in real-time over a local peer-to-peer mesh — zero cloud accounts, zero centralized servers, zero subscription fees.*

---

</div>

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [How It Works](#how-it-works)
- [System Architecture](#system-architecture)
- [Technical Stack](#technical-stack)
- [Repository Structure](#repository-structure)
- [Getting Started & Build](#getting-started--build)
  - [Prerequisites](#prerequisites)
  - [Build Commands](#build-commands)
- [Privacy & Security Model](#privacy--security-model)
- [Permissions Matrix](#permissions-matrix)
- [License](#license)

---

## Overview

**PENGUIN** is a native Android application engineered for instant, zero-friction group photo sharing during events, travel, and social gatherings.

Traditional group photo sharing suffers from fragmented messaging apps, aggressive image compression, slow cloud uploads, and privacy risks associated with centralized storage. PENGUIN solves this by turning nearby smartphones into an encrypted, high-bandwidth local mesh network using **Google Nearby Connections (`P2P_CLUSTER`)**.

When **Auto-Share** is active, photos taken on any participating device are seamlessly distributed to all connected members in real-time — directly device-to-device with uncompromising user privacy.

---

## Key Features

- ⚡ **Zero-Cloud Architecture**  
  Direct peer-to-peer transfer over high-speed Wi-Fi Direct and Bluetooth. No backend servers, databases, or cloud accounts.

- 🛡️ **5-Second Privacy Opt-Out Buffer**  
  Every newly captured photo triggers a 5-second countdown notification with an instant **"Cancel Sync"** action before transmission begins.

- 📷 **Strict Camera-Scoped Observer**  
  Monitors only newly created camera photos (`DCIM/Camera`) captured *after* a session starts. Existing galleries, screenshots, downloads, and private albums are never inspected.

- 🔄 **Self-Healing Sync & Offline Resilience**  
  Leaving Wi-Fi/Bluetooth range never revokes membership. When a disconnected member returns, peers exchange compact inventories and automatically backfill missing photos.

- 🎨 **Modern Material Design 3 Interface**  
  Immersive dark theme built with Material Design 3 tokens, fluid animations, real-time sync status badges, and 3-column photo grid.

- 🚀 **Zero-Configuration Onboarding**  
  Host a session and share a high-contrast QR code or an unambiguous 6-character room code. Peers join in seconds without account creation.

---

## How It Works

```mermaid
sequenceDiagram
    autonumber
    actor Host as Host (Alice)
    actor Peer as Peer (Bob)
    participant Obs as CameraContentObserver
    participant Opt as PhotoOptOutManager
    participant DB as Local Room DB
    participant P2P as Nearby Connections

    Host->>P2P: Create Session & Start Advertising (P2P_CLUSTER)
    Peer->>Host: Scan QR Code / Enter Room Code & Connect
    P2P-->>Host: Peer Connected & Registered

    Note over Host: Host takes a photo with Camera app
    Obs->>Opt: Detects DCIM/Camera capture (500ms debounce)
    Opt->>Host: Heads-up Notification (5-sec "Cancel Sync" window)
    
    alt User taps "Cancel Sync"
        Opt->>DB: Mark CANCELLED (stays local only)
    else 5 seconds elapse without cancellation
        Opt->>DB: Mark QUEUED & persist metadata
        DB->>P2P: Send TransferPayload (Metadata + File Stream)
        P2P->>Peer: Stream binary image bytes
        Peer->>Peer: Verify file, store locally, update Gallery
        P2P-->>Host: Delivery Receipt Confirmed (SYNCED)
    end
```

---

## System Architecture

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        LOCAL ANDROID DEVICE                            │
│                                                                        │
│  [ Camera App ] ──> MediaStore (DCIM/Camera)                           │
│                               │                                        │
│                               ▼                                        │
│                    CameraContentObserver                               │
│                               │ (500ms debounce)                       │
│                               ▼                                        │
│                     PhotoOptOutManager                                 │
│                     [5-Second Window]                                  │
│                     ┌─────────┴─────────┐                              │
│                     ▼                   ▼                              │
│               [Cancel Sync]         [Approved]                         │
│             (Stays On Device)           │                              │
│                                         ▼                              │
│                                Room Database (PhotoDao)                │
│                                         │                              │
│                                         ▼                              │
│                              TransferManager / Queue                   │
│                                         │                              │
│                                         ▼                              │
│                             NearbyConnectionsManager                   │
└─────────────────────────────────────────┬──────────────────────────────┘
                                          │ Google Nearby Connections
                                          │ (High-bandwidth P2P_CLUSTER)
                                          ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        REMOTE PEER DEVICES                             │
│                                                                        │
│  PayloadCallback ──> File Verification ──> Room DB ──> GalleryAdapter  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Technical Stack

| Category | Technology | Specification / Details |
|---|---|---|
| **Platform Target** | Android SDK 34 (Android 14) | `minSdkVersion: 26` (Android 8.0 Oreo) |
| **Language & Toolchain** | Java 17 | Gradle 8.6, Android Gradle Plugin 8.4.0 |
| **User Interface** | Material Design 3 (M3) | XML layouts, View Binding, Dynamic Dark Theme |
| **Local Persistence** | AndroidX Room 2.6.1 | SQLite abstraction, type converters, schema exports |
| **P2P Mesh Network** | Google Nearby Connections 19.1.0 | Strategy: `P2P_CLUSTER` (Bluetooth + Wi-Fi Direct) |
| **Barcode & QR** | ZXing Core 3.5.2 | High-contrast QR generation & embedded scanning |
| **Image Pipeline** | Bumptech Glide 4.16.0 | Hardware bitmap decoding, memory/disk caching |
| **Metadata Processing** | AndroidX ExifInterface 1.3.7 | Safe orientation, timestamp, and dimension parsing |
| **Background Service** | Android Foreground Service | `foregroundServiceType="dataSync"` |

---

## Repository Structure

```text
com.penguin.app
├── PenguinApplication.java           # Application entry point, notification channels & device identity
├── activity/                         # Android Activities (Material Design 3)
│   ├── MainActivity.java             # Entry portal, session dispatch, and permission resolution
│   ├── CreateSessionActivity.java    # Host session creator, QR code & room code generation
│   ├── JoinSessionActivity.java      # Participant QR scanner & manual room code entry
│   └── SessionActivity.java          # Live session dashboard, Auto-Share toggle & shared gallery
├── adapter/
│   └── GalleryAdapter.java           # 3-column RecyclerView adapter with DiffUtil & state badges
├── db/                               # AndroidX Room persistence layer
│   ├── AppDatabase.java              # Thread-safe database instance with background executors
│   ├── Converters.java               # Type converters for sync states and dates
│   ├── PhotoDao.java                 # DAO for photos and per-peer delivery receipts
│   └── SessionDao.java               # DAO for sessions and persistent member rosters
├── model/                            # Domain entities and network payloads
│   ├── InventoryPayload.java         # Compact inventory sync for peer reconciliation
│   ├── PhotoReceipt.java             # Individual peer delivery receipts
│   ├── Session.java                  # Session state entity
│   ├── SessionMember.java            # Persistent membership tracking
│   ├── SharedPhoto.java              # Photo metadata and local path tracking
│   ├── SyncStatus.java               # Lifecycle: QUEUED, SYNCING, SYNCED, FAILED, CANCELLED
│   └── TransferPayload.java          # Nearby payload serialization
├── notification/                     # Privacy & opt-out alerting
│   ├── PhotoOptOutManager.java       # 5-second countdown timer and heads-up notifications
│   └── PhotoOptOutReceiver.java      # Broadcast receiver for instant opt-out actions
├── observer/
│   └── CameraContentObserver.java    # MediaStore observer filtered specifically for DCIM/Camera
├── service/
│   └── MediaDetectionService.java    # Foreground data-sync service maintaining session lifecycle
├── transfer/                         # P2P communications layer
│   ├── NearbyConnectionsManager.java # Google Nearby Connections API wrapper & payload router
│   ├── OfflineQueueManager.java      # Room-backed retry queue (exponential backoff, max 5 retries)
│   └── TransferManager.java          # Top-level transfer orchestrator & inventory reconciler
└── util/                             # Helper utilities
    ├── FileUtils.java                # Safe app-scoped file storage & Exif inspection
    ├── PermissionHelper.java         # Dynamic runtime permission resolver (API 26–34)
    ├── QRCodeUtil.java               # High-contrast QR bitmap generator and URI parser
    └── RoomCodeGenerator.java        # Unambiguous 6-character room code generator
```

---

## Getting Started & Build

### Prerequisites

- **Android Studio**: Hedgehog (2023.1.1) or newer
- **Java Development Kit (JDK)**: JDK 17 or JDK 21
- **Android SDK**: Platform 34 (Android 14) and Build-Tools `34.0.0`
- **Physical Devices**: Minimum 2 Android devices with Bluetooth and Wi-Fi enabled (Nearby Connections requires physical hardware for radio discovery).

### Build Commands

```bash
# Clone the repository
git clone https://github.com/arcolenux/Automatic-media-sharing.git
cd Automatic-media-sharing

# Build the Debug APK
./gradlew assembleDebug

# Run Unit & Database Tests
./gradlew test

# Run Android Lint Code Analysis
./gradlew lint
```

Compiled APK will be output to:
`app/build/outputs/apk/debug/app-debug.apk`

---

## Privacy & Security Model

1. **Local-First & Ephemeral**: All sessions, photos, and metadata exist strictly on participating physical devices. No central servers or cloud relays are utilized.
2. **Deterministic Opt-Out**: Users always maintain complete control over what is shared. The 5-second countdown heads-up notification guarantees an opportunity to cancel any photo before a single byte leaves the device.
3. **Scoped Media Access**: PENGUIN monitors only the camera directory for new files created while Auto-Share is enabled. It never scans prior gallery images, personal albums, screenshots, or documents.
4. **Encrypted Radio Channels**: Nearby Connections negotiates encrypted Wi-Fi Direct and Bluetooth sockets between peers.

---

## Permissions Matrix

| Permission | API Level | Purpose |
|---|---|---|
| `CAMERA` | All | Scans QR codes when joining a session. |
| `READ_MEDIA_IMAGES` | API 33+ | Detects new camera photos in `DCIM/Camera`. |
| `READ_EXTERNAL_STORAGE` | API 26–32 | Legacy media access for Android 8.0 to 12. |
| `NEARBY_WIFI_DEVICES` | API 33+ | Discovers and connects to nearby peers over local Wi-Fi. |
| `BLUETOOTH_SCAN` / `BLUETOOTH_ADVERTISE` / `BLUETOOTH_CONNECT` | API 31+ | Discovers and negotiates P2P connections over Bluetooth. |
| `ACCESS_FINE_LOCATION` | API 26–32 | Legacy requirement for Bluetooth/Wi-Fi beacon discovery. |
| `POST_NOTIFICATIONS` | API 33+ | Displays the 5-second privacy opt-out heads-up notification. |
| `FOREGROUND_SERVICE_DATA_SYNC` | API 34+ | Maintains active session monitoring while the app is in the background. |

---

## License

```text
Copyright 2026 PENGUIN Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
