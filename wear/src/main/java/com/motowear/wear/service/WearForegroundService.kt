package com.motowear.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.motowear.wear.R
import com.motowear.wear.ble.BeelineBleGattPeripheralServer
import com.motowear.wear.data.WearTelemetryRepository

/**
 * Section 5: Wear OS Foreground Service owning the BLE GATT Peripheral server and
 * telemetry session lifecycle, preserving Bluetooth advertising and telemetry forwarding
 * across Activity recreations.
 */
class WearForegroundService : Service() {

    private var blePeripheralServer: BeelineBleGattPeripheralServer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startBlePeripheral()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopBlePeripheral()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Wear foreground service", e)
        }

        return START_STICKY
    }

    private fun startBlePeripheral() {
        if (blePeripheralServer == null) {
            val server = BeelineBleGattPeripheralServer(this)
            server.startPeripheralServer()
            WearTelemetryRepository.registerBlePeripheralServer(server)
            blePeripheralServer = server
        }
    }

    private fun stopBlePeripheral() {
        blePeripheralServer?.let {
            WearTelemetryRepository.unregisterBlePeripheralServer(it)
            it.stopPeripheralServer()
        }
        blePeripheralServer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBlePeripheral()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MotoWear Navigation Link",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains Wear OS telemetry and BLE connectivity"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MotoWear OS Active")
            .setContentText("BLE & telemetry link active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "WearForegroundService"
        const val CHANNEL_ID = "motowear_wear_foreground_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP_SERVICE = "com.motowear.wear.action.STOP_FOREGROUND"

        fun start(context: Context) {
            val intent = Intent(context, WearForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WearForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
