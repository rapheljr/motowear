package com.motowear.phone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.motowear.phone.MainActivity
import com.motowear.phone.R

/**
 * Android 11+ / 14+ Foreground Service (`FOREGROUND_SERVICE_TYPE_LOCATION`) keeping the
 * 10Hz GPS Course-Over-Ground & Wearable `MessageClient` telemetry pipeline active while the phone screen is off.
 * Uses the Beeline Roundel vector icon (`R.drawable.ic_beeline_stat`) in the status bar.
 */
class RideForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val statusText = intent?.getStringExtra(EXTRA_STATUS_TEXT)
            ?: "Streaming 10Hz telemetry to MotoWear OS watch"
        val notification = buildNotification(statusText)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MotoWear Active Ride Telemetry",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains 10Hz GPS Course-Over-Ground and Wear OS vector navigation link"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RideForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Beeline Moto II — Active Ride")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_beeline_stat)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop Ride", pendingStop)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "motowear_ride_telemetry_channel"
        const val NOTIFICATION_ID = 1002
        const val ACTION_STOP_SERVICE = "com.motowear.phone.action.STOP_FOREGROUND_RIDE"
        const val EXTRA_STATUS_TEXT = "com.motowear.phone.extra.STATUS_TEXT"

        fun start(context: Context, statusText: String = "Streaming 10Hz telemetry to MotoWear OS") {
            val intent = Intent(context, RideForegroundService::class.java).apply {
                putExtra(EXTRA_STATUS_TEXT, statusText)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RideForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
