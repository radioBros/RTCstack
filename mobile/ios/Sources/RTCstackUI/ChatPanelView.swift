import RTCstackKit
import SwiftUI

/// Chat panel mirroring `ChatPanel.tsx`. Renders ``Call/messages`` plus a composer.
///
/// NOTE: the SDK does NOT echo the local user's own outgoing messages (LiveKit doesn't loop data
/// back to the sender), so this view keeps a local "sent" list and merges it with received
/// messages — the responsibility called out in the SDK docs.
public struct ChatPanelView: View {
    @ObservedObject private var call: Call
    @Environment(\.rtcColors) private var colors

    private let localId: String
    private let localName: String

    @State private var draft = ""
    @State private var sent: [Message] = []
    @State private var counter = 0

    public init(call: Call, localId: String, localName: String) {
        self.call = call
        self.localId = localId
        self.localName = localName
    }

    private var allMessages: [Message] {
        (call.messages + sent).sorted { $0.timestamp < $1.timestamp }
    }

    public var body: some View {
        VStack(spacing: 8) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 6) {
                    ForEach(allMessages) { msg in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(msg.fromName).font(.subheadline.weight(.semibold)).foregroundColor(colors.accent)
                            Text(msg.text).foregroundColor(colors.text)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(8)
            }
            HStack {
                TextField("Message…", text: $draft)
                    .textFieldStyle(.roundedBorder)
                Button("Send") { send() }.disabled(draft.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding(8)
        }
        .background(colors.surface1)
    }

    private func send() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        counter += 1
        sent.append(Message(id: "local-\(counter)", from: localId, fromName: localName,
                            text: text, timestamp: Date(), to: nil))
        draft = ""
        Task { try? await call.sendMessage(text) }
    }
}
