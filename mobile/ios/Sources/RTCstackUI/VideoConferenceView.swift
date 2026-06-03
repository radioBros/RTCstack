import RTCstackKit
import SwiftUI

/// Drop-in conference UI — the SwiftUI analogue of `VideoConference.tsx`. Observes the ``Call``
/// (an `ObservableObject`) and renders the grid + control bar, handling connecting/disconnected.
///
/// ```swift
/// VideoConferenceView(call: call, onLeave: { dismiss() })
///     .rtcstackTheme()
/// ```
///
/// Screen share: pass `onToggleScreenShare` wired to a ``BroadcastPickerView`` presentation,
/// since iOS screen capture is driven by the system broadcast picker (see MAC_HANDOFF.md §3).
public struct VideoConferenceView: View {
    @ObservedObject private var call: Call
    @Environment(\.rtcColors) private var colors

    private let onLeave: () -> Void
    private let onToggleScreenShare: (() -> Void)?

    public init(call: Call, onLeave: @escaping () -> Void, onToggleScreenShare: (() -> Void)? = nil) {
        self.call = call
        self.onLeave = onLeave
        self.onToggleScreenShare = onToggleScreenShare
    }

    public var body: some View {
        Group {
            switch call.connectionState {
            case .idle, .connecting:
                centered { ProgressView(); Text("Connecting…").foregroundColor(colors.textMuted) }
            case .disconnected:
                centered { Text("You have left the call.").foregroundColor(colors.textMuted) }
            default:
                VStack(spacing: 0) {
                    VideoGridView(participants: allParticipants, layout: call.layout, pinnedId: call.pinnedParticipant)
                        .frame(maxHeight: .infinity)
                    ControlBarView(
                        micMuted: call.localParticipant?.isMuted ?? false,
                        cameraOff: call.localParticipant?.isCameraOff ?? false,
                        screenSharing: call.localParticipant?.isScreenSharing ?? false,
                        onToggleMic: { Task { try? await call.toggleMic() } },
                        onToggleCamera: { Task { try? await call.toggleCamera() } },
                        onToggleScreenShare: { onToggleScreenShare?() },
                        onReaction: { emoji in Task { try? await call.sendReaction(emoji) } },
                        onCycleLayout: { call.setLayout(call.layout == .grid ? .spotlight : .grid) },
                        onLeave: { Task { await call.disconnect(); onLeave() } }
                    )
                }
            }
        }
        .background(colors.bg.ignoresSafeArea())
    }

    private var allParticipants: [RTCstackKit.Participant] {
        var all: [RTCstackKit.Participant] = []
        if let local = call.localParticipant { all.append(local) }
        all.append(contentsOf: call.participants.values)
        return all
    }

    @ViewBuilder
    private func centered<C: View>(@ViewBuilder _ content: () -> C) -> some View {
        VStack(spacing: 12) { content() }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
