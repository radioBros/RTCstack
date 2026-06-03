#if os(iOS)
import AVFoundation
import CallKit
import Foundation

/// Bridges CallKit (system call UI: lock-screen answer/decline, in-call controls, call history)
/// to an RTCstack ``Call``.
///
/// Lifecycle:
///  - Incoming (from a VoIP push): call ``reportIncomingCall(uuid:handle:callerName:completion:)``
///    SYNCHRONOUSLY from the PushKit handler (see ``VoIPPushManager``) — iOS terminates the app
///    if a VoIP push does not report a call before its handler returns.
///  - Outgoing: ``startOutgoingCall(uuid:handle:)``.
///  - On answer/end, the adapter invokes ``onAnswer`` / ``onEnd`` so your app connects/disconnects
///    the underlying ``Call``.
///
/// Audio: CallKit activates the `AVAudioSession` via ``provider(_:didActivate:)`` — your app should
/// start publishing/subscribing media there, and must NOT activate the session itself.
public final class CallKitAdapter: NSObject {

    private let provider: CXProvider
    private let callController = CXCallController()

    /// Invoked when the user answers (incoming) — connect the Call here, then call the completion.
    public var onAnswer: ((_ uuid: UUID, _ completion: @escaping (Bool) -> Void) -> Void)?
    /// Invoked when the user ends/declines — disconnect the Call here.
    public var onEnd: ((_ uuid: UUID) -> Void)?
    /// Invoked when the user toggles mute from the system UI.
    public var onSetMuted: ((_ uuid: UUID, _ muted: Bool) -> Void)?
    /// Invoked when CallKit activates the audio session — start media here.
    public var onAudioSessionActivated: (() -> Void)?

    public init(localizedName: String = "RTCstack") {
        let config = CXProviderConfiguration()
        config.supportsVideo = true
        config.maximumCallsPerCallGroup = 1
        config.supportedHandleTypes = [.generic]
        provider = CXProvider(configuration: config)
        super.init()
        provider.setDelegate(self, queue: nil)
    }

    /// MUST be called synchronously from the PushKit `didReceiveIncomingPushWith` handler.
    public func reportIncomingCall(
        uuid: UUID,
        handle: String,
        callerName: String,
        hasVideo: Bool = true,
        completion: @escaping (Error?) -> Void
    ) {
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .generic, value: handle)
        update.localizedCallerName = callerName
        update.hasVideo = hasVideo
        provider.reportNewIncomingCall(with: uuid, update: update, completion: completion)
    }

    public func startOutgoingCall(uuid: UUID, handle: String) {
        let action = CXStartCallAction(call: uuid, handle: CXHandle(type: .generic, value: handle))
        callController.request(CXTransaction(action: action)) { _ in }
    }

    /// Tell CallKit the outgoing call connected (drives the call timer / UI state).
    public func reportConnected(uuid: UUID) {
        provider.reportOutgoingCall(with: uuid, connectedAt: Date())
    }

    public func endCall(uuid: UUID) {
        let action = CXEndCallAction(call: uuid)
        callController.request(CXTransaction(action: action)) { _ in }
    }
}

extension CallKitAdapter: CXProviderDelegate {
    public func providerDidReset(_ provider: CXProvider) {}

    public func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        if let onAnswer {
            onAnswer(action.callUUID) { ok in ok ? action.fulfill() : action.fail() }
        } else {
            action.fulfill()
        }
    }

    public func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        onEnd?(action.callUUID)
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, perform action: CXSetMutedCallAction) {
        onSetMuted?(action.callUUID, action.isMuted)
        action.fulfill()
    }

    public func provider(_ provider: CXProvider, didActivate audioSession: AVAudioSession) {
        onAudioSessionActivated?()
    }

    public func provider(_ provider: CXProvider, didDeactivate audioSession: AVAudioSession) {}
}
#endif
