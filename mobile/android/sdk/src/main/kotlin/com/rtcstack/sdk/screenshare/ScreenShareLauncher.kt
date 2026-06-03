package com.rtcstack.sdk.screenshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.rtcstack.sdk.Call
import com.rtcstack.sdk.call.RTCstackCallService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Wraps the MediaProjection consent flow + foreground-service promotion into a single call.
 *
 * Screen share on Android requires, in order:
 *   1. user consent via the system MediaProjection dialog,
 *   2. a foreground service of type `mediaProjection` already running (Android 10+),
 *   3. handing the consent Intent to LiveKit's screen-share track.
 *
 * Register this in an Activity/Fragment `onCreate` (it uses the Activity Result API), then call
 * [launch] from a click handler.
 *
 * ```kotlin
 * private lateinit var screenShare: ScreenShareLauncher
 * override fun onCreate(...) {
 *     screenShare = ScreenShareLauncher(this, this, call, lifecycleScope)
 * }
 * // later: shareButton.setOnClickListener { screenShare.launch() }
 * ```
 */
public class ScreenShareLauncher(
    private val context: Context,
    caller: ActivityResultCaller,
    private val call: Call,
    private val scope: CoroutineScope,
    private val onDenied: (() -> Unit)? = null,
) {
    private val projectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private val launcher: ActivityResultLauncher<Intent> =
        caller.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                // Promote the ongoing-call FGS to include mediaProjection, THEN start the track.
                RTCstackCallService.start(context, withScreenShare = true)
                scope.launch { call.startScreenShare(data) }
            } else {
                onDenied?.invoke() // user cancelled the consent dialog → SCREEN_SHARE_DENIED
            }
        }

    /** Show the system screen-capture consent dialog. */
    public fun launch() {
        launcher.launch(projectionManager.createScreenCaptureIntent())
    }

    /** Stop sharing and demote the foreground service back to mic/camera only. */
    public fun stop() {
        scope.launch { call.stopScreenShare() }
        RTCstackCallService.start(context, withScreenShare = false)
    }
}
