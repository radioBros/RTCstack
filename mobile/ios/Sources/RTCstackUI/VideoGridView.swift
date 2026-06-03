import RTCstackKit
import SwiftUI

/// Adaptive participant grid mirroring `VideoGrid.tsx`. Column count scales with participant
/// count; in `.spotlight` layout the pinned participant fills the view.
public struct VideoGridView: View {
    public let participants: [RTCstackKit.Participant]
    public let layout: RTCstackKit.Layout
    public let pinnedId: String?

    public init(participants: [RTCstackKit.Participant], layout: RTCstackKit.Layout, pinnedId: String?) {
        self.participants = participants
        self.layout = layout
        self.pinnedId = pinnedId
    }

    public var body: some View {
        if layout == .spotlight, let pinned = pinnedParticipant {
            ParticipantVideoView(participant: pinned)
                .padding(4)
        } else {
            GeometryReader { geo in
                let columns = max(1, Int(ceil(Double(participants.count).squareRoot())))
                let grid = Array(repeating: GridItem(.flexible(), spacing: 8), count: columns)
                ScrollView {
                    LazyVGrid(columns: grid, spacing: 8) {
                        ForEach(participants) { participant in
                            ParticipantVideoView(participant: participant)
                                .aspectRatio(16.0 / 9.0, contentMode: .fit)
                        }
                    }
                    .padding(8)
                    .frame(minHeight: geo.size.height)
                }
            }
        }
    }

    private var pinnedParticipant: RTCstackKit.Participant? {
        participants.first { $0.id == pinnedId } ?? participants.first
    }
}
