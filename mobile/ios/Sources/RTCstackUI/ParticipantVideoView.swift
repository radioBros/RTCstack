import RTCstackKit
import SwiftUI
import LiveKit

/// Renders one participant's video tile, mirroring `ParticipantVideo.tsx`: accent "speaking"
/// ring, name label, camera-off placeholder. Video is drawn by LiveKit's `SwiftUIVideoView`.
public struct ParticipantVideoView: View {
    @Environment(\.rtcColors) private var colors
    public let participant: RTCstackKit.Participant

    public init(participant: RTCstackKit.Participant) {
        self.participant = participant
    }

    public var body: some View {
        ZStack(alignment: .bottomLeading) {
            if let track = participant.videoTrack, !participant.isCameraOff {
                // VERIFY-ON-MAC: confirm SwiftUIVideoView initializer for the pinned SDK version.
                SwiftUIVideoView(track)
            } else {
                colors.surface2
                Text(String(participant.name.prefix(1)).uppercased())
                    .foregroundColor(colors.textMuted)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            Text(participant.isMuted ? "🔇 \(participant.name)" : participant.name)
                .lineLimit(1)
                .font(.caption)
                .foregroundColor(colors.captionText)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(colors.captionBg)
                .clipShape(RoundedRectangle(cornerRadius: RTCstackRadius.sm))
                .padding(6)
        }
        .clipShape(RoundedRectangle(cornerRadius: RTCstackRadius.md))
        .overlay(
            RoundedRectangle(cornerRadius: RTCstackRadius.md)
                .stroke(participant.isSpeaking ? colors.speakingRing : colors.border,
                        lineWidth: participant.isSpeaking ? 3 : 1)
        )
    }
}
