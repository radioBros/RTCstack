package com.rtcstack.sdk

/**
 * Discrete events emitted by a [Call], mirroring the shipped `@rtcstack/sdk` CallEventMap.
 *
 * Collect via [Call.events] (a SharedFlow). For snapshot state (connection state,
 * participant list, messages) prefer the dedicated StateFlows on [Call], which the UI
 * kit observes directly.
 *
 * Bucket mapping (development/mobile/plan.md):
 *  - Ported verbatim: the connection/participant/messaging/transcript events.
 *  - Dropped from web: `audioPlaybackBlocked` (no native autoplay policy).
 *  - Added for native: [CallResumed], [PermissionDenied] carries native DeviceKind.
 */
public sealed interface CallEvent {
    public data class ConnectionStateChanged(val state: ConnectionState) : CallEvent

    public data class ParticipantJoined(val participant: Participant) : CallEvent
    public data class ParticipantLeft(val participant: Participant) : CallEvent
    public data class ParticipantUpdated(val participant: Participant) : CallEvent
    public data class ActiveSpeakerChanged(val speakers: List<Participant>) : CallEvent

    public data class ScreenShareStarted(val participant: Participant) : CallEvent
    public data class ScreenShareStopped(val participantId: String) : CallEvent

    public data object RecordingStarted : CallEvent
    public data object RecordingStopped : CallEvent

    public data class MessageReceived(val message: Message) : CallEvent
    public data class ReactionReceived(val from: String, val emoji: String) : CallEvent
    public data class TranscriptReceived(val segment: TranscriptSegment) : CallEvent
    public data class SpeakingStarted(val speakerId: String, val speakerName: String) : CallEvent
    public data class SpeakingStopped(val speakerId: String) : CallEvent

    public data class Reconnecting(val attempt: Int) : CallEvent
    public data object Reconnected : CallEvent
    public data class Disconnected(val reason: DisconnectReason) : CallEvent
    public data object TokenExpired : CallEvent

    /** App backgrounded — media may be suspended (handled by the foreground service). */
    public data object CallSuspended : CallEvent
    /** App returned to foreground. */
    public data object CallResumed : CallEvent

    public data class PermissionDenied(val kind: DeviceKind) : CallEvent
    public data class DevicesChanged(val devices: DeviceList) : CallEvent

    public data class Error(val code: CallErrorCode, val message: String) : CallEvent
}
