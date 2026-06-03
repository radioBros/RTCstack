package com.rtcstack.sdk

import io.livekit.android.room.track.AudioTrack
import io.livekit.android.room.track.VideoTrack

/**
 * Connection lifecycle state. Mirrors `@rtcstack/sdk` ConnectionState.
 *
 * idle → connecting → connected → reconnecting → disconnected
 */
public enum class ConnectionState {
    IDLE, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED
}

/** Participant role, encoded in the LiveKit JWT and enforced server-side. */
public enum class ParticipantRole {
    HOST, MODERATOR, PARTICIPANT, VIEWER;

    public companion object {
        public fun fromString(value: String?): ParticipantRole = when (value?.lowercase()) {
            "host" -> HOST
            "moderator" -> MODERATOR
            "viewer" -> VIEWER
            else -> PARTICIPANT
        }
    }
}

/** Signal-layer connection quality. */
public enum class ConnectionQuality {
    EXCELLENT, GOOD, POOR, LOST, UNKNOWN
}

/** Layout state. Pure state — the UI kit renders based on it; the SDK does no rendering. */
public enum class Layout {
    GRID, SPEAKER, SPOTLIGHT
}

/** Device categories. `audiooutput` selection is OS-routed on Android (see [AudioRouter]). */
public enum class DeviceKind {
    AUDIO_INPUT, AUDIO_OUTPUT, VIDEO_INPUT
}

/**
 * A participant in the room. Snapshot value type — re-read from [Call.participants]
 * (a StateFlow) after any change event.
 *
 * Note: tracks are LiveKit-native [VideoTrack]/[AudioTrack] — the native analogue of the
 * web SDK's `MediaStreamTrack` (Bucket B in development/mobile/plan.md).
 */
public data class Participant(
    val id: String,
    val name: String,
    val role: ParticipantRole,
    val isMuted: Boolean,
    val isCameraOff: Boolean,
    val isSpeaking: Boolean,
    val connectionQuality: ConnectionQuality,
    val videoTrack: VideoTrack?,
    val audioTrack: AudioTrack?,
    val screenShareTrack: VideoTrack?,
    val isScreenSharing: Boolean,
    val isLocal: Boolean,
    /** Parsed JWT metadata JSON, or empty map if absent/unparseable. */
    val metadata: Map<String, Any?>,
)

/** A peer chat message received over the LiveKit data channel. */
public data class Message(
    val id: String,
    val from: String,
    val fromName: String,
    val text: String,
    /** Epoch millis when the SDK observed the message. */
    val timestamp: Long,
    /** Recipient identities if targeted; null means broadcast. */
    val to: List<String>?,
)

/** A live transcript segment delivered over the data channel (topic-agnostic). */
public data class TranscriptSegment(
    val text: String,
    val speaker: String,
    val speakerId: String,
    val timestamp: Long,
    val startMs: Long? = null,
)

/** Snapshot of enumerated input devices. Audio output routing is handled by [AudioRouter]. */
public data class DeviceList(
    val audioInput: List<DeviceInfo> = emptyList(),
    val audioOutput: List<DeviceInfo> = emptyList(),
    val videoInput: List<DeviceInfo> = emptyList(),
)

/** Minimal device descriptor (native analogue of web `MediaDeviceInfo`). */
public data class DeviceInfo(
    val deviceId: String,
    val label: String,
    val kind: DeviceKind,
)

/** Reason a call ended. */
public enum class DisconnectReason {
    KICKED, ROOM_CLOSED, TOKEN_EXPIRED, MAX_RETRIES,
    DUPLICATE_IDENTITY, USER_INITIATED, SERVER_SHUTDOWN, UNKNOWN
}

/** Non-fatal and fatal error codes surfaced via [CallEvent.Error]. */
public enum class CallErrorCode {
    AUTH_FAILED, ROOM_NOT_FOUND, PERMISSION_DENIED, DEVICE_NOT_FOUND,
    PERMISSION_DENIED_MIC, PERMISSION_DENIED_CAM, SCREEN_SHARE_DENIED,
    TOKEN_REFRESH_FAILED, NETWORK_ERROR, UNKNOWN
}

public class CallException(
    public val code: CallErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Options for [RTCstack.createCall].
 *
 * SECURITY: the SDK never holds the RTCstack API key/secret. [token] and [url] come from
 * the customer's backend (which mints LiveKit JWTs). [tokenRefresher] calls that backend.
 */
public data class CallOptions(
    /** LiveKit JWT obtained from the app backend (POST /v1/token). */
    val token: String,
    /** WSS URL from the token response (e.g. wss://yourdomain.com/livekit). */
    val url: String,
    /**
     * Called automatically before a reconnect when the stored token is within 60s of
     * expiry or already expired. Must return a fresh JWT. If null and the token expires,
     * the SDK emits [CallEvent.TokenExpired] instead of reconnecting.
     */
    val tokenRefresher: (suspend () -> String)? = null,
    /** Optional analytics sink. Never called with PII. */
    val onAnalyticsEvent: ((event: String, data: Map<String, Any?>) -> Unit)? = null,
)
