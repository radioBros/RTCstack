import Combine
import Foundation
import LiveKit

// Public because they appear in `public` signatures (the `room` property and the
// RoomDelegate conformance methods). Qualified aliases sidestep the type-name shadowing
// between RTCstackKit's `Participant`/`ConnectionState`/… and LiveKit's same-named types.
public typealias LKRoom = LiveKit.Room
public typealias LKParticipant = LiveKit.Participant

/// The primary RTCstack call object — an idiomatic Swift wrapper over a LiveKit `Room`,
/// mirroring the shipped `@rtcstack/sdk` `Call` class (`packages/sdk/src/call.ts`).
///
/// Observe `@Published` state from SwiftUI, or subscribe to discrete ``events``.
///
/// ```swift
/// let call = RTCstack.createCall(.init(token: jwt, url: wssURL))
/// try await call.connect()
/// ```
///
/// ⚠️ VERIFY-ON-MAC: the `RoomDelegate` method signatures below are researched against
/// client-sdk-swift 2.x but not compiled. If a signature is wrong the method silently does
/// NOT override (delegate methods are optional) — confirm each against the pinned version's
/// `RoomDelegate`. `syncFromRoom()` is called from every delegate hook so state stays correct
/// even if one hook's signature drifts.
@MainActor
public final class Call: ObservableObject {

    // MARK: Published snapshot state
    @Published public private(set) var connectionState: ConnectionState = .idle
    @Published public private(set) var participants: [String: Participant] = [:]
    @Published public private(set) var localParticipant: Participant?
    @Published public private(set) var activeSpeakers: [Participant] = []
    @Published public private(set) var messages: [Message] = []
    @Published public private(set) var layout: Layout = .grid
    @Published public private(set) var pinnedParticipant: String?

    /// Discrete events (joined/left/message/reaction/...).
    public let events = PassthroughSubject<CallEvent, Never>()

    /// Token expiry, decoded from the JWT at connect time.
    public private(set) var tokenExpiresAt: Date = .init(timeIntervalSince1970: 0)

    public var livekitUrl: String { options.url }

    /// Underlying LiveKit room — exposed for the UI kit (components-swift) to render tracks.
    public let room: LKRoom

    private let options: CallOptions
    private var msgCounter: Int = 0

    init(options: CallOptions) {
        self.options = options
        self.room = LKRoom()
        self.room.add(delegate: self)
    }

    // MARK: Connection

    public func connect() async throws {
        setState(.connecting)
        options.onAnalyticsEvent?("connect_start", ["url": options.url])

        var token = options.token
        tokenExpiresAt = Self.parseTokenExpiry(token)

        if let refresher = options.tokenRefresher,
           Date().timeIntervalSince1970 >= tokenExpiresAt.timeIntervalSince1970 - 60 {
            do {
                token = try await refresher()
                tokenExpiresAt = Self.parseTokenExpiry(token)
            } catch {
                events.send(.error(.tokenRefreshFailed, "\(error)"))
                throw CallError(.tokenRefreshFailed, "\(error)")
            }
        }

        do {
            try await room.connect(url: options.url, token: token)
        } catch {
            setState(.disconnected)
            let code = Self.classifyConnectError(error)
            events.send(.error(code, "\(error)"))
            throw CallError(code, "\(error)")
        }
    }

    public func disconnect() async {
        await room.disconnect()
        setState(.disconnected)
    }

    // MARK: Media controls

    public func toggleMic() async throws {
        try await room.localParticipant.setMicrophone(enabled: !room.localParticipant.isMicrophoneEnabled())
    }

    public func setMicEnabled(_ enabled: Bool) async throws {
        try await room.localParticipant.setMicrophone(enabled: enabled)
    }

    public func toggleCamera() async throws {
        try await room.localParticipant.setCamera(enabled: !room.localParticipant.isCameraEnabled())
    }

    public func setCameraEnabled(_ enabled: Bool) async throws {
        try await room.localParticipant.setCamera(enabled: enabled)
    }

    /// Start screen share. On iOS this publishes via a Broadcast Upload Extension — see
    /// ``ScreenShareManager`` and MAC_HANDOFF.md §3 for the App Group + extension wiring.
    public func startScreenShare() async throws {
        try await room.localParticipant.setScreenShare(enabled: true)
    }

    public func stopScreenShare() async throws {
        try await room.localParticipant.setScreenShare(enabled: false)
    }

    public var isScreenSharing: Bool {
        room.localParticipant.firstScreenShareVideoTrack != nil
    }

