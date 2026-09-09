# PENGUIN — Version 1 (V1)

**PENGUIN** is a native Android phone application for temporary, privacy-first automatic group photo sharing.

---

## 1. Product Overview

People in the same physical event or hangout can automatically share newly captured camera photos with the group in real-time, without manually picking, emailing, or uploading files.

- **100% Local & P2P-First**: Direct device-to-device transfers using **Google Nearby Connections** (`P2P_CLUSTER`).
- **Zero Cloud Costs**: Absolutely no Firebase, AWS, VPS, or developer-owned backend storage required in V1.
- **Privacy-First & Camera-Scoped**: Only detects photos taken by the phone camera (`DCIM/Camera`) after the Auto-Share toggle is activated.
- **5-Second Opt-Out Window**: Every detected photo presents a high-priority notification with **"Cancel Sync"** before sharing.
- **Resilient Offline Queue & Reconciliation**: Session members remain active across temporary disconnections; missed photos are automatically synchronized upon reconnecting.

---

## 2. Technical Stack

| Layer | Technology |
|---|---|
| **Language** | Java 17 |
| **Target SDK / Min SDK** | Android SDK 34 / minSdk 26 (Android 8.0+) |
| **UI Framework** | XML Layouts + Material Design 3 + View Binding |
| **Local Persistence** | AndroidX Room 2.6.1 |
| **P2P Networking** | Google Nearby Connections 19.1.0 (`P2P_CLUSTER`) |
| **QR Code Engine** | ZXing Core 3.5.2 + `zxing-android-embedded` 4.3.0 |
| **Image Loading & Cache** | Glide 4.16.0 |
| **Photo Detection** | `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` ContentObserver |
| **Background Monitoring** | Android Foreground Service (`dataSync`) |
| **Photo Metadata** | AndroidX ExifInterface 1.3.7 |

---

## 3. Project Architecture

```text
com.penguin.app
├── PenguinApplication.java           # App initialization, notification channels, device identity
├── activity/
│   ├── MainActivity.java             # Splash/Home screen, session routing & permissions
│   ├── CreateSessionActivity.java    # Host session generator, QR code & 6-character room code
│   ├── JoinSessionActivity.java      # Participant QR scanner & manual room code entry
│   └── SessionActivity.java          # Primary screen: Auto-Share toggle, member status, gallery
├── adapter/
│   └── GalleryAdapter.java           # 3-column RecyclerView adapter with DiffUtil & Glide
├── db/
│   ├── AppDatabase.java              # Room database (Sessions, Members, Photos, Receipts)
│   ├── Converters.java               # Room type converters (SyncStatus)
│   ├── PhotoDao.java                 # DAO for SharedPhoto and per-peer PhotoReceipt
│   └── SessionDao.java               # DAO for Session and SessionMember
├── model/
│   ├── InventoryPayload.java         # Photo inventory payload for peer reconciliation
│   ├── PhotoReceipt.java             # Per-peer delivery receipt tracking
│   ├── Session.java                  # Group session entity
│   ├── SessionMember.java            # Persistent session membership entity
│   ├── SharedPhoto.java              # Shared photo record
│   ├── SyncStatus.java               # Sync states: QUEUED, SYNCING, SYNCED, FAILED, CANCELLED
│   └── TransferPayload.java          # Nearby payload metadata (file & control messages)
├── notification/
│   ├── PhotoOptOutManager.java       # 5-second countdown timer & heads-up notification manager
│   └── PhotoOptOutReceiver.java      # Broadcast receiver for "Cancel Sync" action
├── observer/
│   └── CameraContentObserver.java    # MediaStore observer filtering for DCIM/Camera photos
├── service/
│   └── MediaDetectionService.java    # Foreground service active during Auto-Share
├── transfer/
│   ├── NearbyConnectionsManager.java # Google Nearby Connections lifecycle, advertising & discovery
│   ├── OfflineQueueManager.java      # Room-backed retry queue (max 5 retries, no photo loss)
│   └── TransferManager.java          # Top-level transfer coordinator
└── util/
    ├── FileUtils.java                # Safe file copy, dimensions, and image validation
    ├── PermissionHelper.java         # Runtime permission helper across Android 8 to 14
    ├── QRCodeUtil.java               # QR Code generator and payload parser
    └── RoomCodeGenerator.java        # Unambiguous 6-character code generator (no 0, 1, I, L, O)
```

---

## 4. Core Workflows

### 4.1 Session Creation & Joining
1. **Host**: Enters session name &rarr; Generates unambiguous 6-character room code and QR Code &rarr; Starts Nearby Advertising.
2. **Participant**: Scans QR Code or enters room code &rarr; Starts Nearby Discovery &rarr; Discovers Host &rarr; Automatically connects and exchanges member identity.

### 4.2 Camera Detection & 5-Second Cancellation
1. When **Auto-Share** is turned ON, `MediaDetectionService` starts and establishes a timestamp baseline.
2. When the user snaps a photo, `CameraContentObserver` verifies the photo was saved in `DCIM/Camera` after the baseline.
3. A heads-up notification **"Photo ready to share"** appears with a **"Cancel Sync"** action for 5 seconds.
4. If cancelled: photo is marked `CANCELLED` and is never transferred.
5. If 5 seconds elapse: photo is approved and transferred to connected peers.

### 4.3 Out-of-Range Member Catch-Up & Reconciliation
1. **Persistent Membership**: A participant who walks out of range remains an active session member.
2. Photos taken while a member is away are transferred to reachable peers and kept in durable local storage.
3. When the away member returns and reconnects, peers automatically exchange an `InventoryPayload` containing known photo IDs.
4. Missing photos are immediately transferred, bringing the returned member fully up-to-date.

---

## 5. Building & Running Tests

### 5.1 Build Debug APK
```bash
./gradlew assembleDebug
```

### 5.2 Run Unit Tests
```bash
./gradlew test
```

### 5.3 Run Lint
```bash
./gradlew lint
```
