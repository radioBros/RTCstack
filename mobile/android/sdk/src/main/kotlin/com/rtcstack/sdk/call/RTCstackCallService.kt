package com.rtcstack.sdk.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Ongoing-call foreground service. REQUIRED to keep mic/camera (and screen share) alive
 * while the app is backgrounded — Android kills background mic/camera access otherwise.
 *
 * Android 14+ requires each foreground-service type to be declared in the manifest AND
 * asserted at startForeground() time. This service starts with microphone|camera and is
 * promoted to include mediaProjection when screen share begins.
 *
 * Start with [start]; stop with [stop]. The host app supplies notification content.
 */
public class RTCstackCallService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Ongoing call"
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Tap to return to the call"
        val withProjection = intent?.getBooleanExtra(EXTRA_WITH_PROJECTION, false) ?: false

        startForeground(NOTIFICATION_ID, buildNotification(title, text), serviceType(withProjection))
        return START_STICKY
    }

    private fun serviceType(withProjection: Boolean): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (withProjection && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }
        return type
    }

    private fun buildNotification(title: String, text: String): Notification {
        ensureChannel(this)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    public companion object {
        private const val CHANNEL_ID = "rtcstack_ongoing_call"
        private const val NOTIFICATION_ID = 7273 // "RTC" keypad
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_WITH_PROJECTION = "withProjection"

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Ongoing call",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply { setShowBadge(false) },
                )
            }
        }

        /** Start the ongoing-call foreground service. Call after the call connects. */
        public fun start(
            context: Context,
            title: String = "Ongoing call",
            text: String = "Tap to return to the call",
            withScreenShare: Boolean = false,
        ) {
            val intent = Intent(context, RTCstackCallService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_WITH_PROJECTION, withScreenShare)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        public fun stop(context: Context) {
            context.stopService(Intent(context, RTCstackCallService::class.java))
        }
    }
}
