#if os(iOS)
import Foundation
import ReplayKit
import SwiftUI

/// Screen share on iOS runs in a SEPARATE process — a Broadcast Upload Extension — not the host
/// app. This is the most involved native integration. The flow:
///
///  1. Add a Broadcast Upload Extension target to your app (see `BroadcastExtension/SampleHandler.swift`
///     template) and an App Group shared between the app and the extension.
///  2. LiveKit captures the broadcast sample buffers from the extension and publishes them.
///  3. Present ``BroadcastPickerView`` (a wrapper over `RPSystemBroadcastPickerView`) so the user
///     starts the system broadcast; preferredExtension must be your extension's bundle id.
///
/// See MAC_HANDOFF.md §3 for the App Group + bundle-id wiring (a common failure point).
public struct BroadcastPickerView: UIViewRepresentable {
    /// Your Broadcast Upload Extension's bundle identifier (e.g. "com.yourapp.Broadcast").
    public let preferredExtensionBundleId: String
    public let showsMicrophoneButton: Bool

    public init(preferredExtensionBundleId: String, showsMicrophoneButton: Bool = false) {
        self.preferredExtensionBundleId = preferredExtensionBundleId
        self.showsMicrophoneButton = showsMicrophoneButton
    }

    public func makeUIView(context: Context) -> RPSystemBroadcastPickerView {
        let picker = RPSystemBroadcastPickerView(frame: .init(x: 0, y: 0, width: 60, height: 60))
        picker.preferredExtension = preferredExtensionBundleId
        picker.showsMicrophoneButton = showsMicrophoneButton
        return picker
    }

    public func updateUIView(_ uiView: RPSystemBroadcastPickerView, context: Context) {}
}
#endif
