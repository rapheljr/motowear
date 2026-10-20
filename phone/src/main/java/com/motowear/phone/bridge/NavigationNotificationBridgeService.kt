package com.motowear.phone.bridge

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Section 2: Dual-Ingest Notification Bridge (`NotificationListenerService`).
 *
 * Hooks ongoing turn-by-turn notifications from the Official Beeline App (`co.beeline`),
 * Google Maps (`com.google.android.apps.maps`), and OsmAnd (`net.osmand`), converts them
 * into `ExternalNavSnapshot` frames, and publishes them to `ExternalNavBridgeRepository`
 * for 10Hz Protobuf streaming to the Wear OS watch.
 */
class NavigationNotificationBridgeService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val pkg = sbn.packageName ?: return

        if (!NavigationBridgeParser.isSupportedPackage(pkg)) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val progress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val progressMax = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)

        if (title.isBlank() && text.isBlank()) return

        val snapshot = NavigationBridgeParser.parseNotificationTexts(
            packageName = pkg,
            title = title,
            text = text,
            subText = subText,
            progress = progress,
            progressMax = progressMax
        )
        ExternalNavBridgeRepository.publishSnapshot(snapshot)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val pkg = sbn?.packageName ?: return
        if (NavigationBridgeParser.isSupportedPackage(pkg)) {
            ExternalNavBridgeRepository.clearSnapshot()
        }
    }
}
