package com.rtcstack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Which controls to show, mirroring `ControlBarButton` from the React kit. */
public enum class ControlButton { MIC, CAMERA, SCREENSHARE, REACTIONS, LAYOUT, LEAVE }

private val DEFAULT_BUTTONS = listOf(
    ControlButton.MIC, ControlButton.CAMERA, ControlButton.SCREENSHARE,
    ControlButton.REACTIONS, ControlButton.LAYOUT, ControlButton.LEAVE,
)
private val REACTIONS = listOf("👍", "❤️", "😂", "🎉", "👏", "🙌")

/**
 * Bottom control bar mirroring `ControlBar.tsx`. Pure presentational — wire the lambdas to
 * a [com.rtcstack.sdk.Call] (typically via [VideoConference]).
 */
@Composable
public fun ControlBar(
    micMuted: Boolean,
    cameraOff: Boolean,
    screenSharing: Boolean,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onReaction: (String) -> Unit,
    onCycleLayout: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    buttons: List<ControlButton> = DEFAULT_BUTTONS,
) {
    val colors = RTCstackTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface1)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (ControlButton.MIC in buttons) {
            CircleControl(label = if (micMuted) "🎤✕" else "🎤", active = micMuted, danger = micMuted, onClick = onToggleMic)
        }
        if (ControlButton.CAMERA in buttons) {
            CircleControl(label = if (cameraOff) "📷✕" else "📷", active = cameraOff, danger = cameraOff, onClick = onToggleCamera)
        }
        if (ControlButton.SCREENSHARE in buttons) {
            CircleControl(label = "🖥", active = screenSharing, onClick = onToggleScreenShare)
        }
        if (ControlButton.REACTIONS in buttons) {
            REACTIONS.forEach { emoji ->
                CircleControl(label = emoji, onClick = { onReaction(emoji) })
            }
        }
        if (ControlButton.LAYOUT in buttons) {
            CircleControl(label = "⊞", onClick = onCycleLayout)
        }
        if (ControlButton.LEAVE in buttons) {
            CircleControl(label = "✕", danger = true, onClick = onLeave)
        }
    }
}

@Composable
private fun CircleControl(
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    danger: Boolean = false,
) {
    val colors = RTCstackTheme.colors
    val container = when {
        danger -> colors.danger
        active -> colors.accent
        else -> colors.surface2
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).clip(CircleShape),
        colors = IconButtonDefaults.iconButtonColors(containerColor = container),
    ) {
        Text(label, color = if (danger || active) Color.White else colors.text)
    }
}
