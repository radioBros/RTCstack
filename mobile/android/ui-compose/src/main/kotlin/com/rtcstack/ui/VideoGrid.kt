package com.rtcstack.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rtcstack.sdk.Call
import com.rtcstack.sdk.Layout
import com.rtcstack.sdk.Participant
import io.livekit.android.room.Room
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Adaptive participant grid mirroring `VideoGrid.tsx`. Column count scales with participant
 * count; in SPOTLIGHT layout the pinned participant fills the view.
 */
@Composable
public fun VideoGrid(
    call: Call,
    participants: List<Participant>,
    layout: Layout,
    pinnedId: String?,
    modifier: Modifier = Modifier,
) {
    val room: Room = call.lkRoom

    if (layout == Layout.SPOTLIGHT && pinnedId != null) {
        val pinned = participants.firstOrNull { it.id == pinnedId } ?: participants.firstOrNull()
        if (pinned != null) {
            ParticipantVideo(room, pinned, modifier.fillMaxSize().padding(4.dp))
            return
        }
    }

    val columns = if (participants.isEmpty()) 1 else ceil(sqrt(participants.size.toDouble())).toInt()
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        modifier = modifier.fillMaxSize().padding(4.dp),
    ) {
        items(participants, key = { it.id }) { participant ->
            ParticipantVideo(
                room = room,
                participant = participant,
                modifier = Modifier.padding(4.dp).aspectRatio(16f / 9f),
            )
        }
    }
}
