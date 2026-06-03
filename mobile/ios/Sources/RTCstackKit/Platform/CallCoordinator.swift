#if os(iOS)
import AVFoundation
import CallKit
import Combine
import Foundation

/// Integration glue that wires ``Call`` ↔ ``CallKitAdapter`` ↔ ``VoIPPushManager`` ↔
/// ``AudioSessionManager`` with the correct ordering and a single audio-session owner.
///
/// This is the piece the standalone Platform/ classes leave to the app. It encodes two design
/// decisions that are easy to get wrong:
///
///  1. **VoIP push → CallKit, report-before-return.** On an incoming push iOS gives you a
///     handler that MUST report a call to CallKit before it returns, or the app is killed. The
///     coordinator reports synchronously inside the PushKit callback, *then* does async work
///     (token mint, connect) on answer.
///
///  2. **CallKit owns the audio session.** ``AudioSessionManager/configure()`` sets the
///     category/mode, but activation is left to CallKit (`provider(_:didActivate:)`). The
///     coordinator starts media only after CallKit activates the session — it never calls
///     `setActive` itself. (If you do NOT use CallKit, call `AudioSessionManager.setActive(true)`
///     yourself on connect instead — but then don't also use this coordinator.)
///
/// You supply a `tokenProvider` that calls YOUR backend (`POST /v1/token`) — secrets never live
/// in the app. The push payload only needs enough to identify the room + caller.
///
/// ⚠️ VERIFY-ON-MAC: this orchestration is researched, not compiled. Confirm the LiveKit media
/// start point and that `Call` is created on the main actor as expected.
@MainActor
public final class CallCoordinator {

    public struct IncomingPush {
        public let roomId: String
        public let callerId: String
        public let callerName: String
        public init(roomId: String, callerId: String, callerName: String) {
            self.roomId = roomId
            self.callerId = callerId
            self.callerName = callerName
        }
    }

    /// Mints a `(token, url)` from your backend for a given room/caller. NEVER embed API secrets.
    public typealias TokenProvider = @Sendable (_ roomId: String, _ callerId: String) async throws -> (token: String, url: String)

    /// Parses your app-specific VoIP push payload into an ``IncomingPush``.
    public typealias PushParser = @Sendable (_ payload: [AnyHashable: Any]) -> IncomingPush?

    public let callKit: CallKitAdapter
    public let voip: VoIPPushManager
    public let audio = AudioSessionManager.shared

    /// Emits whenever the active ``Call`` changes (nil when no call). Observe to drive UI.
    @Published public private(set) var activeCall: Call?

    private let tokenProvider: TokenProvider
    private let pushParser: PushParser
    private var pendingByUUID: [UUID: IncomingPush] = [:]
    private var uuidForCall: UUID?

    public init(
        localizedName: String = "RTCstack",
        tokenProvider: @escaping TokenProvider,
        pushParser: @escaping PushParser,
        onVoIPTokenUpdated: @escaping (Data) -> Void
    ) {
        self.tokenProvider = tokenProvider
        self.pushParser = pushParser
        self.callKit = CallKitAdapter(localizedName: localizedName)
        self.voip = VoIPPushManager()

        try? audio.configure()
        wire(onVoIPTokenUpdated: onVoIPTokenUpdated)
    }

    // MARK: Outgoing

    /// Place an outgoing call: tell CallKit, mint a token, connect.
    public func startOutgoingCall(roomId: String, callerId: String, handle: String) {
        let uuid = UUID()
        uuidForCall = uuid
        callKit.startOutgoingCall(uuid: uuid, handle: handle)
        Task { await connect(roomId: roomId, callerId: callerId, uuid: uuid, outgoing: true) }
    }

    public func endActiveCall() {
        if let uuid = uuidForCall { callKit.endCall(uuid: uuid) }
    }

    // MARK: Wiring

    private func wire(onVoIPTokenUpdated: @escaping (Data) -> Void) {
        voip.onTokenUpdated = onVoIPTokenUpdated

        // (1) report-before-return: report to CallKit synchronously, defer connect to answer.
        voip.onIncomingPush = { [weak self] payload, completion in
            guard let self, let push = self.pushParser(payload) else { completion(); return }
            let uuid = UUID()
            self.pendingByUUID[uuid] = push
            self.callKit.reportIncomingCall(uuid: uuid, handle: push.callerId, callerName: push.callerName) { _ in
                completion() // MUST be called once the report is made
            }
        }

        callKit.onAnswer = { [weak self] uuid, done in
            guard let self, let push = self.pendingByUUID[uuid] else { done(false); return }
            self.uuidForCall = uuid
            Task {
                let ok = await self.connect(roomId: push.roomId, callerId: push.callerId, uuid: uuid, outgoing: false)
                done(ok)
            }
        }

        callKit.onEnd = { [weak self] _ in
            guard let self else { return }
            Task { await self.activeCall?.disconnect(); self.activeCall = nil }
        }

        callKit.onSetMuted = { [weak self] _, muted in
            Task { try? await self?.activeCall?.setMicEnabled(!muted) }
        }

        // (2) CallKit owns audio activation → start media only after it activates the session.
        callKit.onAudioSessionActivated = { [weak self] in
            Task { try? await self?.activeCall?.setMicEnabled(true) }
        }
    }

    @discardableResult
    private func connect(roomId: String, callerId: String, uuid: UUID, outgoing: Bool) async -> Bool {
        do {
            let (token, url) = try await tokenProvider(roomId, callerId)
            let call = RTCstack.createCall(.init(token: token, url: url))
            activeCall = call
            try await call.connect()
            if outgoing { callKit.reportConnected(uuid: uuid) }
            return true
        } catch {
            activeCall = nil
            return false
        }
    }
}
#endif
