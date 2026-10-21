# MotoWear OS Screen Modes Catalog 🖥️🏍️

MotoWear OS implements 11 authentic screen modes replicating the **Beeline Moto 2** motorcycle navigation computer down to the individual pixel.

Every screen is rendered on Jetpack Compose Canvas using high-contrast vector drawing primitives designed for bright sunlight visibility, high-speed glanceability, and OLED power efficiency.

---

## 1. Screen Mode Directory

| Mode Enum | Mode ID | Title | Purpose |
| :--- | :---: | :--- | :--- |
| `QUICK_START` | `0` | Quick Start | Zero-setup instant ride launch |
| `TURN_BY_TURN` | `1` | Turn-by-Turn Navigation | Primary 2D vector mini-map, maneuvers, turn distance, ETA |
| `FAST_REROUTING` | `2` | Fast Rerouting | Off-route detour breadcrumbs and route rejoin glyphs |
| `SPEEDO` | `3` | Speedometer & Speed Limits | High-visibility speed readout with speed limit ring |
| `JOURNEY_PROGRESS` | `4` | Journey Progress | Remaining distance, remaining time, ETA, completion arc |
| `JOURNEY_TRACKING` | `5` | Compass & Tracking | Rotating 360° compass rose, elapsed time, odometer, clock |
| `WAYPOINT_SKIPPING` | `6` | Waypoint Skipping | Multi-stop waypoint management with confirmation dome |
| `JOURNEY_SUMMARY` | `7` | Journey Summary | Post-ride review of distance, ride time, and average speed |
| `DEVICE_SETTINGS` | `8` | Device Options & Settings | Rotary wheel settings list with Beeline edge D-buttons |
| `QR_ONBOARDING` | `9` | QR Code Onboarding | 25x25 QR pairing matrix with device ID badge (`8046`) |
| `DEBUG_TELEMETRY` | `10` | Debug Telemetry Inspector | Real-time diagnostic monitor for packets, BLE, and GPS |

---

## 2. Visual Screen Showcase & Layouts