    // MARK: Messaging
    // NOTE: matches shipped web SDK — LiveKit does not loop data back to the sender, so neither
    // `messages` nor a `messageReceived` event fires locally. The UI layer echoes own messages.

    public func sendMessage(_ text: String, to: [String]? = nil) async throws {
        msgCounter += 1
        let payload = WireFormat.encodeChat(text: text, id: String(msgCounter))
        try await publish(payload, to: to)
    }

    public func sendReaction(_ emoji: String) async throws {
        try await publish(WireFormat.encodeReaction(emoji: emoji), to: nil)
    }

    private func publish(_ data: Data, to: [String]?) async throws {
        // VERIFY-ON-MAC: confirm DataPublishOptions field names (reliable / destinationIdentities)
        // and the Participant.Identity wrapper for the pinned version.
        // NOTE: `Participant` unqualified = RTCstackKit's struct; use LKParticipant for LiveKit's.
        // destinationIdentities is non-optional ([] = broadcast to all) in 2.14.1.
        let identities = to?.map { LKParticipant.Identity(from: $0) } ?? []
        let opts = DataPublishOptions(destinationIdentities: identities, reliable: true)
        try await room.localParticipant.publish(data: data, options: opts)
    }

    // MARK: Layout state

    public func setLayout(_ layout: Layout) { self.layout = layout }
    public func pin(_ participantId: String?) { self.pinnedParticipant = participantId }

    // MARK: Internals

    private func setState(_ state: ConnectionState) {
        connectionState = state
        events.send(.connectionStateChanged(state))
    }

    fileprivate func syncFromRoom(emitUpdates: Bool = false) {
        var mapped: [String: Participant] = [:]
        for (_, p) in room.remoteParticipants {
            let participant = map(p)
            mapped[participant.id] = participant
            if emitUpdates { events.send(.participantUpdated(participant)) }
        }
        participants = mapped
        let local = map(room.localParticipant, isLocal: true)
        localParticipant = local
        if emitUpdates { events.send(.participantUpdated(local)) }
    }

    fileprivate func handleData(_ data: Data, from sender: LKParticipant?) {
        switch WireFormat.decode(data) {
        case let .chat(text, id):
            let msg = Message(
                id: id ?? { msgCounter += 1; return String(msgCounter) }(),
                from: sender?.identity?.stringValue ?? "unknown",
                fromName: sender?.name ?? sender?.identity?.stringValue ?? "unknown",
                text: text,
                timestamp: Date(),
                to: nil
            )
            messages = Array((messages + [msg]).suffix(500))
            events.send(.messageReceived(msg))
        case let .reaction(emoji):
            events.send(.reactionReceived(from: sender?.identity?.stringValue ?? "unknown", emoji: emoji))
        case let .speaking(speakerId, speaker):
            let sid = speakerId ?? sender?.identity?.stringValue ?? "unknown"
            events.send(.speakingStarted(speakerId: sid, speakerName: speaker ?? sender?.name ?? sid))
        case let .transcript(text, speakerId, speaker, startMs):
            let sid = speakerId ?? sender?.identity?.stringValue ?? "unknown"
            events.send(.speakingStopped(speakerId: sid))
            events.send(.transcriptReceived(.init(
                text: text,
                speaker: speaker ?? sender?.name ?? sid,
                speakerId: sid,
                timestamp: Date(),
                startMs: startMs
            )))
        case .ignored, .none:
            break
        }
    }

    private func map(_ p: LKParticipant, isLocal: Bool = false) -> Participant {
        // VERIFY-ON-MAC: confirm convenience accessors (firstCameraVideoTrack, firstAudioTrack,
        // firstScreenShareVideoTrack) and isMicrophoneEnabled()/isCameraEnabled() exist on this version.
        let meta = parseMetadata(p.metadata)
        return Participant(
            id: p.identity?.stringValue ?? "",
            name: p.name ?? p.identity?.stringValue ?? "",
            role: ParticipantRole(fromString: meta["role"]),
            isMuted: !p.isMicrophoneEnabled(),
            isCameraOff: !p.isCameraEnabled(),
            isSpeaking: p.isSpeaking,
            connectionQuality: Self.mapQuality(p.connectionQuality),
            videoTrack: p.firstCameraVideoTrack,
            audioTrack: p.firstAudioTrack,
            screenShareTrack: p.firstScreenShareVideoTrack,
            isScreenSharing: p.firstScreenShareVideoTrack != nil,
            isLocal: isLocal,
            metadata: meta
        )
    }

