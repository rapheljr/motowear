# MotoWear OS Developer & Contributor Guide 🛠️💻

Welcome to the **MotoWear OS** developer guide! This document provides complete instructions for configuring your development environment, building the multi-module project, deploying to physical smartwatches or circular Wear OS emulators, and running automated tests.

---

## 1. Prerequisites & Environment Setup

### Required Tools
* **Android Studio**: Ladybug (2024.2.1+) or newer recommended.
* **Java Development Kit (JDK)**: **OpenJDK 17** (LTS).
* **Android SDK**: API 34+ (compileSdk: `34`, minSdk: `30` for phone, `33` for wear).
* **Build System**: Gradle 8.9 with Android Gradle Plugin (AGP) 8.6.0+.

### Configuring `JAVA_HOME`
MotoWear OS requires JDK 17 to execute Gradle and compile Protocol Buffers.

#### macOS (Homebrew):
```bash
brew install openjdk@17
export JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.20.1/libexec/openjdk.jdk/Contents/Home"
```

#### Linux (Debian / Ubuntu):
```bash
sudo apt update && sudo apt install -y openjdk-17-jdk
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
```

Verify your setup:
```bash
$JAVA_HOME/bin/java -version
# Expected: openjdk version "17.0.x"
```

---

## 2. Project Modules & Build Commands

The repository contains three primary Gradle modules:
* `:shared-proto`: Protobuf contract and Kotlin domain models.
* `:wear`: Wear OS client app (Jetpack Compose Canvas UI, BLE GATT peripheral).
* `:phone`: Android companion app (Material 3 Expressive UI, GPS engine, Notification bridge).

### Building APKs

```bash
# Build all debug APKs
./gradlew assembleDebug

# Build Wear OS client APK only
./gradlew :wear:assembleDebug

# Build Companion Phone APK only
./gradlew :phone:assembleDebug
```

Outputs will be generated in:
* Wear OS APK: `wear/build/outputs/apk/debug/wear-debug.apk`
* Companion APK: `phone/build/outputs/apk/debug/phone-debug.apk`

---

## 3. Running Automated Tests

MotoWear OS includes unit tests covering GPX parsing, 2D vector projections, notification scraping heuristics, Protobuf serialization, and BLE GATT decoding:

```bash
# Run all unit tests across all modules
./gradlew test

# Run Phone module tests
./gradlew :phone:test

# Run Wear module tests
./gradlew :wear:test
```

### Verified Test Suites:
* [`NavigationBridgeParserTest.kt`](file:///Users/abin/workspace/projects/motonavi/phone/src/test/java/com/motowear/phone/NavigationBridgeParserTest.kt): Verifies distance conversions, roundabout exit regex, and maneuver classification across Beeline, Google Maps, and OsmAnd strings.
* [`GpxAndProjectionTest.kt`](file:///Users/abin/workspace/projects/motonavi/phone/src/test/java/com/motowear/phone/GpxAndProjectionTest.kt): Tests GPX 1.1 XML parsing, waypoint extraction, and 2D heading-up vector projections.
* [`BleAndProtobufDecoderTest.kt`](file:///Users/abin/workspace/projects/motonavi/wear/src/test/java/com/motowear/wear/BleAndProtobufDecoderTest.kt): Tests compact 10-byte BLE frame decoding, Protobuf wire serialization, and button press notifications.

---

## 4. Emulator & Device Deployment

### Option A: Wear OS Circular Emulator
1. In Android Studio, open **Device Manager** $\to$ **Create Device** $\to$ **Wear OS**.
2. Select **Wear OS Small Round** ($384\times 384$) or **Wear OS Large Round** ($454\times 454$).
3. Select system image **Wear OS 4.0** or **Wear OS 5.0** (API 33 or API 34).
4. Launch emulator and deploy `:wear`:
   ```bash
   adb -s <emulator-id> install -r wear/build/outputs/apk/debug/wear-debug.apk
   ```

### Option B: Physical Smartwatch Deployment
Tested on:
* **Samsung Galaxy Watch 4 / 5 / 6 / 7 / Ultra** (Wear OS 4.0 / 5.0)
* **Google Pixel Watch 1 / 2 / 3** (Wear OS 4.0 / 5.0)
* **OnePlus Watch 2 / TicWatch Pro 5**

1. On the watch, enable **Developer options** $\to$ enable **ADB debugging** and **Wireless debugging**.
2. Pair and connect via ADB:
   ```bash
   adb pair <watch-ip>:<pairing-port>
   adb connect <watch-ip>:<connect-port>
   adb -s <watch-ip>:<connect-port> install -r wear/build/outputs/apk/debug/wear-debug.apk
   ```

---

## 5. Testing Modes & Built-in Simulator

### 1. Zero-Setup 60fps Mock Telemetry Generator
When `:wear` is launched independently without an active companion connection, [`RideViewModel`](file:///Users/abin/workspace/projects/motonavi/wear/src/main/java/com/motowear/wear/state/RideViewModel.kt) automatically spins up a high-fidelity 60fps mock telemetry generator:
* Simulates smooth vehicle acceleration and deceleration ($60-95\text{ km/h}$).
* Smoothly transitions distances ($500\text{ m} \to 0\text{ m}$).
* Progressively cycles maneuvers: Turn Right $\to$ Roundabout Exit 2 $\to$ Sharp Left $\to$ U-turn $\to$ Straight.
* Exercises dynamic ambient dimming and touch locking.

### 2. Live Telemetry Debug Screen (`DEBUG_TELEMETRY`)
Rotate the watch crown or swipe to screen mode `10` (`DEBUG_TELEMETRY`) to view live streaming statistics:
* Milliseconds elapsed since last received packet.
* Total packet counter.
* Active maneuver index, speed limits, and vector segment counts.

### 3. Pairing with Official Beeline App (`co.beeline`)
1. Ensure Bluetooth permissions are granted on the watch.
2. Launch MotoWear OS on the watch (it starts advertising `C5D70001` as `"Beeline Moto II"`).
3. Open the official **Beeline** app on your phone $\to$ go to **Devices** $\to$ **Pair Device** $\to$ **Beeline Moto II**.
4. The Beeline app will discover the watch, establish a GATT connection, and stream live navigation data directly!
