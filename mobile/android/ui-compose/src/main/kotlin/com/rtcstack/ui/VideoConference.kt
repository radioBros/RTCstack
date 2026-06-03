package com.rtcstack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rtcstack.sdk.Call
import com.rtcstack.sdk.ConnectionState
import com.rtcstack.sdk.Layout
import kotlinx.coroutines.launch

/**
 * Drop-in conference UI — the Compose analogue of `VideoConference.tsx`. Observes the [Call]'s
 * StateFlows and renders the grid + control bar, handling connecting/disconnected states.
 *
 * ```kotlin
 * setContent { RTCstackTheme { VideoConference(call, onLeave = { finish() }) } }
 * ```
 *
 * Screen share requires a [com.rtcstack.sdk.screenshare.ScreenShareLauncher] wired from the
 * host Activity (Activity Result API can't be registered from inside composition); pass its
 * `launch`/`stop` via [onToggleScreenShare].
 */
@Composable
public fun VideoConference(
    call: Call,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleScreenShare: (() -> Unit)? = null,
) {
    val colors = RTCstackTheme.colors
    val scope = rememberCoroutineScope()

    val connectionState by call.connectionState.collectAsStateWithLifecycle()
    val participantsMap by call.participants.collectAsStateWithLifecycle()
    val local by call.localParticipant.collectAsStateWithLifecycle()
    val layout by call.layout.collectAsStateWithLifecycle()
    val pinned by call.pinnedParticipant.collectAsStateWithLifecycle()

    when (connectionState) {
        ConnectionState.IDLE, ConnectionState.CONNECTING ->
            Centered(modifier) { CircularProgressIndicator(); Text("Connecting…", color = colors.textMuted) }

        ConnectionState.DISCONNECTED ->
            Centered(modifier) { Text("You have left the call.", color = colors.textMuted) }

        else -> {
            // All participants (remote + local) for the grid.
            val all = buildList {
                local?.let { add(it) }
                addAll(participantsMap.values)
            }
            Column(modifier.fillMaxSize()) {
                VideoGrid(
                    call = call,
                    participants = all,
                    layout = layout,
                    pinnedId = pinned,
                    modifier = Modifier.weight(1f),
                )
                ControlBar(
                    micMuted = local?.isMuted ?: false,
                    cameraOff = local?.isCameraOff ?: false,
                    screenSharing = local?.isScreenSharing ?: false,
                    onToggleMic = { scope.launch { call.toggleMic() } },
                    onToggleCamera = { scope.launch { call.toggleCamera() } },
                    onToggleScreenShare = { onToggleScreenShare?.invoke() },
                    onReaction = { emoji -> scope.launch { call.sendReaction(emoji) } },
                    onCycleLayout = {
                        call.setLayout(if (layout == Layout.GRID) Layout.SPOTLIGHT else Layout.GRID)
                    },
                    onLeave = { scope.launch { call.disconnect(); onLeave() } },
                )
            }
        }
    }
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
