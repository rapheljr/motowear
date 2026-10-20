# MotoWear OS 🏍️⌚

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Wear%20OS%204.0%2B%20%7C%20Android%2011%2B-brightgreen.svg)](https://developer.android.com/wear)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![Compose Wear OS](https://img.shields.io/badge/UI-Jetpack%20Compose%20Wear%20OS-4285F4.svg)](https://developer.android.com/training/wearables/compose)

**MotoWear OS** transforms any circular Wear OS smartwatch into a pixel-for-pixel, feature-complete functional replica of the **Beeline Moto 2** motorcycle navigation computer.

---

## 📱 Screen Showcase

| Quick Start | Turn-by-Turn | Fast Rerouting | Speedometer |
| :---: | :---: | :---: | :---: |
| <img src="docs/images/blk_strart_ride_from_device.webp" width="160" alt="Quick Start"/> | <img src="docs/images/blk_nav.webp" width="160" alt="Turn by Turn"/> | <img src="docs/images/blk_rerouting_32632794-93c7-410a-972e-2440e0115b3a.webp" width="160" alt="Fast Rerouting"/> | <img src="docs/images/blk_speedo_55040e5d-f3ce-4ce7-b106-c9ef0ac57a7b.webp" width="160" alt="Speedo"/> |

| Journey Progress | Compass Tracking | Waypoint Skipping | Ride Summary |
| :---: | :---: | :---: | :---: |
| <img src="docs/images/blk_journey_eta.webp" width="160" alt="Journey Progress"/> | <img src="docs/images/blk_Journey_tracking.webp" width="160" alt="Journey Tracking"/> | <img src="docs/images/blk_skip_waypoiny_9fb04247-e640-4922-9847-69d28479134f.webp" width="160" alt="Waypoint Skipping"/> | <img src="docs/images/blk_ride_summary.webp" width="160" alt="Ride Summary"/> |

---

## 🌟 Key Features

- **8 Authentic Beeline Moto 2 Ride Modes**:
  1. `QUICK_START` - Zero-setup instant launch mode.
  2. `TURN_BY_TURN` - Primary 2D vector mini-map, turn maneuvers, distance to next turn, speed, and ETA.
  3. `FAST_REROUTING` - Automatic off-route detection with 4-dot detour breadcrumbs and rejoin glyphs.
  4. `SPEEDO` - Live speedometer, average speed, and speed limit warning ring.
  5. `JOURNEY_PROGRESS` - Remaining distance, time remaining, and completion arc.
  6. `JOURNEY_TRACKING` - 360° rotating compass ring, elapsed time, total distance, and clock.
  7. `WAYPOINT_SKIPPING` - Multi-stop waypoint management with confirmation dome.
  8. `JOURNEY_SUMMARY` - Ride statistics review.
- **Dual Routing Ingest Paths**:
  - **Path 1: Native GPX Engine** - Parse `.gpx` files directly with 2D vector projection and turn inference.
  - **Path 2: Notification Listener Bridge** - Intercept live navigation streams from Official Beeline, Google Maps, and OsmAnd.
- **Glove & Wet Weather Friendly**:
  - Capacitive Touch Lock mode to prevent false triggers in rain or road spray.
  - Full hardware button and rotary crown state machine mapping.
- **Battery & Screen Protection**:
  - Dynamic ambient mode auto-dimming during long straightaways.
  - OLED burn-in pixel shift protection.

---

## 🏗️ Architecture & Modules

```
MotoWear OS
├── :shared-proto      # Protobuf contract (telemetry.proto) & domain mapping
├── :wear              # Wear OS client app (Jetpack Compose Canvas UI, BLE GATT)
└── :phone             # Android phone companion app (GPS engine, GPX parser, BLE host)
```

- **`:shared-proto`**: Shared binary Protobuf schema (`telemetry.proto`) for efficient 10Hz low-overhead communication between phone host and watch client.
- **`:wear`**: Circular AMOLED UI rendered using Jetpack Compose Canvas, hardware rotary crown/button handler, and local 60fps mock telemetry fallback simulator.
- **`:phone`**: Dual-ingest routing engine, native GPX 1.1 parser, GPS location tracker, and TTS helmet intercom router.

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** (Ladybug / 2024.2.1+ recommended)
- **JDK 17**
- **Android SDK** API 34+
- **Wear OS Device or Emulator**: Wear OS 4.0+ (API 33+)
- **Phone Device or Emulator**: Android 11+ (API 30+)

### Building the Project

Clone the repository and run Gradle build:

```bash
# Build all APKs
./gradlew assembleDebug

# Run unit tests across modules
./gradlew test
```

### Running the Apps

1. **Companion Phone App**: Deploy `:phone` module to your Android smartphone or emulator.
2. **Wear OS App**: Deploy `:wear` module to your Wear OS smartwatch or circular emulator.

---

## 🧪 Testing

Unit tests for GPX route parsing, 2D vector projection, and notification bridge telemetry parsing are included:

```bash
./gradlew :phone:test
```

---

## 🤝 Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for details on code style, branch guidelines, and submission workflows.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
