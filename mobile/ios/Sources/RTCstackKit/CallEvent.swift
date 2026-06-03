import Foundation

/// Discrete events emitted by a ``Call``, mirroring the shipped `@rtcstack/sdk` CallEventMap.
///
/// Subscribe via ``Call/events`` (a Combine publisher). For snapshot state, observe the
/// `@Published` properties on ``Call`` directly from SwiftUI.
///
/// Bucket mapping (development/mobile/plan.md): the web-only `audioPlaybackBlocked` is dropped
/// (no autoplay policy on native); `callResumed` and native permission events are added.
public enum CallEvent {
    case connectionStateChanged(ConnectionState)

    case participantJoined(Participant)
    case participantLeft(Participant)
    case participantUpdated(Participant)
    case activeSpeakerChanged([Participant])

    case screenShareStarted(Participant)
    case screenShareStopped(participantId: String)

    case recordingStarted
    case recordingStopped

    case messageReceived(Message)
    case reactionReceived(from: String, emoji: String)
    case transcriptReceived(TranscriptSegment)
    case speakingStarted(speakerId: String, speakerName: String)
    case speakingStopped(speakerId: String)

    case reconnecting(attempt: Int)
    case reconnected
    case disconnected(DisconnectReason)
    case tokenExpired

    /// App backgrounded — see ``AudioSessionManager`` / lifecycle handling.
    case callSuspended
    case callResumed

    case permissionDenied(DeviceKind)

    case error(CallErrorCode, String)
}
