# Product Requirements Document (PRD)
## MotoWear OS: Beeline Moto 2 Replication & Companion System

| Document Metadata | Details |
| :--- | :--- |
| **Product Name** | MotoWear OS (Beeline Moto 2 Pixel-Accurate Wear OS Client & Companion) |
| **Target Platform** | Wear OS 4.0 / 5.0+ (API 33+) & Android 11+ Companion Host (API 30+) |
| **UI Framework** | Jetpack Compose for Wear OS (`androidx.wear.compose`) + Custom Canvas UI |
| **Design System** | Authentic Beeline Vector Design System & Material 3 Expressive (Phone) |
| **Hardware Targets** | Circular AMOLED Smartwatches (450×450 px / 480×480 px baseline, 1,000+ nits) |
| **Document Status** | Approved Engineering Specification (v2.0) |

---

## 1. Executive Summary & Product Vision

**MotoWear OS** transforms any standard circular Wear OS smartwatch—worn on the rider's wrist or securely mounted directly to motorcycle handlebars—into a pixel-for-pixel, feature-complete functional replica of the **Beeline Moto 2** motorcycle navigation computer.

The product solves three major challenges for motorcycle riders:
1. **Cost & Dedicated Device Burden**: High-end motorcycle navigation units often cost $200–$400. MotoWear OS repurposes ubiquitous circular Wear OS hardware (Samsung Galaxy Watch, Google Pixel Watch, OnePlus Watch) into a rugged, high-clarity navigation computer.
2. **Readability & Glanceability at Speed**: Standard smartwatch maps are cluttered and impossible to read safely at 100 km/h. MotoWear OS implements Beeline's high-contrast vector-line mini-map, large maneuver indicators, and prominent speed badges.
3. **Ecosystem Interoperability**: MotoWear OS uniquely supports **dual connectivity**:
   * It can advertise as a genuine Beeline Moto II over Bluetooth Low Energy, allowing the official Beeline mobile app to pair directly with the watch.
   * It pairs with our Android Companion App to ingest `.gpx` routes directly or bridge notifications from Google Maps and OsmAnd.

---

## 2. Target Personas & Use Cases

* **The Urban Commuter**: Requires quick glanceable turn indicators, real-time speed limit alerts, and instant rerouting through complex city street grids. Uses either wrist mounting or handlebar cradle.
* **The Weekend Tourer**: Follows curated GPX routes across country roads. Relies on waypoint skipping, total journey progress arcs, remaining distance/time, and battery-preserving Dynamic Distance Wake.
* **The All-Weather Adventurer**: Rides in heavy rain and adverse weather conditions. Relies on the hardware button state machine and the Capacitive Touch Lock to prevent road spray or wet gloves from registering false touch inputs.

---

## 3. Hardware & Platform Specifications

| Component | Minimum Specification | Recommended Specification |
| :--- | :--- | :--- |
| **Display Form Factor** | Circular AMOLED, $\ge 1.2\text{ inch}$ | Circular AMOLED, $1.4\text{ inch}$ ($450\times 450$ or $480\times 480\text{ px}$) |
| **Peak Brightness** | $\ge 600\text{ nits}$ | $\ge 1,000\text{ to }2,000\text{ nits}$ (Sunlight readable) |
| **Operating System** | Wear OS 4.0 (API 33) | Wear OS 4.0 / 5.0+ (API 33 / 34+) |
| **Physical Controls** | At least 1 physical hardware stem button | 2 physical stem buttons (`STEM_1`, `STEM_2`) + Rotary Crown |
| **Wireless Radio** | Bluetooth Low Energy 5.0+ | BLE 5.2+ with low-latency peripheral advertising support |
| **Phone Companion** | Android 11+ (API 30+) | Android 14+ (API 34+) with GNSS Fused Location |

---

## 4. System Architecture & Topology

MotoWear OS is organized into three modular subprojects:

```
MotoWear OS
├── :shared-proto      # Binary Protobuf Contract (telemetry.proto) & Domain Models
├── :wear              # Wear OS Client App (Jetpack Compose Canvas, BLE GATT Peripheral)
└── :phone             # Companion Host App (GPX Engine, Notification Bridge, Broadcaster)
```

### Ingestion Topologies:
1. **Topology A: Direct Beeline App BLE GATT Connection**
   The watch runs `BeelineBleGattPeripheralServer`, advertising service `C5D70001-C45D-4F12-8693-7EF838E96446`. The official Beeline mobile app connects directly to the watch as a standard Beeline Moto II peripheral.
2. **Topology B: MotoWear Companion Data Layer**
   The companion app parses GPX routes or intercepts Google Maps / OsmAnd notifications, project coordinates into 2D heading-up vectors, and broadcasts binary Protobuf frames over Google Play Services `MessageClient` at 10Hz.
3. **Topology C: Autonomous Mock Telemetry Fallback**
   If no phone is connected, `RideViewModel` runs an internal 60fps telemetry simulation to allow full offline demonstration and testing.

---

## 5. Functional Requirements: The 11 Screen Modes

Every screen mode is implemented as a dedicated Jetpack Compose Canvas renderer:

