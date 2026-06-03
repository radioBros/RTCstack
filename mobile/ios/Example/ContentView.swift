import RTCstackKit
import RTCstackUI
import SwiftUI

/// Minimal example: a join form (paste a LiveKit JWT + WSS URL from your backend's POST /v1/token)
/// → drop-in ``VideoConferenceView``. Mirrors `apps/examples/react-example`.
///
/// In a real app you mint the token from your authenticated backend, never paste it.
struct ContentView: View {
    @State private var url = "wss://"
    @State private var token = ""
    @State private var call: Call?

    var body: some View {
        if let call {
            VideoConferenceView(call: call, onLeave: { self.call = nil })
        } else {
            Form {
                Section("Join a call") {
                    TextField("WSS URL", text: $url)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("Token (JWT)", text: $token, axis: .vertical)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Button("Join") { join() }
                        .disabled(token.isEmpty)
                }
            }
        }
    }

    private func join() {
        let c = RTCstack.createCall(.init(token: token.trimmingCharacters(in: .whitespacesAndNewlines),
                                          url: url.trimmingCharacters(in: .whitespacesAndNewlines)))
        call = c
        Task {
            do {
                try? AudioSessionManager.shared.configure()
                try await c.connect()
                try await c.setMicEnabled(true)
                try await c.setCameraEnabled(true)
            } catch {
                print("connect failed: \(error)")
            }
        }
    }
}
