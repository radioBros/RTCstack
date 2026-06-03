import Foundation

/// Data-channel wire format.
///
/// CROSS-PLATFORM CONTRACT — must stay *semantically* compatible with the shipped web SDK
/// (`packages/sdk/src/call.ts`): the web client does `JSON.parse(text)` and reads
/// `data.type` / `data.text` / `data.id` / `data.emoji`. Native and web clients share rooms.
///
/// Shipped shapes (NOT development/sdk/api.md — see MAC_HANDOFF.md §1):
///   chat:       {"type":"chat","text":"...","id":"..."}
///   reaction:   {"type":"reaction","emoji":"..."}
///   speaking:   {"type":"speaking","speakerId":"...","speaker":"..."}
///   transcript: {"type":"transcript","text":"...","speakerId":"...","speaker":"...","startMs":1234}
/// Unknown `type` values are ignored (forward-compat).
enum WireFormat {

    enum Inbound {
        case chat(text: String, id: String?)
        case reaction(emoji: String)
        case speaking(speakerId: String?, speaker: String?)
        case transcript(text: String, speakerId: String?, speaker: String?, startMs: Int64?)
        case ignored
    }

    static func encodeChat(text: String, id: String) -> Data {
        encode(["type": "chat", "text": text, "id": id])
    }

    static func encodeReaction(emoji: String) -> Data {
        encode(["type": "reaction", "emoji": emoji])
    }

    private static func encode(_ object: [String: Any]) -> Data {
        // sortedKeys keeps output deterministic for tests; key order is irrelevant to interop.
        (try? JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])) ?? Data()
    }

    /// Decode an inbound payload. Returns nil on malformed (non-JSON) data — caller ignores.
    static func decode(_ data: Data) -> Inbound? {
        guard let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        switch obj["type"] as? String {
        case "chat":
            guard let text = obj["text"] as? String else { return .ignored }
            return .chat(text: text, id: obj["id"] as? String)
        case "reaction":
            guard let emoji = obj["emoji"] as? String else { return .ignored }
            return .reaction(emoji: emoji)
        case "speaking":
            return .speaking(speakerId: obj["speakerId"] as? String, speaker: obj["speaker"] as? String)
        case "transcript":
            guard let text = obj["text"] as? String else { return .ignored }
            let startMs = (obj["startMs"] as? NSNumber)?.int64Value
            return .transcript(
                text: text,
                speakerId: obj["speakerId"] as? String,
                speaker: obj["speaker"] as? String,
                startMs: startMs
            )
        default:
            return .ignored
        }
    }
}

/// Parse a LiveKit participant metadata JSON string into a flat [String: String] map.
func parseMetadata(_ raw: String?) -> [String: String] {
    guard let raw, !raw.isEmpty,
          let data = raw.data(using: .utf8),
          let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
    else { return [:] }
    var result: [String: String] = [:]
    for (k, v) in obj { result[k] = String(describing: v) }
    return result
}
