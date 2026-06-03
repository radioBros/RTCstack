import SwiftUI

/// Which controls to show, mirroring `ControlBarButton` from the React kit.
public enum ControlButton: CaseIterable {
    case mic, camera, screenShare, reactions, layout, leave
}

private let reactions = ["👍", "❤️", "😂", "🎉", "👏", "🙌"]

/// Bottom control bar mirroring `ControlBar.tsx`. Pure presentational — wire the closures to a
/// `Call` (typically via ``VideoConferenceView``).
public struct ControlBarView: View {
    @Environment(\.rtcColors) private var colors

    public let micMuted: Bool
    public let cameraOff: Bool
    public let screenSharing: Bool
    public let buttons: [ControlButton]

    public var onToggleMic: () -> Void
    public var onToggleCamera: () -> Void
    public var onToggleScreenShare: () -> Void
    public var onReaction: (String) -> Void
    public var onCycleLayout: () -> Void
    public var onLeave: () -> Void

    public init(
        micMuted: Bool,
        cameraOff: Bool,
        screenSharing: Bool,
        buttons: [ControlButton] = ControlButton.allCases,
        onToggleMic: @escaping () -> Void,
        onToggleCamera: @escaping () -> Void,
        onToggleScreenShare: @escaping () -> Void,
        onReaction: @escaping (String) -> Void,
        onCycleLayout: @escaping () -> Void,
        onLeave: @escaping () -> Void
    ) {
        self.micMuted = micMuted
        self.cameraOff = cameraOff
        self.screenSharing = screenSharing
        self.buttons = buttons
        self.onToggleMic = onToggleMic
        self.onToggleCamera = onToggleCamera
        self.onToggleScreenShare = onToggleScreenShare
        self.onReaction = onReaction
        self.onCycleLayout = onCycleLayout
        self.onLeave = onLeave
    }

    public var body: some View {
        HStack(spacing: 8) {
            if buttons.contains(.mic) {
                control(micMuted ? "🎤✕" : "🎤", danger: micMuted, action: onToggleMic)
            }
            if buttons.contains(.camera) {
                control(cameraOff ? "📷✕" : "📷", danger: cameraOff, action: onToggleCamera)
            }
            if buttons.contains(.screenShare) {
                control("🖥", active: screenSharing, action: onToggleScreenShare)
            }
            if buttons.contains(.reactions) {
                ForEach(reactions, id: \.self) { emoji in
                    control(emoji) { onReaction(emoji) }
                }
            }
            if buttons.contains(.layout) {
                control("⊞", action: onCycleLayout)
            }
            if buttons.contains(.leave) {
                control("✕", danger: true, action: onLeave)
            }
        }
        .padding(8)
        .frame(maxWidth: .infinity)
        .background(colors.surface1)
    }

    @ViewBuilder
    private func control(_ label: String, active: Bool = false, danger: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .frame(width: 48, height: 48)
                .background(danger ? colors.danger : (active ? colors.accent : colors.surface2))
                .foregroundColor(danger || active ? .white : colors.text)
                .clipShape(Circle())
        }
    }
}
