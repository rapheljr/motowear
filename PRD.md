# Product Requirements Document (PRD): MotoWear OS (Beeline Moto 2 Replication)

| Document Metadata | Details |
| :--- | :--- |
| **Product Name** | MotoWear OS (Beeline Moto 2 Pixel-Accurate Wear OS Client & Companion) |
| **Target Platform** | Wear OS 4.0 / 5.0+ (API 33+) & Android 11+ Companion Host (API 30+) |
| **UI Framework** | Jetpack Compose for Wear OS (`androidx.wear.compose`) + Custom Canvas |
| **Hardware Target** | Circular AMOLED Smartwatches (450×450 px / 480×480 px baseline, 1,000+ nits) |
| **Document Status** | Baseline Engineering Specification (v1.0) |

## 1. Executive Summary & Product Vision

MotoWear OS transforms a standard circular Wear OS smartwatch—worn on the wrist or mounted on motorcycle handlebars—into a pixel-for-pixel functional replica of the Beeline Moto 2 navigation computer.

## 2. Project Modules

- **`:shared-proto`**: Contains `telemetry.proto` (the binary Protobuf contract between phone host and watch client) and shared Kotlin domain models (`RideTelemetryState`, `ActiveMode`, `Maneuver`, `Vec2`, `RouteSegment`).
- **`:wear`**: Wear OS client featuring the 8 Beeline Moto 2 Jetpack Compose Canvas ride screens, hardware button/rotary crown state machine, capacitive touch lock, Dynamic Distance Wake (`AmbientLifecycleObserver`), and 60fps mock telemetry simulator.
- **`:phone`**: Android 11+ companion host stub with `MessageClient` 10Hz telemetry broadcaster and helmet intercom audio cue router (`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`).