### Screen 1: `QUICK_START` (Mode 0)
* **File**: [`QuickStartScreen.kt`](file:///Users/abin/workspace/projects/motonavi/wear/src/main/java/com/motowear/wear/ui/screens/QuickStartScreen.kt)
* **Visual Reference**: `docs/images/blk_strart_ride_from_device.webp`
* **Layout**:
  * Centered Beeline yellow brand roundel and start arrow icon.
  * Uppercase headline: `"START RIDE FROM DEVICE"`.
  * Bottom pill badge: `"PRESS TO START"`.
* **Hardware Controls**:
  * Bottom Action Button (`STEM_2`): Starts active ride and transitions into `TURN_BY_TURN`.
  * Rotary Crown: Cycles between onboarding, settings, and start screen.

### Screen 2: `TURN_BY_TURN` (Mode 1)
* **File**: [`TurnByTurnAndRerouteScreen.kt`](file:///Users/abin/workspace/projects/motonavi/wear/src/main/java/com/motowear/wear/ui/screens/TurnByTurnAndRerouteScreen.kt)
* **Visual Reference**: `docs/images/blk_nav.webp`
* **Layout**:
  * **2D Vector Mini-Map**: Dynamic heading-up vector map. Active route is rendered as a 12dp solid white path with rounded caps; intersecting side streets render as 2dp subtle dark grey lines.
  * **Rider Chevron**: Solid Beeline Amber arrow pinned at $(X = 50\%, Y = 63\%)$ pointing straight up.
  * **Turn Header**: Bold maneuver vector glyph (e.g. sharp right, roundabout exit #2) paired with distance to turn (e.g. `300 m` or `0.2 mi`).
  * **Speed Badge**: Bottom-left capsule with live speed (`80`) and outer white arc representing approach to current speed limit.
  * **ETA Badge**: Bottom-right capsule showing estimated arrival time (`09:45`).
* **Ambient Behavior**: Automatically dims and draws hollow maneuver vectors during straight road sections over $400\text{ m}$.

### Screen 3: `FAST_REROUTING` (Mode 2)
* **File**: [`TurnByTurnAndRerouteScreen.kt`](file:///Users/abin/workspace/projects/motonavi/wear/src/main/java/com/motowear/wear/ui/screens/TurnByTurnAndRerouteScreen.kt)
* **Visual Reference**: `docs/images/blk_rerouting_32632794-93c7-410a-972e-2440e0115b3a.webp`
* **Layout**:
  * Activates automatically when GPS deviation from route exceeds $40\text{ meters}$.
  * 4-dot dashed amber detour breadcrumb trail extending from rider chevron.
  * Animated pulsing reroute indicator banner at top.

### Screen 4: `SPEEDO` (Mode 3)
* **File**: [`SpeedoScreen.kt`](file:///Users/abin/workspace/projects/motonavi/wear/src/main/java/com/motowear/wear/ui/screens/SpeedoScreen.kt)
* **Visual Reference**: `docs/images/blk_speedo_55040e5d-f3ce-4ce7-b106-c9ef0ac57a7b.webp`
* **Layout**:
  * Huge centered speed readout in custom heavy typography (e.g. `80`).
  * Unit sub-label (`km/h` or `mph`).
  * Circular outer speedometer gauge arc showing acceleration and speed limit threshold.
  * Regulatory speed limit sign badge (e.g. `[ 100 ]`) in top quadrant.
  * Bottom trip metrics: Average Speed and Trip Top Speed.

### Screen 5: `JOURNEY_PROGRESS` (Mode 4)
* **File**: [`JourneyProgressScreen.kt`](file:///Users/abin/workspace/projects/motonavi/wear/src/main/java/com/motowear/wear/ui/screens/JourneyProgressScreen.kt)
* **Visual Reference**: `docs/images/blk_journey_eta.webp`
* **Layout**:
  * Outer $360^\circ$ circular progress ring with amber leading dot indicating overall route completion percentage ($0-100\%$).
  * Prominent remaining distance counter (`158.0 km`).
  * Remaining duration countdown (`2h 25m`) and estimated arrival time (`ETA 09:45`).

### Screen 6: `JOURNEY_TRACKING` (Mode 5)
* **File**: [`JourneyTrackingScreen.kt`](file:///Users/abin/workspace/projects/motonavi/wear/src/main/java/com/motowear/wear/ui/screens/JourneyTrackingScreen.kt)
* **Visual Reference**: `docs/images/blk_Journey_tracking.webp`
* **Layout**:
  * Outer rotating $360^\circ$ compass rose ring with cardinal tick marks (`N`, `E`, `S`, `W`).
  * Live digital heading readout (`330° NW`).
  * Live ride duration timer (`07:20`) and total odometer (`99.9 km`).
  * Current 12h/24h digital clock (`12:55 am`).
  * Sub-page indicator dots (`○ ●`) toggled via bottom button or crown.

### Screen 7: `WAYPOINT_SKIPPING` (Mode 6)
* **File**: [`WaypointSkippingScreen.kt`](file:///Users/abin/workspace/projects/motonavi/wear/src/main/java/com/motowear/wear/ui/screens/WaypointSkippingScreen.kt)
* **Visual Reference**: `docs/images/blk_skip_waypoiny_9fb04247-e640-4922-9847-69d28479134f.webp`
* **Layout**:
  * Waypoint index badge (`Waypoint 1 of 3`).
  * Semi-circular action dome at bottom with skip glyph (`>>|`).
  * `"PRESS TO SKIP"` action confirmation prompt.

### Screen 8: `JOURNEY_SUMMARY` (Mode 7)
* **File**: [`JourneySummaryScreen.kt`](file:///Users/abin/workspace/projects/motonavi/wear/src/main/java/com/motowear/wear/ui/screens/JourneySummaryScreen.kt)
* **Visual Reference**: `docs/images/blk_ride_summary.webp`
* **Layout**:
  * Displayed upon ride finish or manual end ride action.
  * Comprehensive statistics grid: Total Distance (`98.2 km`), Total Duration (`2h 32m`), Average Speed (`38.8 km/h`).
  * Official Beeline brand roundel footer.

### Screen 9: `DEVICE_SETTINGS` (Mode 8)
* **File**: [`DeviceSettingsScreen.kt`](file:///Users/abin/workspace/projects/motonavi/wear/src/main/java/com/motowear/wear/ui/screens/DeviceSettingsScreen.kt)
* **Visual Reference**: `docs/images/device_settings_template.png`
* **Layout**:
  * Left edge 9 o'clock D-button with return arrow glyph (`EdgeGlyphType.RETURN_BACK`).
  * Right edge 3 o'clock D-button with chevron right glyph (`EdgeGlyphType.CHEVRON_RIGHT`).
  * Centered vertical carousel rotary wheel:
    * `About`
    * `Unpair`
    * `Reset`
    * `Legal`
  * Active item rendered in 30sp bold pure white; adjacent items fade to 70%, 45%, 25% opacity.

### Screen 10: `QR_ONBOARDING` (Mode 9)
* **File**: [`QrOnboardingScreen.kt`](file:///Users/abin/workspace/projects/motonavi/wear/src/main/java/com/motowear/wear/ui/screens/QrOnboardingScreen.kt)
* **Visual Reference**: `docs/images/qr_onboarding_connect.png`
* **Layout**:
  * Upper circular area contains a scannable $25\times 25$ ISO/IEC 18004 Version 2-L QR code encoding `https://beeline.co/app`.
  * Centered bold white uppercase label: `"SCAN TO GET APP"`.
  * Lower capsule pill badge displaying 4-character pairing code: `8046`.

### Screen 11: `DEBUG_TELEMETRY` (Mode 10)
* **File**: [`DebugTelemetryScreen.kt`](file:///Users/abin/workspace/projects/motonavi/wear/src/main/java/com/motowear/wear/ui/screens/DebugTelemetryScreen.kt)
* **Layout**:
  * Live diagnostic stream monitor for engineering and route debugging.
  * Packet latency monitor (`Last packet: 14ms ago`), packet counters, connection status badge.
  * Real-time metrics breakdown: Speed, Speed Limit, Heading, Maneuver index, Roundabout Exit, Odometer, Map Vector segment count, Breadcrumb count.
  * Scrollable list view optimized for Wear OS rotary crown scrolling.

---

## 3. Screen Mode Navigation Carousel

The smartwatch physical rotary crown and top stem button navigate between screens according to the following cycle:

```
[QR_ONBOARDING] ⇄ [QUICK_START] ⇄ [TURN_BY_TURN] ⇄ [SPEEDO] ⇄ [JOURNEY_PROGRESS] ⇄ [JOURNEY_TRACKING] ⇄ [DEVICE_SETTINGS] ⇄ [DEBUG_TELEMETRY]
                                         ⇅
                                  [FAST_REROUTING]
                                         ⇅
                                 [WAYPOINT_SKIPPING]
```

* Long-pressing `STEM_1` for $\ge 1.5\text{ seconds}$ locks touch input on any screen, preventing accidental interactions from gloves or rain.
