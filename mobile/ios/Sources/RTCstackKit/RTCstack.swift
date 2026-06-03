import Foundation

/// Entry point for the RTCstack iOS SDK. Mirrors `createCall` from `@rtcstack/sdk`.
///
/// ```swift
/// let call = RTCstack.createCall(.init(token: jwt, url: wssURL))
/// try await call.connect()
/// ```
///
/// The SDK only ever holds a LiveKit JWT + WSS URL — never the RTCstack API key/secret.
/// Mint tokens on your backend (POST /v1/token) and pass them in.
public enum RTCstack {
    @MainActor
    public static func createCall(_ options: CallOptions) -> Call {
        Call(options: options)
    }
}
