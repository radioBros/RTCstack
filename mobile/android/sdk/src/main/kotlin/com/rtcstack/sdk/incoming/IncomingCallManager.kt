package com.rtcstack.sdk.incoming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Builds and shows incoming-call notifications driven by a high-priority FCM data message
 * (Android's analogue of iOS VoIP push). This is the recommended baseline; full Telecom
 * self-managed `ConnectionService` integration is a heavier, optional upgrade (see
 * development/mobile/plan.md).
 *
 * Wiring (in the app, not the SDK — the SDK does not depend on firebase-messaging):
 *  1. App's `FirebaseMessagingService.onMessageReceived` extracts `{ roomId, callerName, ... }`.
 *  2. It calls [showIncomingCall], passing Activity intents for accept/decline.
 *  3. On accept, the app mints a token via its backend, then `RTCstack.createCall(...).connect()`.
 *
 * The notification uses a full-screen intent so it surfaces over the lock screen. Android 14+
 * restricts full-screen intents to calling/alarm apps with `USE_FULL_SCREEN_INTENT`.
 */
public class IncomingCallManager(private val context: Context) {

    /**
     * Show a heads-up / full-screen incoming-call notification.
     *
     * @param callId stable id so the notification can be dismissed on accept/decline/timeout.
     * @param callerName display name shown in the notification.
     * @param fullScreenIntent launched when the device is locked (your incoming-call Activity).
     * @param acceptIntent fired by the Accept action.
     * @param declineIntent fired by the Decline action.
     */
    public fun showIncomingCall(
        callId: Int,
        callerName: String,
        fullScreenIntent: Intent,
        acceptIntent: Intent,
        declineIntent: Intent,
    ) {
        ensureChannel()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val fsPending = PendingIntent.getActivity(context, callId, fullScreenIntent, flags)
        val acceptPending = PendingIntent.getActivity(context, callId + 1, acceptIntent, flags)
        val declinePending = PendingIntent.getActivity(context, callId + 2, declineIntent, flags)

        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(callerName)
            .setContentText("Incoming call")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(true)
            .setFullScreenIntent(fsPending, true)
            .addAction(android.R.drawable.sym_action_call, "Accept", acceptPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePending)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(callId, notification)
    }

    public fun cancel(callId: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(callId)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Incoming calls",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Notifications for incoming RTCstack calls"
                    setShowBadge(true)
                },
            )
        }
    }

    public companion object {
        private const val CHANNEL_ID = "rtcstack_incoming_call"
    }
}
