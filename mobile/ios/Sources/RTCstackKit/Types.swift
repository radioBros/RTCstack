import Foundation
import LiveKit

/// Connection lifecycle state. Mirrors `@rtcstack/sdk` ConnectionState.
public enum ConnectionState: String, Sendable {
    case idle, connecting, connected, reconnecting, disconnected
}

/// Participant role, encoded in the LiveKit JWT and enforced server-side.
public enum ParticipantRole: String, Sendable {
    case host, moderator, participant, viewer

    init(fromString value: String?) {
        switch value?.lowercased() {
        case "host": self = .host
        case "moderator": self = .moderator
        case "viewer": self = .viewer
        default: self = .participant
        }
    }
}

/// Signal-layer connection quality.
public enum ConnectionQuality: String, Sendable {
    case excellent, good, poor, lost, unknown
}

/// Layout state. Pure state — the UI kit renders based on it.
public enum Layout: String, Sendable {
    case grid, speaker, spotlight
}

/// Device categories. Audio output is OS/route-managed via ``AudioSessionManager``.
public enum DeviceKind: String, Sendable {
    case audioInput, audioOutput, videoInput
}

/// Reason a call ended.
public enum DisconnectReason: String, Sendable {
    case kicked, roomClosed, tokenExpired, maxRetries
    case duplicateIdentity, userInitiated, serverShutdown, unknown
}

/// Error codes surfaced via ``CallEvent/error(_:_:)`` or thrown from ``Call/connect()``.
public enum CallErrorCode: String, Sendable {
    case authFailed, roomNotFound, permissionDenied, deviceNotFound
    case permissionDeniedMic, permissionDeniedCam, screenShareDenied
    case tokenRefreshFailed, networkError, unknown
}

public struct CallError: Error, Sendable {
    public let code: CallErrorCode
    public let message: String
    public init(_ code: CallErrorCode, _ message: String) {
        self.code = code
        self.message = message
    }
}

/// A participant snapshot. Tracks are LiveKit-native (the iOS analogue of the web SDK's
/// `MediaStreamTrack`). Re-read from ``Call/participants`` after change events.
///
/// NOTE: `VideoTrack`/`AudioTrack` are reference types owned by the SDK; this struct is used
/// on the main actor only — do not retain tracks past room disconnect.
public struct Participant: Identifiable {
    public let id: String
    public let name: String
    public let role: ParticipantRole
    public let isMuted: Bool
    public let isCameraOff: Bool
    public let isSpeaking: Bool
    public let connectionQuality: ConnectionQuality
    public let videoTrack: VideoTrack?
    public let audioTrack: AudioTrack?
    public let screenShareTrack: VideoTrack?
    public let isScreenSharing: Bool
    public let isLocal: Bool
    public let metadata: [String: String]
}

/// A peer chat message received over the LiveKit data channel.
public struct Message: Identifiable, Sendable {
    public let id: String
    public let from: String
    public let fromName: String
    public let text: String
    public let timestamp: Date
    public let to: [String]?

    // Public so the UI kit can construct local echoes of the user's own outgoing messages
    // (the SDK does not loop sent data back — see Call.sendMessage docs).
    public init(id: String, from: String, fromName: String, text: String, timestamp: Date, to: [String]?) {
        self.id = id
        self.from = from
        self.fromName = fromName
        self.text = text
        self.timestamp = timestamp
        self.to = to
    }
}

/// A live transcript segment delivered over the data channel.
public struct TranscriptSegment: Sendable {
    public let text: String
    public let speaker: String
    public let speakerId: String
    public let timestamp: Date
    public let startMs: Int64?
}

/// Options for creating a ``Call``.
///
/// SECURITY: the SDK never holds the RTCstack API key/secret. `token` and `url` come from your
/// backend (which mints LiveKit JWTs). `tokenRefresher` calls that backend.
public struct CallOptions: Sendable {
    public let token: String
    public let url: String
    public let tokenRefresher: (@Sendable () async throws -> String)?
    public let onAnalyticsEvent: (@Sendable (String, [String: String]) -> Void)?

    public init(
        token: String,
        url: String,
        tokenRefresher: (@Sendable () async throws -> String)? = nil,
        onAnalyticsEvent: (@Sendable (String, [String: String]) -> Void)? = nil
    ) {
        self.token = token
        self.url = url
        self.tokenRefresher = tokenRefresher
        self.onAnalyticsEvent = onAnalyticsEvent
    }
}
