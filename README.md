# ORBIT Mobile

**ORBIT Mobile** is the Android companion application for **ORBIT Windows**, enabling local Wi-Fi (LAN) pairing, live device telemetry, remote system controls, trackpad input, clipboard synchronization, file transfers, system monitoring, and AI chat integration.

---

## Technology Stack

- **Language:** Kotlin `2.2.10`
- **UI:** Jetpack Compose (BOM `2026.02.01`), Material 3, Material Icons Extended
- **Navigation & Lifecycle:** Navigation Compose `2.8.5`, Lifecycle Runtime/ViewModel Compose `2.6.1`
- **Camera & QR Scanning:** CameraX (`1.4.1`) + ZXing Core (`3.5.3`)
- **Persistence:** Room (`2.6.1`), DataStore Preferences (`1.1.1`), `SharedPreferences`
- **Async:** Kotlinx Coroutines (`1.9.0`)
- **Build System:** Gradle `9.6.0`, Android Gradle Plugin `9.5.1`

---

## Requirements

- **JDK:** Java 11 (`sourceCompatibility` / `targetCompatibility = JavaVersion.VERSION_11`)
- **Android Studio:** Version compatible with AGP `9.5.1` and Gradle `9.6.0`
- **Android SDK:**
  - `compileSdk`: `37`
  - `targetSdk`: `37`
  - `minSdk`: `26` (Android 8.0 Oreo)

---

## Project Structure

```text
app/src/main/java/com/pratyush/orbitmobile/
├── core/
│   ├── common/          # Shared Result wrapper, constants, extensions
│   ├── designsystem/    # OrbitTheme, colors, typography, reusable UI components
│   ├── di/              # AppContainer and ViewModelFactory
│   ├── navigation/      # AppRoute definitions and Compose NavHost
│   └── platform/        # KeystoreManager, NetworkMonitor, TelemetryCollector
├── data/
│   ├── local/           # Room database (AiChat) and DataStore PreferencesManager
│   ├── mapper/          # Entity-to-domain mappers
│   ├── remote/          # RemoteDataSource (HTTP/SSE client) and AndroidLanReceiver (port 8768)
│   └── repository/      # Repository implementations (Connection, Controls, Device, FileTransfer, SystemMonitor, Trackpad, AiChat, Settings)
├── domain/              # Domain models, repository contracts, and use cases
├── networking/          # Networking contracts and protocol definitions
├── presentation/        # Compose screens, ViewModels, UI states, actions, and bottom navigation
├── services/            # OrbitBackgroundService (foreground service for persistent LAN telemetry & sync)
└── util/                # Date, logging, and IPv4/port validation utilities
```

---

## Setup Instructions

1. **Clone the repository:**
   ```bash
   git clone https://github.com/pratyush-deve/Orbit-Android-mobile-client.git
   ```
2. **Open in Android Studio:**
   Open the cloned `OrbitMobile` folder in Android Studio.
3. **Configure Android SDK:**
   Ensure Android SDK Platform `37` is installed via SDK Manager (Android Studio will generate `local.properties` automatically).
4. **Sync & Build:**
   ```powershell
   .\gradlew.bat clean assembleDebug
   ```
5. **Install Debug APK:**
   ```powershell
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

---

## QR Pairing & Local Connectivity

ORBIT Mobile connects to ORBIT Windows over the local Wi-Fi (LAN) network:

1. Start **ORBIT Windows** on your PC and open the mobile pairing QR code.
2. Open **ORBIT Mobile** and tap **Scan QR Code** (`QrScannerScreen`).
3. Grant `CAMERA` permission when prompted and scan the QR code displayed by ORBIT Windows.
4. `ConnectionRepositoryImpl.pairWithQr` parses the QR JSON payload, validates expiration and Wi-Fi connectivity, and sends a pairing request to `POST /api/mobile/pair` (default port `8765`).
5. Once approved on Windows, ORBIT Mobile persists the paired device (`paired_devices.json`) and session identity (`auth_token`, `last_host`, `last_port`) so subsequent launches, Wi-Fi toggles, and restarts reconnect automatically without re-scanning.

> **Note:** Connectivity is currently LAN-only (direct Wi-Fi). Remote cloud/Firebase connectivity is not implemented in this codebase.

---

## Permissions

Declared in `app/src/main/AndroidManifest.xml`:

- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `CHANGE_NETWORK_STATE`: LAN HTTP/SSE communication, Wi-Fi state monitoring, and UDP discovery fallback.
- `CAMERA`: Live CameraX QR code scanning for initial Windows pairing.
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`: Reading Wi-Fi SSID/link telemetry on Android.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `WAKE_LOCK`, `POST_NOTIFICATIONS`: Running `OrbitBackgroundService` reliably for persistent background telemetry and sync.
- `BLUETOOTH` (maxSdk 30), `BLUETOOTH_CONNECT`: Reading Bluetooth status telemetry and satisfying `connectedDevice` foreground service requirements on Android 12+.

---

## Testing

- **Run unit tests:**
  ```powershell
  .\gradlew.bat testDebugUnitTest
  ```
- **Run instrumentation tests** (with an emulator or physical device connected):
  ```powershell
  .\gradlew.bat connectedDebugAndroidTest
  ```

---

## Troubleshooting

- **Gradle sync / SDK errors:** Verify JDK 11+ is selected in Android Studio (`Settings -> Build, Execution, Deployment -> Build Tools -> Gradle`) and Android SDK `37` is installed.
- **QR pairing fails or times out:** Ensure both your Android phone and Windows PC are on the **same local Wi-Fi network** (not cellular data or guest-isolated Wi-Fi) and that Windows Firewall allows TCP `8765` and UDP `8766`.
- **`403 INVALID_TOKEN` on reconnect after reinstalling the app:** Uninstalling the app clears local `SharedPreferences`. Forget the device on ORBIT Windows and scan the QR code once again.
