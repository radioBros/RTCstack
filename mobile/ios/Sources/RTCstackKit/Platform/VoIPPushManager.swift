#if os(iOS)
import Foundation
import PushKit

/// Registers for VoIP pushes (PushKit) and wakes the app for incoming calls from a killed or
/// suspended state.
///
/// ⚠️ App Store contract: a VoIP push MUST result in a CallKit incoming-call report BEFORE the
/// `didReceiveIncomingPushWith` handler returns, or iOS will terminate the app (and may revoke
/// VoIP push privileges). This manager enforces the ordering by requiring the report to happen
/// inside ``onIncomingPush`` synchronously.
///
/// Token handling: the device push token is forwarded to YOUR backend's notification service.
/// RTCstack does not store device tokens.
public final class VoIPPushManager: NSObject {

    private let registry: PKPushRegistry

    /// Forward this token to your backend (it sends VoIP pushes via APNs).
    public var onTokenUpdated: ((_ token: Data) -> Void)?

    /// Called on the main queue when a VoIP push arrives. You MUST, within this closure,
    /// synchronously report the call to CallKit (e.g. `callKit.reportIncomingCall(...)`).
    /// Call `completion()` once the report has been made.
    public var onIncomingPush: ((_ payload: [AnyHashable: Any], _ completion: @escaping () -> Void) -> Void)?

    public override init() {
        registry = PKPushRegistry(queue: .main)
        super.init()
        registry.delegate = self
        registry.desiredPushTypes = [.voIP]
    }
}

extension VoIPPushManager: PKPushRegistryDelegate {
    public func pushRegistry(_ registry: PKPushRegistry, didUpdate pushCredentials: PKPushCredentials, for type: PKPushType) {
        guard type == .voIP else { return }
        onTokenUpdated?(pushCredentials.token)
    }

    public func pushRegistry(
        _ registry: PKPushRegistry,
        didReceiveIncomingPushWith payload: PKPushPayload,
        for type: PKPushType,
        completion: @escaping () -> Void
    ) {
        guard type == .voIP else { completion(); return }
        // The app MUST report to CallKit synchronously inside onIncomingPush, then call completion.
        if let onIncomingPush {
            onIncomingPush(payload.dictionaryPayload, completion)
        } else {
            // No handler wired → still must not violate the contract; complete immediately.
            // (A real integration MUST set onIncomingPush and report a call.)
            completion()
        }
    }
}
#endif
