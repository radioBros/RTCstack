package com.rtcstack.sdk

import android.content.Context
import android.content.Intent
import android.util.Base64
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant as LKParticipant
import io.livekit.android.room.participant.ConnectionQuality as LKQuality
import io.livekit.android.room.track.AudioTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * The primary RTCstack call object — a thin, idiomatic Kotlin wrapper over a LiveKit [Room],
 * mirroring the shipped `@rtcstack/sdk` `Call` class (`packages/sdk/src/call.ts`).
 *
 * Reactive surface:
 *  - [events]: one-shot [CallEvent]s (joined/left/message/etc.).
 *  - [connectionState], [participants], [localParticipant], [activeSpeakers], [messages],
 *    [layout], [pinnedParticipant]: snapshot StateFlows the UI observes.
 *
 * Construct via [RTCstack.createCall]. Does NOT connect until [connect] is called.
 */
public class Call internal constructor(
    private val appContext: Context,
    private val options: CallOptions,
) {
    private val room: Room = LiveKit.create(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var roomEventsJob: Job? = null
    private val msgCounter = AtomicLong(0)

    private val _events = MutableSharedFlow<CallEvent>(extraBufferCapacity = 64)
    public val events: SharedFlow<CallEvent> = _events.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    public val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _participants = MutableStateFlow<Map<String, Participant>>(emptyMap())
    public val participants: StateFlow<Map<String, Participant>> = _participants.asStateFlow()

    private val _localParticipant = MutableStateFlow<Participant?>(null)
    public val localParticipant: StateFlow<Participant?> = _localParticipant.asStateFlow()

    private val _activeSpeakers = MutableStateFlow<List<Participant>>(emptyList())
    public val activeSpeakers: StateFlow<List<Participant>> = _activeSpeakers.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    public val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _layout = MutableStateFlow(Layout.GRID)
    public val layout: StateFlow<Layout> = _layout.asStateFlow()

    private val _pinned = MutableStateFlow<String?>(null)
    public val pinnedParticipant: StateFlow<String?> = _pinned.asStateFlow()

    /** Token expiry (epoch millis), decoded from the JWT at connect time. */
    public var tokenExpiresAt: Long = 0L
        private set

    public val livekitUrl: String get() = options.url

    /** Underlying LiveKit room — exposed for the UI kit (components-android) to render tracks. */
    public val lkRoom: Room get() = room

    // ─── Connection ──────────────────────────────────────────────────────────

    public suspend fun connect() {
        setState(ConnectionState.CONNECTING)
        options.onAnalyticsEvent?.invoke("connect_start", mapOf("url" to options.url))

        var token = options.token
        tokenExpiresAt = parseTokenExpiry(token)

        val refresher = options.tokenRefresher
        if (refresher != null && System.currentTimeMillis() >= tokenExpiresAt - 60_000) {
            token = refresher()
            tokenExpiresAt = parseTokenExpiry(token)
        }

        startObservingRoom()
        try {
            room.connect(options.url, token)
        } catch (e: Exception) {
            setState(ConnectionState.DISCONNECTED)
            val code = classifyConnectError(e)
            _events.tryEmit(CallEvent.Error(code, e.message ?: "connect failed"))
            throw CallException(code, e.message ?: "connect failed", e)
        }
    }

    public suspend fun disconnect() {
        room.disconnect()
        setState(ConnectionState.DISCONNECTED)
        roomEventsJob?.cancel()
    }

    /** Releases the room and coroutine scope. Call when the call object is no longer needed. */
    public fun release() {
        roomEventsJob?.cancel()
        room.release()
    }

    // ─── Media controls ──────────────────────────────────────────────────────

    public suspend fun toggleMic() {
        room.localParticipant.setMicrophoneEnabled(!room.localParticipant.isMicrophoneEnabled)
    }

    public suspend fun setMicEnabled(enabled: Boolean) {
        room.localParticipant.setMicrophoneEnabled(enabled)
    }

    public suspend fun toggleCamera() {
        room.localParticipant.setCameraEnabled(!room.localParticipant.isCameraEnabled)
    }

    public suspend fun setCameraEnabled(enabled: Boolean) {
        room.localParticipant.setCameraEnabled(enabled)
    }

    /**
     * Start screen share. Requires the MediaProjection consent Intent from
     * [com.rtcstack.sdk.screenshare.ScreenShareLauncher] AND a running
     * [com.rtcstack.sdk.call.RTCstackCallService] foreground service of type mediaProjection.
     */
    public suspend fun startScreenShare(mediaProjectionPermissionResultData: Intent) {
        room.localParticipant.setScreenShareEnabled(
            true,
            ScreenCaptureParams(mediaProjectionPermissionResultData),
        )
    }

    public suspend fun stopScreenShare() {
        room.localParticipant.setScreenShareEnabled(false)
    }

    public fun isScreenSharing(): Boolean =
        room.localParticipant.getTrackPublication(Track.Source.SCREEN_SHARE)?.track != null

    // ─── Messaging ───────────────────────────────────────────────────────────

    /**
     * Send a chat message. NOTE: matches the shipped web SDK — LiveKit does NOT loop data
     * back to the sender, so neither [messages] nor a [CallEvent.MessageReceived] fires
     * locally. The UI layer is responsible for echoing the user's own outgoing messages.
     */
    public suspend fun sendMessage(text: String, to: List<String>? = null) {
        val id = msgCounter.incrementAndGet().toString()
        publish(WireFormat.encodeChat(text, id), to)
    }

    /** Send a reaction. Like [sendMessage], no local echo — the UI shows the sender's own reaction. */
    public suspend fun sendReaction(emoji: String) {
        publish(WireFormat.encodeReaction(emoji), null)
    }

    private suspend fun publish(payload: ByteArray, to: List<String>?) {
        val identities = to?.map { LKParticipant.Identity(it) }
        // NOTE(verify-on-mac): confirm publishData signature for the pinned livekit-android version.
        room.localParticipant.publishData(data = payload, identities = identities)
    }

    // ─── Layout state ────────────────────────────────────────────────────────

    public fun setLayout(layout: Layout) { _layout.value = layout }
    public fun pin(participantId: String?) { _pinned.value = participantId }

    // ─── Internals ───────────────────────────────────────────────────────────

    private fun setState(state: ConnectionState) {
        _connectionState.value = state
        _events.tryEmit(CallEvent.ConnectionStateChanged(state))
    }

    private fun startObservingRoom() {
        roomEventsJob?.cancel()
        roomEventsJob = scope.launch {
            room.events.collect { event -> handleRoomEvent(event) }
        }
    }

    private fun handleRoomEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.Connected -> {
                setState(ConnectionState.CONNECTED)
                options.onAnalyticsEvent?.invoke("connect_success", emptyMap())
                syncParticipants()
                syncLocal()
            }
            is RoomEvent.Disconnected -> {
                setState(ConnectionState.DISCONNECTED)
                _events.tryEmit(CallEvent.Disconnected(mapDisconnect(event)))
            }
            is RoomEvent.Reconnecting -> {
                setState(ConnectionState.RECONNECTING)
                _events.tryEmit(CallEvent.Reconnecting(1))
            }
            is RoomEvent.Reconnected -> {
                setState(ConnectionState.CONNECTED)
                _events.tryEmit(CallEvent.Reconnected)
            }
            is RoomEvent.ParticipantConnected -> {
                val p = mapParticipant(event.participant)
                _participants.value = _participants.value + (p.id to p)
                _events.tryEmit(CallEvent.ParticipantJoined(p))
            }
            is RoomEvent.ParticipantDisconnected -> {
                val id = event.participant.identity?.value ?: return
                val existing = _participants.value[id] ?: mapParticipant(event.participant)
                _participants.value = _participants.value - id
                _events.tryEmit(CallEvent.ParticipantLeft(existing))
            }
            is RoomEvent.ActiveSpeakersChanged -> {
                val speakers = event.speakers.map { mapParticipant(it) }
                _activeSpeakers.value = speakers
                _events.tryEmit(CallEvent.ActiveSpeakerChanged(speakers))
                syncParticipants(emitUpdates = true)
            }
            is RoomEvent.TrackPublished -> {
                syncParticipants(emitUpdates = true)
                if (event.publication.source == Track.Source.SCREEN_SHARE) {
                    _events.tryEmit(CallEvent.ScreenShareStarted(mapParticipant(event.participant)))
                }
            }
            is RoomEvent.TrackUnpublished -> {
                syncParticipants(emitUpdates = true)
                if (event.publication.source == Track.Source.SCREEN_SHARE) {
                    _events.tryEmit(CallEvent.ScreenShareStopped(event.participant.identity?.value ?: ""))
                }
            }
            is RoomEvent.TrackSubscribed -> syncParticipants(emitUpdates = true)
            is RoomEvent.TrackUnsubscribed -> syncParticipants(emitUpdates = true)
            is RoomEvent.TrackMuted -> syncParticipants(emitUpdates = true)
            is RoomEvent.TrackUnmuted -> syncParticipants(emitUpdates = true)
            is RoomEvent.ParticipantMetadataChanged -> syncParticipants(emitUpdates = true)
            is RoomEvent.ConnectionQualityChanged -> syncParticipants(emitUpdates = true)
            is RoomEvent.RecordingStatusChanged -> {
                // 2.18.2's RecordingStatusChanged exposes no property accessor; read off the Room.
                if (room.isRecording) _events.tryEmit(CallEvent.RecordingStarted)
                else _events.tryEmit(CallEvent.RecordingStopped)
            }
            is RoomEvent.DataReceived -> handleData(event)
            else -> Unit // forward-compat: ignore LiveKit events we don't surface
        }
    }

    private fun handleData(event: RoomEvent.DataReceived) {
        val sender = event.participant
        when (val inbound = WireFormat.decode(event.data)) {
            is WireFormat.Inbound.Chat -> {
                val msg = Message(
                    id = inbound.id ?: msgCounter.incrementAndGet().toString(),
                    from = sender?.identity?.value ?: "unknown",
                    fromName = sender?.name ?: sender?.identity?.value ?: "unknown",
                    text = inbound.text,
                    timestamp = System.currentTimeMillis(),
                    to = null,
                )
                _messages.value = (_messages.value + msg).takeLast(500)
                _events.tryEmit(CallEvent.MessageReceived(msg))
            }
            is WireFormat.Inbound.Reaction ->
                _events.tryEmit(CallEvent.ReactionReceived(sender?.identity?.value ?: "unknown", inbound.emoji))
            is WireFormat.Inbound.Speaking -> {
                val sid = inbound.speakerId ?: sender?.identity?.value ?: "unknown"
                val sname = inbound.speaker ?: sender?.name ?: sid
                _events.tryEmit(CallEvent.SpeakingStarted(sid, sname))
            }
            is WireFormat.Inbound.Transcript -> {
                val sid = inbound.speakerId ?: sender?.identity?.value ?: "unknown"
                _events.tryEmit(CallEvent.SpeakingStopped(sid))
                _events.tryEmit(
                    CallEvent.TranscriptReceived(
                        TranscriptSegment(
                            text = inbound.text,
                            speaker = inbound.speaker ?: sender?.name ?: sid,
                            speakerId = sid,
                            timestamp = System.currentTimeMillis(),
                            startMs = inbound.startMs,
                        ),
                    ),
                )
            }
            WireFormat.Inbound.Ignored, null -> Unit
        }
    }

    private fun syncParticipants(emitUpdates: Boolean = false) {
        val mapped = room.remoteParticipants.values.associate {
            val p = mapParticipant(it)
            p.id to p
        }
        _participants.value = mapped
        if (emitUpdates) {
            mapped.values.forEach { _events.tryEmit(CallEvent.ParticipantUpdated(it)) }
            syncLocal()
        }
    }

    private fun syncLocal() {
        val p = mapParticipant(room.localParticipant, isLocal = true)
        _localParticipant.value = p
        _events.tryEmit(CallEvent.ParticipantUpdated(p))
    }

    private fun mapParticipant(p: LKParticipant, isLocal: Boolean = false): Participant {
        val camera = p.getTrackPublication(Track.Source.CAMERA)?.track as? VideoTrack
        val mic = p.getTrackPublication(Track.Source.MICROPHONE)?.track as? AudioTrack
        val screen = p.getTrackPublication(Track.Source.SCREEN_SHARE)?.track as? VideoTrack
        val meta = parseMetadata(p.metadata)
        return Participant(
            id = p.identity?.value ?: "",
            name = p.name ?: p.identity?.value ?: "",
            role = ParticipantRole.fromString(meta["role"] as? String),
            isMuted = !p.isMicrophoneEnabled,
            isCameraOff = !p.isCameraEnabled,
            isSpeaking = p.isSpeaking,
            connectionQuality = mapQuality(p.connectionQuality),
            videoTrack = camera,
            audioTrack = mic,
            screenShareTrack = screen,
            isScreenSharing = screen != null,
            isLocal = isLocal,
            metadata = meta,
        )
    }

    private fun mapQuality(q: LKQuality): ConnectionQuality = when (q) {
        LKQuality.EXCELLENT -> ConnectionQuality.EXCELLENT
        LKQuality.GOOD -> ConnectionQuality.GOOD
        LKQuality.POOR -> ConnectionQuality.POOR
        LKQuality.LOST -> ConnectionQuality.LOST
        else -> ConnectionQuality.UNKNOWN
    }

    private fun mapDisconnect(event: RoomEvent.Disconnected): DisconnectReason {
        // NOTE(verify-on-mac): map event.reason (livekit DisconnectReason) precisely.
        return DisconnectReason.UNKNOWN
    }

    private fun classifyConnectError(e: Exception): CallErrorCode {
        val m = e.message?.lowercase().orEmpty()
        return when {
            "auth" in m || "401" in m || "token" in m -> CallErrorCode.AUTH_FAILED
            "not found" in m || "404" in m -> CallErrorCode.ROOM_NOT_FOUND
            else -> CallErrorCode.NETWORK_ERROR
        }
    }

    private fun parseTokenExpiry(token: String): Long {
        return try {
            val parts = token.split(".")
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            val exp = JSONObject(payload).optLong("exp", 0L)
            if (exp > 0) exp * 1000 else System.currentTimeMillis() + 6 * 3600_000L
        } catch (_: Exception) {
            System.currentTimeMillis() + 6 * 3600_000L
        }
    }
}
