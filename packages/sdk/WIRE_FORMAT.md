# RTCstack Data-Channel Wire Format

**This is the authoritative cross-platform contract.** The web SDK (`packages/sdk/src/call.ts`),
the Android SDK (`mobile/android/.../WireFormat.kt`), and the iOS SDK
(`mobile/ios/.../WireFormat.swift`) all implement exactly these payloads so that web and native
clients can interoperate in a shared LiveKit room.

> **History:** an earlier draft spec (`development/sdk/api.md`) documented a different shape
> (`{type:"chat_message", from, fromName, to, timestamp}` and `trackPublished`/`screenShareChanged`
> events). That doc was never shipped and no longer exists in the tree. The **shipped** `call.ts`
> format below is the single source of truth. This file replaces that draft.

## Transport

Messages are sent over the LiveKit data channel via `localParticipant.publishData(...)`. The
payload is **UTF-8 JSON**. The receiver does `JSON.parse(text)` and switches on `data.type`.
The contract is **semantic JSON compatibility**, not byte-identity: key order and string escaping
are irrelevant. Unknown `type` values MUST be ignored (forward-compat).

## Payloads

| Type | JSON | Notes |
|---|---|---|
| chat | `{"type":"chat","text":"…","id":"…"}` | `id` is a per-sender counter string |
| reaction | `{"type":"reaction","emoji":"…"}` | |
| speaking | `{"type":"speaking","speakerId":"…","speaker":"…"}` | both fields optional; fall back to the LiveKit sender identity |
| transcript | `{"type":"transcript","text":"…","speakerId":"…","speaker":"…","startMs":1234}` | `startMs` optional |

### Receive-side fallbacks (match `call.ts`)

- `speakerId` → `data.speakerId ?? sender.identity ?? "unknown"`
- `speaker`   → `data.speaker ?? sender.name ?? sender.identity ?? "unknown"`
- `chat.id`   → if absent, the receiver assigns its own local counter id.
- Malformed (non-JSON) payloads are dropped silently.

## No local echo

LiveKit does **not** loop data back to the sender. Sending a chat or reaction does **not** fire a
local `messageReceived`/`reactionReceived` event. The UI layer must render the local user's own
outgoing messages/reactions itself. (The bundled UI kits — `RTCstackUI` / `ui-compose` — do this.)

## Versioning

This format is a cross-platform contract. Any change here is a **breaking** change and must bump
the MAJOR version of `@rtcstack/sdk` and the native SDKs **together**.

## Conformance tests

- Web: `packages/sdk` tests around `call.ts`.
- Android: `mobile/android/sdk/src/test/kotlin/com/rtcstack/sdk/WireFormatTest.kt` (7 tests).
- iOS: `mobile/ios/Tests/RTCstackKitTests/WireFormatTests.swift` (6 tests).
