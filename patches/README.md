# Morphe Patches for Official Beeline App (`co.beeline`)

This directory contains Morphe / ReVanced style byte-code patch specifications and patching utilities for the **Official Beeline Android App** (`co.beeline` v4.2.8704).

---

## 🧩 Included Patches

### 1. `BypassBLEHardwareCheck`
- **Target:** `co.beeline.device.pairing.BeelineDevicePairing` / `co.beeline.bluetooth.device.BlePeripheral`
- **Description:** Bypasses physical hardware signature validation in `co.beeline` BLE pairing. Allows `co.beeline` to connect instantly with **MotoWear OS** (`"Beeline Moto II"`) without timing out on hardware challenge checks.

### 2. `UnlockBeelinePlusFeatures`
- **Target:** `co.beeline.ui.account.AccountComposablesKt`
- **Description:** Unlocks Beeline Plus features including round-trip route generation, live traffic overlays, and premium map layers.

---

## 🛠️ How to Apply Patches

### Method A: Using Morphe Patcher App
1. Download **Morphe Patcher** app on your phone.
2. Add custom patch bundle source: `beeline-morphe-patch.json`.
3. Select `co.beeline` APK and tap **Patch**.
4. Install the generated `co.beeline-patched.apk`.

### Method B: Using Provided Python Utility
```bash
python3 patches/patch_beeline.py co.beeline_4.2.8704.xapk patched_output
```

---

## 📱 Alternative: Built-In Notification Bridge (No Patching Required)
MotoWear OS includes **Ingest Path 2 (Notification Listener Bridge)** out of the box. You can run the standard, unpatched Beeline app (or Google Maps / OsmAnd) on your phone:
1. In the MotoWear Companion App, tap **Grant Notification Access**.
2. Start navigation in your phone app — MotoWear OS automatically captures turn guidance and streams vector maps & maneuvers to your watch screen live!
