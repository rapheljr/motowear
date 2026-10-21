# MotoWear Protobuf Wire Protocol Specification 📜⚡

MotoWear OS uses Google Protocol Buffers (Proto3) to define the binary serialization contract between the companion phone app and Wear OS smartwatch client ([`shared-proto/src/main/proto/telemetry.proto`](file:///Users/abin/workspace/projects/motonavi/shared-proto/src/main/proto/telemetry.proto)).

This specification details the schema structure, coordinate normalization math, transport paths, and streaming characteristics.

---

## 1. Schema Definition (`telemetry.proto`)

```protobuf
syntax = "proto3";
package com.motowear.telemetry;

option java_package = "com.motowear.telemetry.proto";
option java_multiple_files = true;

enum ActiveScreenMode {
  QUICK_START = 0;
  TURN_BY_TURN = 1;
  FAST_REROUTING = 2;
  SPEEDO = 3;
  JOURNEY_PROGRESS = 4;
  JOURNEY_TRACKING = 5;
  WAYPOINT_SKIPPING = 6;
  JOURNEY_SUMMARY = 7;
  DEVICE_SETTINGS = 8;
  QR_ONBOARDING = 9;
  DEBUG_TELEMETRY = 10;
}

enum ManeuverType {
  STRAIGHT = 0;
  TURN_LEFT = 1;
  TURN_RIGHT = 2;
  SLIGHT_LEFT = 3;
  SLIGHT_RIGHT = 4;
  SHARP_LEFT = 5;
  SHARP_RIGHT = 6;
  UTURN = 7;
  ROUNDABOUT_LEFT = 8;
  ROUNDABOUT_RIGHT = 9;
}

message Point2D {
  float x = 1; // Normalized [-1.0, 1.0] relative to rider chevron origin
  float y = 2; // Normalized [-1.0, 1.0] rotated to rider heading-up
}

message PolylineSegment {
  repeated Point2D points = 1;
  bool is_primary_route = 2; // true = 12dp white path, false = 2dp grey side street
}

message RideTelemetryFrame {
  ActiveScreenMode mode = 1;
  
  // Navigation & Mini-Map (Modes 1 & 2)
  repeated PolylineSegment map_vectors = 2;
  repeated Point2D reroute_breadcrumbs = 3;
  ManeuverType next_maneuver = 4;
  uint32 round_exit_number = 5;
  uint32 distance_to_next_m = 6;
  float leg_progress_ratio = 7; // [0.0 - 1.0]
  bool is_off_route = 8;

  // Speed & Limits (Modes 1 & 3)
  uint32 current_speed_kmh = 9;
  uint32 speed_limit_kmh = 10;
  uint32 avg_speed_kmh = 11;
  float speed_limit_approach_ratio = 12; // [0.0 - 1.0] white arc around speed badge

  // Journey Progress & Tracking (Modes 4, 5, 7)
  uint32 remaining_duration_min = 13;
  string elapsed_time_str = 14;   // "07:20"
  string eta_time_str = 15;       // "09:45"
  float remaining_distance = 16;  // 158.0
  bool use_imperial_units = 17;   // true = mi/mph, false = km/kmh
  float route_completion_ratio = 18; // [0.0 - 1.0]
  uint32 heading_degrees = 19;    // 0 - 359
  string cardinal_direction = 20; // "NW"
  float odometer_km = 21;         // 99.9
  string current_clock_str = 22;  // "12:55 am"

  // Waypoints (Mode 6)
  uint32 current_waypoint_idx = 23;
  uint32 total_waypoints = 24;
}
```

---

## 2. Coordinate System & 2D Vector Normalization

Instead of sending heavy WGS-84 coordinate polylines (latitudes and longitudes) that would require complex projection math on the smartwatch's battery-constrained CPU, the companion phone app pre-projects vectors into a normalized **Rider Heading-Up** coordinate system.

```
                  (-1.0, -1.0)              (1.0, -1.0)
                        ┌─────────────────────┐
                        │      Ahead (Y < 0)  │
                        │                     │
                        │          ▲          │
                        │         / \         │
      Left (X < 0) ─────┼─────── (0, 0) ──────┼───── Right (X > 0)
                        │     Rider Origin    │
                        │       (50%, 63%)    │
                        │                     │
                        │      Behind (Y > 0) │
                        └─────────────────────┘
                  (-1.0, 1.0)               (1.0, 1.0)
```

### Projection & Transformation Formula:

For any GPS point $P(\text{lat}, \text{lon})$ relative to rider GPS location $R(\text{lat}_R, \text{lon}_R)$ and heading $\theta$:

1. **Equirectangular Flat-Earth Delta**:
   $$\Delta x_{\text{meters}} = (P_{\text{lon}} - R_{\text{lon}}) \times \frac{\pi}{180} \times R_{\text{Earth}} \times \cos(R_{\text{lat}})$$
   $$\Delta y_{\text{meters}} = (P_{\text{lat}} - R_{\text{lat}}) \times \frac{\pi}{180} \times R_{\text{Earth}}$$

2. **Heading-Up 2D Rotation**:
   $$x_{\text{rot}} = \Delta x \cos(-\theta) - \Delta y \sin(-\theta)$$
   $$y_{\text{rot}} = \Delta x \sin(-\theta) + \Delta y \cos(-\theta)$$

3. **Window Normalization (Viewport Radius $D_{\text{view}} \approx 250\text{ m}$)**:
   $$X_{\text{norm}} = \frac{x_{\text{rot}}}{D_{\text{view}}} \quad \in [-1.0, 1.0]$$
   $$Y_{\text{norm}} = -\frac{y_{\text{rot}}}{D_{\text{view}}} \quad \in [-1.0, 1.0]$$

On the watch Canvas:
* Rider Chevron origin is anchored at screen center horizontally ($X = 50\%$) and slightly below center vertically ($Y = 63\%$).
* Screen pixel coordinate:
  $$\text{pixel}_X = \text{size.width} \times \left(0.5 + 0.5 \times X_{\text{norm}}\right)$$
  $$\text{pixel}_Y = \text{size.height} \times \left(0.63 + 0.5 \times Y_{\text{norm}}\right)$$

---

## 3. Data Layer Transport & Endpoints

MotoWear OS uses the Google Play Services Wearable Data Layer API:

* **Telemetry Stream Path**: `/motowear/telemetry/frame_v1`
  * Transmitted by: `TelemetryBroadcaster` (Phone Companion)
  * Received by: `WearTelemetryListenerService` (Wear OS client)
  * Frequency: $10\text{ Hz}$ during active rides
  * Transport method: `MessageClient.sendMessage()` (unbuffered UDP-like low-latency delivery)
* **Watch Command Path**: `/motowear/command/action_v1`
  * Transmitted by: `WearTelemetryRepository` (Wear OS client)
  * Received by: `RideHostEngine` (Phone Companion)
  * Payload: Action Opcode (`SKIP_WAYPOINT = 1`, `PAUSE_RIDE = 2`, `RESUME_RIDE = 3`, `END_RIDE = 4`)

---

## 4. Bandwidth & Performance Optimization

* Average payload size: $140\text{ to }280\text{ bytes}$ per frame.
* At $10\text{ Hz}$, total bandwidth consumed is under $2.8\text{ KB/s}$, ensuring minimal Bluetooth battery drain.
* Memory allocations on the Wear OS watch are near-zero:
  * Static domain objects are mutated into immutable StateFlow updates.
  * Canvas paths reuse pre-allocated `Path` instances whenever vector node counts are constant.