    private static func mapQuality(_ q: LiveKit.ConnectionQuality) -> ConnectionQuality {
        switch q {
        case .excellent: return .excellent
        case .good: return .good
        case .poor: return .poor
        case .lost: return .lost
        default: return .unknown
        }
    }

    private static func classifyConnectError(_ error: Error) -> CallErrorCode {
        let m = "\(error)".lowercased()
        if m.contains("auth") || m.contains("401") || m.contains("token") { return .authFailed }
        if m.contains("not found") || m.contains("404") { return .roomNotFound }
        return .networkError
    }

    private static func parseTokenExpiry(_ token: String) -> Date {
        let parts = token.split(separator: ".")
        guard parts.count >= 2 else { return Date().addingTimeInterval(6 * 3600) }
        var b64 = String(parts[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while b64.count % 4 != 0 { b64 += "=" }
        guard let data = Data(base64Encoded: b64),
              let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let exp = (obj["exp"] as? NSNumber)?.doubleValue
        else { return Date().addingTimeInterval(6 * 3600) }
        return Date(timeIntervalSince1970: exp)
    }
}

// MARK: - RoomDelegate
// ⚠️ VERIFY-ON-MAC: signatures researched against client-sdk-swift 2.x. Delegate methods are
// optional, so a wrong signature compiles but never fires. Confirm each against the pinned SDK.
// Delegates may be called off the main thread → hop to the main actor before touching state.

extension Call: RoomDelegate {
    public nonisolated func room(
        _ room: LKRoom,
        didUpdateConnectionState connectionState: LiveKit.ConnectionState,
        from oldState: LiveKit.ConnectionState
    ) {
        Task { @MainActor in
            switch connectionState {
            case .connected:
                self.setState(.connected)
                self.options.onAnalyticsEvent?("connect_success", [:])
                self.syncFromRoom()
            case .reconnecting:
                self.setState(.reconnecting)
                self.events.send(.reconnecting(attempt: 1))
            case .disconnected:
                self.setState(.disconnected)
                self.events.send(.disconnected(.unknown)) // VERIFY-ON-MAC: map disconnect reason
            default:
                break
            }
        }
    }

    public nonisolated func room(_ room: LKRoom, participantDidConnect participant: RemoteParticipant) {
        Task { @MainActor in self.syncFromRoom(emitUpdates: false); self.events.send(.participantJoined(self.map(participant))) }
    }

    public nonisolated func room(_ room: LKRoom, participantDidDisconnect participant: RemoteParticipant) {
        Task { @MainActor in
            let id = participant.identity?.stringValue ?? ""
            let existing = self.participants[id]
            self.syncFromRoom()
            if let existing { self.events.send(.participantLeft(existing)) }
        }
    }

    public nonisolated func room(_ room: LKRoom, didUpdateSpeakingParticipants participants: [LKParticipant]) {
        Task { @MainActor in
            let speakers = participants.map { self.map($0) }
            self.activeSpeakers = speakers
            self.events.send(.activeSpeakerChanged(speakers))
            self.syncFromRoom(emitUpdates: true)
        }
    }

    // NOTE: RoomDelegate has separate Local/Remote track-publish variants. Screen-share
    // detection targets REMOTE participants, so we implement the RemoteParticipant/
    // RemoteTrackPublication overloads (the Local* overloads would compile but never fire
    // for remote shares — verified against the 2.14.1 RoomDelegate protocol).
    public nonisolated func room(_ room: LKRoom, participant: RemoteParticipant, didPublishTrack publication: RemoteTrackPublication) {
        Task { @MainActor in
            self.syncFromRoom(emitUpdates: true)
            if publication.source == .screenShareVideo { self.events.send(.screenShareStarted(self.map(participant))) }
        }
    }

    public nonisolated func room(_ room: LKRoom, participant: RemoteParticipant, didUnpublishTrack publication: RemoteTrackPublication) {
        Task { @MainActor in
            self.syncFromRoom(emitUpdates: true)
            if publication.source == .screenShareVideo {
                self.events.send(.screenShareStopped(participantId: participant.identity?.stringValue ?? ""))
            }
        }
    }

    public nonisolated func room(_ room: LKRoom, participant: RemoteParticipant?, didReceiveData data: Data, forTopic topic: String, encryptionType: EncryptionType) {
        Task { @MainActor in self.handleData(data, from: participant) }
    }
}
