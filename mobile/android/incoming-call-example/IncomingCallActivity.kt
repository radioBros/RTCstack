package com.rtcstack.example.incoming

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.rtcstack.sdk.Call
import com.rtcstack.sdk.CallOptions
import com.rtcstack.sdk.RTCstack
import com.rtcstack.sdk.call.RTCstackCallService
import com.rtcstack.sdk.incoming.IncomingCallManager
import com.rtcstack.ui.RTCstackTheme
import com.rtcstack.ui.VideoConference
import kotlinx.coroutines.launch

/**
 * REFERENCE TEMPLATE. Launched (full-screen) by an incoming-call notification. On ACCEPT it
 * mints a token from your backend and connects; on DECLINE it dismisses.
 *
 * This mirrors iOS's `CallCoordinator` answer path: surface UI → on accept mint token → connect.
 * Register with `showWhenLocked`/`turnScreenOn` so it appears over the lock screen.
 */
class IncomingCallActivity : ComponentActivity() {

    private var call: Call? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val roomId = intent.getStringExtra("roomId") ?: return finish()
        val callerId = intent.getStringExtra("callerId") ?: "unknown"
        val callId = roomId.hashCode()
        IncomingCallManager(this).cancel(callId)

        when (intent.action) {
            ACTION_DECLINE -> { finish(); return }
            else -> accept(roomId, callerId) // ACTION_ACCEPT or full-screen tap
        }
    }

    private fun accept(roomId: String, callerId: String) {
        lifecycleScope.launch {
            // Mint a token from YOUR backend (POST /v1/token). Never embed API secrets.
            val (token, url) = mintToken(roomId, callerId)
            val c = RTCstack.createCall(this@IncomingCallActivity, CallOptions(token = token, url = url))
            call = c
            RTCstackCallService.start(this@IncomingCallActivity)
            runCatching { c.connect(); c.setMicEnabled(true); c.setCameraEnabled(true) }
            setContent {
                RTCstackTheme {
                    VideoConference(call = c, onLeave = {
                        RTCstackCallService.stop(this@IncomingCallActivity)
                        c.release(); finish()
                    })
                }
            }
        }
    }

    /** Replace with a real call to your authenticated backend. */
    private suspend fun mintToken(roomId: String, callerId: String): Pair<String, String> {
        throw NotImplementedError("Call your backend POST /v1/token with roomId=$roomId, userId=$callerId")
    }

    companion object {
        const val ACTION_ACCEPT = "com.rtcstack.example.ACCEPT"
        const val ACTION_DECLINE = "com.rtcstack.example.DECLINE"
    }
}
