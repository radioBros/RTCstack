package com.rtcstack.example.incoming

import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rtcstack.sdk.incoming.IncomingCallManager

/**
 * REFERENCE TEMPLATE (requires firebase-messaging — see README).
 *
 * Receives high-priority FCM data messages and surfaces an incoming-call notification via the
 * SDK's [IncomingCallManager]. The notification's accept/decline intents target
 * [IncomingCallActivity], which mints a token and connects.
 *
 * Register in the app manifest:
 * ```xml
 * <service android:name=".incoming.RTCstackMessagingService" android:exported="false">
 *   <intent-filter><action android:name="com.google.firebase.MESSAGING_EVENT" /></intent-filter>
 * </service>
 * ```
 */
class RTCstackMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Forward to YOUR backend so it can target this device with VoIP-style pushes.
        // backend.registerPushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val roomId = data["roomId"] ?: return
        val callerName = data["callerName"] ?: "Unknown"
        val callId = roomId.hashCode()

        val base = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("roomId", roomId)
            putExtra("callerId", data["callerId"])
            putExtra("callerName", callerName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val accept = Intent(base).apply { action = IncomingCallActivity.ACTION_ACCEPT }
        val decline = Intent(base).apply { action = IncomingCallActivity.ACTION_DECLINE }

        IncomingCallManager(this).showIncomingCall(
            callId = callId,
            callerName = callerName,
            fullScreenIntent = base,
            acceptIntent = accept,
            declineIntent = decline,
        )
    }
}
