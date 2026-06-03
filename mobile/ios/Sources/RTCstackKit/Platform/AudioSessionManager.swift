#if os(iOS)
import AVFoundation
import Foundation

/// Manages `AVAudioSession` for a VoIP call: category/mode configuration, interruptions
/// (phone call, Siri), and route changes (headphones/Bluetooth plugged/unplugged).
///
/// IMPORTANT — CallKit coordination: when using ``CallKitAdapter``, CallKit activates and
/// deactivates the audio session for you via `provider(_:didActivate:)`. In that case call
/// ``configure()`` (to set category/mode) but DO NOT call ``setActive(_:)`` yourself — let
/// CallKit own activation. Without CallKit, call `setActive(true)` on connect.
///
/// LiveKit also has its own audio session handling; if you let LiveKit manage it, this manager
/// is for the parts LiveKit leaves to the app (e.g. forcing speaker). Pick one owner — see
/// MAC_HANDOFF.md §3.
public final class AudioSessionManager {

    public static let shared = AudioSessionManager()
    private let session = AVAudioSession.sharedInstance()
    private var observersInstalled = false

    /// Called when an interruption begins/ends or the route changes, so the app/UI can react.
    public var onInterruption: ((_ began: Bool) -> Void)?
    public var onRouteChange: (() -> Void)?

    public init() {}

    /// Configure category + mode for a video/voice call. Safe to call before connect.
    public func configure(defaultToSpeaker: Bool = true) throws {
        // NOTE: `.allowBluetooth` was renamed `.allowBluetoothHFP` in the iOS 26 SDK (same HFP
        // route). We keep `.allowBluetooth` for the iOS 15 deployment target; building against a
        // newer SDK emits a harmless rename-deprecation warning. Switch to `.allowBluetoothHFP`
        // once the deployment target moves to iOS 26+.
        try session.setCategory(
            .playAndRecord,
            mode: .videoChat,
            options: [.allowBluetooth, .allowBluetoothA2DP, defaultToSpeaker ? .defaultToSpeaker : []]
        )
        installObservers()
    }

    /// Activate/deactivate the session. SKIP this when CallKit is in use (CallKit owns activation).
    public func setActive(_ active: Bool) throws {
        try session.setActive(active, options: active ? [] : [.notifyOthersOnDeactivation])
    }

    public func overrideSpeaker(_ on: Bool) throws {
        try session.overrideOutputAudioPort(on ? .speaker : .none)
    }

    private func installObservers() {
        guard !observersInstalled else { return }
        observersInstalled = true
        let nc = NotificationCenter.default
        nc.addObserver(self, selector: #selector(handleInterruption(_:)),
                       name: AVAudioSession.interruptionNotification, object: session)
        nc.addObserver(self, selector: #selector(handleRouteChange(_:)),
                       name: AVAudioSession.routeChangeNotification, object: session)
    }

    @objc private func handleInterruption(_ note: Notification) {
        guard let info = note.userInfo,
              let raw = info[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
        onInterruption?(type == .began)
    }

    @objc private func handleRouteChange(_ note: Notification) {
        onRouteChange?()
    }

    deinit { NotificationCenter.default.removeObserver(self) }
}
#endif