### Mode 0: `QUICK_START`
* **Entry Condition**: Cold boot default state before route is active.
* **UI Elements**: Centered Beeline brand roundel, `"START RIDE FROM DEVICE"` prompt, bottom `"PRESS TO START"` pill badge.
* **Interaction**: Pressing `STEM_2` initiates ride and switches to `TURN_BY_TURN`.

### Mode 1: `TURN_BY_TURN`
* **UI Elements**:
  * Dynamic heading-up 2D vector mini-map: 12dp primary white route, 2dp grey side roads.
  * Amber rider chevron fixed at $(X = 50\%, Y = 63\%)$.
  * Maneuver glyph (Straight, Turn Left/Right, Slight Left/Right, Sharp Left/Right, U-turn, Roundabouts).
  * Roundabout exit badge (e.g. exit 1, 2, 3).
  * Distance to next turn counter (meters or miles/feet).
  * Bottom-left speed badge with speed limit approach indicator arc.
  * Bottom-right ETA badge.

### Mode 2: `FAST_REROUTING`
* **Trigger**: Automatic when rider strays $> 40\text{ meters}$ off planned route.
* **UI Elements**: 4-dot amber detour breadcrumb trail extending from chevron, pulsing reroute banner.

### Mode 3: `SPEEDO`
* **UI Elements**: Centered massive digital speed readout, circular outer tachometer ring, top regulatory speed limit badge (`[ 70 ]`), trip average and top speed readouts.

### Mode 4: `JOURNEY_PROGRESS`
* **UI Elements**: Circular 360° route completion ring with leading indicator dot, remaining distance counter, remaining travel time, and ETA.

### Mode 5: `JOURNEY_TRACKING`
* **UI Elements**: Rotating 360° compass rose ring with cardinal points, heading in degrees (e.g. `330° NW`), trip odometer, ride duration stopwatch, and current clock. Secondary stats accessible via sub-page dots (`○ ●`).

### Mode 6: `WAYPOINT_SKIPPING`
* **Trigger**: Multi-stop ride navigation.
* **UI Elements**: Waypoint index badge (`Waypoint X of Y`), skip action confirmation dome (`>>|`), `"PRESS TO SKIP"` prompt.

### Mode 7: `JOURNEY_SUMMARY`
* **Trigger**: Route finish or manual end-ride action.
* **UI Elements**: Post-ride summary statistics (Total Distance, Total Time, Average Speed, Top Speed) with Beeline brand roundel.

### Mode 8: `DEVICE_SETTINGS`
* **UI Elements**: Rotary wheel menu list (`About`, `Unpair`, `Reset`, `Legal`) with Beeline edge D-buttons (back arrow at 9 o'clock, chevron at 3 o'clock).

### Mode 9: `QR_ONBOARDING`
* **UI Elements**: 25x25 ISO/IEC 18004 Version 2-L QR code encoding `https://beeline.co/app`, `"SCAN TO GET APP"` header, and 4-character pairing code capsule (`8046`).

### Mode 10: `DEBUG_TELEMETRY`
* **UI Elements**: Diagnostic telemetry inspector displaying live packet latency (ms), packet count, GPS heading, speed limits, vector segment counts, and connection state.

---

## 6. Hardware & Physical Controls

* **Stem Button 1 (Top Button)**:
  * Short press: Navigate to next screen mode in active carousel.
  * Long press ($\ge 1.5\text{ seconds}$): Toggle Capacitive Touch Lock (prevents false touch input during rain or glove use).
* **Stem Button 2 (Bottom Action Button)**:
  * Triggers mode-specific action (e.g. Confirm Start, Skip Waypoint, Dismiss).
* **Rotary Crown**:
  * Clockwise: Advance carousel / scroll downward.
  * Counter-Clockwise: Previous screen / scroll upward.

---

## 7. Power, Thermal & Battery Management

* **Dynamic Distance Wake**:
  * When approaching a maneuver ($< 400\text{ meters}$), display operates at full brightness.
  * On long highway straightaways ($> 400\text{ meters}$ to next turn), display automatically switches to low-power hollow ambient mode.
  * Full brightness is restored $5\text{ seconds}$ prior to the next turn maneuver.
* **OLED Burn-in Pixel Shifting**:
  * The entire canvas shifts periodically by $\pm 4\text{ pixels}$ ($X$ and $Y$) to ensure static elements do not cause AMOLED subpixel burn-in.

---

## 8. Non-Functional Requirements & Performance SLAs

* **Cold Boot Time**: $< 1.2\text{ seconds}$ from application launch to interactive `QUICK_START` screen (validated via `reportFullyDrawn()`).
* **Frame Rate**: Consistent $60\text{ fps}$ Jetpack Compose Canvas rendering with zero GC churn on UI thread.
* **Latency**: $< 100\text{ ms}$ packet-to-glass latency between phone GPS measurement and watch display update.
* **Memory Footprint**: Heap usage $< 35\text{ MB}$ on Wear OS client.
* **Test Coverage**: Automated unit test suites covering GPX parsing, 2D vector projection, notification regex parsing, Protobuf serialization, and BLE GATT decoding.
