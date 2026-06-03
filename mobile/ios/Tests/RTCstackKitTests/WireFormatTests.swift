import Foundation
import XCTest
@testable import RTCstackKit

/// Cross-platform wire-format contract tests.
///
/// The bar is SEMANTIC compatibility with the shipped web SDK (`packages/sdk/src/call.ts`):
/// the web client does `JSON.parse(text)` and reads `data.type`/`data.text`/`data.id`/`data.emoji`.
/// Native and web clients in the same room must decode each other's payloads.
final class WireFormatTests: XCTestCase {

    private func parse(_ data: Data) -> [String: Any] {
        (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }

    func testChatEncodesKeysWebReads() {
        let obj = parse(WireFormat.encodeChat(text: "Hello everyone!", id: "42"))
        XCTAssertEqual(obj["type"] as? String, "chat")
        XCTAssertEqual(obj["text"] as? String, "Hello everyone!")
        XCTAssertEqual(obj["id"] as? String, "42")
    }

    func testReactionEncodesKeysWebReads() {
        let obj = parse(WireFormat.encodeReaction(emoji: "👍"))
        XCTAssertEqual(obj["type"] as? String, "reaction")
        XCTAssertEqual(obj["emoji"] as? String, "👍")
    }

    func testRoundTrips() {
        guard case let .chat(text, id)? = WireFormat.decode(WireFormat.encodeChat(text: "round trip", id: "99")) else {
            return XCTFail("expected chat")
        }
        XCTAssertEqual(text, "round trip")
        XCTAssertEqual(id, "99")
    }

    func testTranscriptWithStartMs() {
        let json = #"{"type":"transcript","text":"hello","speakerId":"u1","speaker":"Al","startMs":1234}"#
        guard case let .transcript(text, speakerId, _, startMs)? = WireFormat.decode(Data(json.utf8)) else {
            return XCTFail("expected transcript")
        }
        XCTAssertEqual(text, "hello")
        XCTAssertEqual(speakerId, "u1")
        XCTAssertEqual(startMs, 1234)
    }

    func testUnknownTypeIgnored() {
        guard case .ignored? = WireFormat.decode(Data(#"{"type":"future_thing"}"#.utf8)) else {
            return XCTFail("expected ignored")
        }
    }

    func testMalformedReturnsNil() {
        XCTAssertNil(WireFormat.decode(Data("not json".utf8)))
    }
}
