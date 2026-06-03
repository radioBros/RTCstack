# @rtcstack/sdk

Framework-agnostic TypeScript SDK for RTCstack — a self-hosted WebRTC conferencing platform built on LiveKit.

## Install

```bash
npm install @rtcstack/sdk livekit-client
```

## Quick start

```typescript
import { createCall } from '@rtcstack/sdk'

const call = createCall({
  url: 'wss://your-livekit-server.com',
  token: await fetchToken(),         // JWT from your API
  roomName: 'my-room',               // needed for recording/transcription control
  apiUrl: 'https://api.yourapp.com', // optional, for recording/transcription
})

call.on('participantJoined', (p) => console.log(p.name, 'joined'))
call.on('transcriptReceived', (seg) => console.log(seg.speaker, ':', seg.text))

await call.connect()
```

## Constructor options

```typescript
interface CallOptions {
  url: string                           // LiveKit WebSocket URL
  token: string                         // LiveKit JWT access token
  tokenRefresher?: () => Promise<string>// called before expiry to renew token
  roomName?: string                     // room name (required for API proxy methods)
  apiUrl?: string                       // RTCstack API base URL (required for API proxy methods)
  onAnalyticsEvent?: (event: string, data: Record<string, unknown>) => void
}
```

## Methods

### Connection

| Method | Description |
|--------|-------------|
| `connect()` | Connect to the LiveKit room |
| `disconnect()` | Leave the room |

### Media controls

| Method | Description |
|--------|-------------|
| `toggleMic()` | Toggle microphone on/off |
| `setMicEnabled(enabled)` | Set microphone to a specific state |
| `toggleCamera()` | Toggle camera on/off |
| `setCameraEnabled(enabled)` | Set camera to a specific state |
| `startScreenShare()` | Begin screen sharing |
| `stopScreenShare()` | Stop screen sharing |
| `isScreenSharing()` | Returns `true` if currently sharing screen |
| `switchDevice(kind, deviceId)` | Switch active audio/video device |

### Chat & reactions

| Method | Description |
|--------|-------------|
| `sendMessage(text, options?)` | Send a chat message; `options.to` limits recipients |
| `sendReaction(emoji)` | Broadcast an emoji reaction |

### Layout

| Method | Description |
|--------|-------------|
| `setLayout(layout)` | Set layout: `'grid' \| 'speaker' \| 'spotlight'` |
| `pin(participantId \| null)` | Pin a participant (null to unpin) |

### Recording & transcription (requires `apiUrl` + `roomName`)

| Method | Description |
|--------|-------------|
| `startRecording()` | Start egress recording for this room |
| `stopRecording()` | Stop recording |
| `startTranscription()` | Start live STT transcription agent |
| `stopTranscription()` | Stop transcription |

## Getters (read-only state)

| Getter | Type | Description |
|--------|------|-------------|
| `connectionState` | `ConnectionState` | `'idle' \| 'connecting' \| 'connected' \| 'reconnecting' \| 'disconnected'` |
| `participants` | `Map<string, Participant>` | All remote participants |
| `localParticipant` | `Participant \| null` | Local participant |
| `activeSpeakers` | `Participant[]` | Currently speaking participants |
| `messages` | `Message[]` | Chat message history (last 500) |
| `devices` | `DeviceList` | Available audio/video devices |
| `layout` | `Layout` | Current layout mode |
| `pinnedParticipant` | `string \| null` | Pinned participant ID |
| `tokenExpiresAt` | `Date` | Token expiry time |
| `livekitUrl` | `string` | LiveKit server URL |

## Events

```typescript
call.on('connectionStateChanged', (state: ConnectionState) => {})
call.on('participantJoined', (participant: Participant) => {})
call.on('participantLeft', (participant: Participant) => {})
call.on('participantUpdated', (participant: Participant) => {})
call.on('activeSpeakerChanged', (speakers: Participant[]) => {})
call.on('screenShareStarted', (participant: Participant) => {})
call.on('screenShareStopped', (participantId: string) => {})
call.on('recordingStarted', () => {})
call.on('recordingStopped', () => {})
call.on('messageReceived', (message: Message) => {})
call.on('reactionReceived', (from: string, emoji: string) => {})
call.on('transcriptReceived', (segment: TranscriptSegment) => {})
call.on('speakingStarted', (speakerId: string, speakerName: string) => {})
call.on('speakingStopped', (speakerId: string) => {})
call.on('reconnecting', (attempt: number) => {})
call.on('reconnected', () => {})
call.on('disconnected', (reason?: string) => {})
call.on('tokenExpired', () => {})
call.on('audioPlaybackBlocked', () => {})
call.on('devicesChanged', (devices: DeviceList) => {})
call.on('error', (error: Error) => {})
```

## TypeScript types

```typescript
type ConnectionState = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'disconnected'
type ConnectionQuality = 'excellent' | 'good' | 'poor' | 'lost' | 'unknown'
type Layout = 'grid' | 'speaker' | 'spotlight'
type ParticipantRole = 'host' | 'moderator' | 'participant' | 'viewer'

interface Participant {
  id: string
  name: string
  role: ParticipantRole
  isMuted: boolean
  isCameraOff: boolean
  isSpeaking: boolean
  connectionQuality: ConnectionQuality
  videoTrack: MediaStreamTrack | null
  audioTrack: MediaStreamTrack | null
  screenShareTrack: MediaStreamTrack | null
  isScreenSharing: boolean
  isLocal: boolean
  metadata: Record<string, unknown>
}

interface Message {
  id: string
  from: string
  fromName: string
  text: string
  timestamp: Date
  to: string[] | null
}

interface TranscriptSegment {
  text: string
  speaker: string
  speakerId: string
  timestamp: Date
  startMs?: number
}
```

## Native mobile SDKs

iOS and Android SDKs mirror this same `Call` surface, event model, and data-channel wire format,
so native and web clients interoperate in the same room:

- **iOS** — `RTCstackKit` / `RTCstackUI` (Swift Package, wraps `client-sdk-swift`)
- **Android** — `com.rtcstack:sdk` / `com.rtcstack:ui-compose` (wraps `io.livekit:livekit-android`)

The cross-platform contract is documented in [`WIRE_FORMAT.md`](./WIRE_FORMAT.md). See
[`../../mobile/README.md`](../../mobile/README.md) for build, test, and integration details.

## Testing with MockCall

```typescript
import { MockCall } from '@rtcstack/sdk/mock'

const mock = new MockCall()
mock.simulateParticipantJoin({ id: 'alice', name: 'Alice' })
mock.simulateTranscript({ text: 'Hello world', speaker: 'Alice', speakerId: 'alice' })
```

## License

MIT
