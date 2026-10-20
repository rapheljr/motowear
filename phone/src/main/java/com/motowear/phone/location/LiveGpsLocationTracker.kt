package com.motowear.phone.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.motowear.phone.gpx.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.roundToInt

data class GpsCourseSnapshot(
    val position: GeoPoint,
    val speedKmh: Int,
    val courseOverGroundDegrees: Double,
    val accuracyMeters: Float,
    val timestampMs: Long
)

/**
 * Section 6.1: Hardware GPS Course-Over-Ground (`COG`) Location Tracker.
 *
 * Uses `FusedLocationProviderClient` at 10Hz (`100ms` interval, `PRIORITY_HIGH_ACCURACY`).
 * Enforces Magnetometer Rejection: Heading (`courseOverGroundDegrees`) is updated strictly from
 * `Location.getBearing()` when `speedKmh > 5` and locks to the last valid COG vector when `< 5 km/h`.
 */
class LiveGpsLocationTracker(context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    private var lastValidCogDegrees: Double = 330.0

    @SuppressLint("MissingPermission")
    fun observeGpsTelemetry(intervalMs: Long = 100L): Flow<GpsCourseSnapshot> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc: Location = result.lastLocation ?: return
                val speedKmh = if (loc.hasSpeed()) {
                    (loc.speed * 3.6f).roundToInt().coerceAtLeast(0)
                } else {
                    0
                }

                // Section 6.1: Only update Course-Over-Ground when speed exceeds 5 km/h
                if (speedKmh > 5 && loc.hasBearing()) {
                    lastValidCogDegrees = ((loc.bearing.toDouble() % 360.0) + 360.0) % 360.0
                }

                val snapshot = GpsCourseSnapshot(
                    position = GeoPoint(
                        lat = loc.latitude,
                        lon = loc.longitude,
                        elevationMeters = if (loc.hasAltitude()) loc.altitude else 0.0,
                        speedLimitKmh = 70
                    ),
                    speedKmh = speedKmh,
                    courseOverGroundDegrees = lastValidCogDegrees,
                    accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 0f,
                    timestampMs = loc.time
                )
                trySend(snapshot)
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose {
            fusedClient.removeLocationUpdates(callback)
        }
    }
}
