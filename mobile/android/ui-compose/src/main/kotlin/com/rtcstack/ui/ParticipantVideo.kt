package com.rtcstack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rtcstack.sdk.Participant
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.room.Room

/**
 * Renders one participant's video tile, mirroring `ParticipantVideo.tsx`:
 * accent "speaking" ring, name label, and a camera-off placeholder.
 *
 * Video is drawn by LiveKit's Compose [VideoRenderer] (from livekit-android-compose-components).
 */
@Composable
public fun ParticipantVideo(
    room: Room,
    participant: Participant,
    modifier: Modifier = Modifier,
) {
    val colors = RTCstackTheme.colors
    val ringColor = if (participant.isSpeaking) colors.speakingRing else colors.border

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(RTCstackTokens.Radius.md))
            .background(colors.surface1)
            .border(if (participant.isSpeaking) 3.dp else 1.dp, ringColor, RoundedCornerShape(RTCstackTokens.Radius.md)),
    ) {
        val track = participant.videoTrack
        if (track != null && !participant.isCameraOff) {
            // components 2.x renamed VideoRenderer → VideoTrackView (io.livekit.android.compose.ui).
            // Positional: VideoTrackView(VideoTrack, Modifier, Room, …); rest have defaults.
            VideoTrackView(track, Modifier.fillMaxSize(), room)
        } else {
            Box(Modifier.fillMaxSize().background(colors.surface2), contentAlignment = Alignment.Center) {
                Text(participant.name.take(1).uppercase(), color = colors.textMuted)
            }
        }

        Text(
            text = if (participant.isMuted) "🔇 ${participant.name}" else participant.name,
            color = colors.captionText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(RTCstackTokens.Radius.sm))
                .background(colors.captionBg)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
