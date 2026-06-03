# RTCstack

A production-ready, self-hosted WebRTC communication platform. Drop-in video conferencing, screen sharing, recording, RTMP/WHIP ingress, and live/post-call transcription — packaged as a single Docker Compose stack with a clean TypeScript SDK and three UI kits.

Built on [LiveKit](https://livekit.io/), [Caddy](https://caddyserver.com/), and [OpenAI Whisper](https://github.com/openai/whisper).

---

## Contents

- [Architecture](#architecture)
- [Features](#features)
- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Packages](#packages)
- [API Reference](#api-reference)
- [Authentication](#authentication)
- [Configuration](#configuration)
- [Docker Profiles](#docker-profiles)
- [Speech-to-Text Add-ons](#speech-to-text-add-ons)
- [Examples](#examples)
- [Development](#development)
- [Requirements](#requirements)

---

## Architecture

RTCstack separates media from signalling. There are two independent data paths:

```
                        ┌─────────────────────────────────────────────────────────┐
                        │                    Docker Stack                         │
                        │                                                         │
 Browser ──── HTTPS ──▶ │ Caddy ──▶ /v1/*  ──▶  RTCstack API  ──▶  LiveKit SDK  │
                        │                                                         │
 Browser ──── WSS ────▶ │ Caddy ──▶ /livekit ──▶  LiveKit SFU                   │
 Browser ──── UDP ────▶ │ (direct, no proxy)                                     │
                        └─────────────────────────────────────────────────────────┘
```

**Path 1 — Token handshake (HTTPS):** Your app calls the RTCstack API to get a signed LiveKit JWT. The API is a thin Fastify server; no media passes through it.

**Path 2 — Call (WebRTC):** The browser connects directly to LiveKit via WebSocket + UDP. The API is not in the media path; all audio/video is end-to-end encrypted at the SFU layer.

---

## Features

| Feature | Status |
|---|---|
| Video & audio conferencing | ✅ |
| Screen sharing | ✅ |
| Room recording (composite + per-track) | ✅ |
| RTMP / WHIP ingress (stream from OBS, etc.) | ✅ |
| Text chat over data channel | ✅ |
| Reactions | ✅ |
| Live transcription (Whisper, per-room) | ✅ optional |
| Post-call transcription (async, from recording) | ✅ optional |
| Role-based access (host, moderator, participant, viewer) | ✅ |
| Device selection (mic, camera, speaker) | ✅ |
| Connection quality badges | ✅ |
| HMAC-signed API requests + replay protection | ✅ |
| Per-key rate limiting | ✅ |
| Webhook delivery with retries | ✅ |
| TLS via Caddy (automatic or manual cert) | ✅ |
| TURN/STUN relay via coturn | ✅ |
| S3-compatible recording storage via MinIO | ✅ |
| React, Vue 3, Vanilla JS UI kits | ✅ |
| Framework-agnostic TypeScript SDK | ✅ |
| Native iOS + Android SDKs & UI kits | ✅ |

---

## Quick Start

### Prerequisites

- Docker 24+ with Compose v2
- `openssl` (for generating secrets)

### 1. Configure

```bash
cd docker
cp .env.example .env
```

Edit `.env` — at minimum replace every `REPLACE_WITH_*` value. You can generate secrets with:

```bash
openssl rand -base64 32   # for API_KEY, API_SECRET
openssl rand -base64 32   # for REDIS_PASSWORD, LIVEKIT_API_SECRET, etc.
```

### 2. Start

```bash
docker compose up -d
```

This brings up: Caddy, LiveKit, Egress, coturn, the RTCstack API, Redis, and MinIO.

### 3. Verify

```bash
curl http://localhost:3246/v1/health
```

```json
{
  "status": "ok",
  "version": "0.1.0",
  "livekit": "connected",
  "redis": "connected",
  "minio": "connected",
  "features": {
    "recording": true,
    "transcriptionLive": false,
    "transcriptionPost": false
  }
}
```

### 4. Get a token and connect

```bash
curl -X POST http://localhost:3246/v1/token \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: <API_KEY>" \
  -H "X-RTCstack-Timestamp: $(date +%s)" \
  -H "X-RTCstack-Signature: sha256=<hmac>" \
  -d '{ "roomId": "my-room", "userId": "alice", "displayName": "Alice", "role": "host" }'
```

Use the returned `token` and `url` with the SDK to connect.

---

## Project Structure

```
rtcStack/
├── apps/
│   ├── api/                    # Fastify REST API (Node.js / TypeScript)
│   ├── playground/             # Interactive dev playground
│   └── examples/
│       ├── react/              # React reference app
│       ├── vue/                # Vue 3 reference app
│       └── vanilla/            # Vanilla JS reference app
│
├── packages/
│   ├── sdk/                    # @rtcstack/sdk — framework-agnostic SDK
│   ├── ui-react/               # @rtcstack/ui-react — React component library
│   ├── ui-vue/                 # @rtcstack/ui-vue — Vue 3 component library
│   └── ui-vanilla/             # @rtcstack/ui-vanilla — Vanilla JS library
│
├── mobile/                     # Native SDKs + UI kits
│   ├── ios/                    # RTCstackKit / RTCstackUI (Swift Package)
│   └── android/                # com.rtcstack:sdk / :ui-compose (Gradle)
│
├── services/
│   ├── whisper/                # Whisper STT HTTP service (Python)
│   ├── stt-live/               # Live transcription agent (Python / LiveKit Agents)
│   └── stt-worker/             # Post-call transcription worker (Node.js)
│
├── docker/
│   ├── docker-compose.yml      # Main stack
│   ├── docker-compose.stt.yml  # Standalone STT stack (GPU machine)
│   ├── .env.example            # Environment template
│   ├── Caddyfile               # Reverse proxy config
│   ├── livekit.yaml            # LiveKit server config
│   ├── egress.yaml             # Recording config
│   └── turnserver.conf         # coturn TURN/STUN config
│
├── turbo.json                  # Turborepo task pipeline
└── pnpm-workspace.yaml
```

---

## Packages

### `@rtcstack/sdk`

Framework-agnostic TypeScript SDK. The only thing you need to build a custom UI.

```ts
import { createCall } from '@rtcstack/sdk'

const call = createCall({ token, url })

await call.connect()

call.on('participantJoined', (p) => console.log(p.name, 'joined'))
call.on('transcriptReceived', (seg) => console.log(seg.speaker, ':', seg.text))
call.on('speakingStarted', (id, name) => console.log(name, 'is speaking…'))

await call.toggleMic()
await call.startScreenShare()
await call.sendMessage('Hello!')
await call.disconnect()
```

**Key APIs:**

| API | Description |
|---|---|
| `call.connect() / disconnect()` | Join / leave the room |
| `call.participants` | `Map<id, Participant>` — all remote participants |
| `call.localParticipant` | Local participant with media state |
| `call.activeSpeakers` | Current speakers sorted by audio level |
| `call.messages` | Chat history (capped at 500) |
| `call.devices` | Enumerated camera/mic/speaker devices |
| `call.toggleMic() / toggleCamera()` | Toggle local tracks |
| `call.startScreenShare() / stopScreenShare()` | Screen sharing |
| `call.sendMessage(text)` | Broadcast text chat |
| `call.sendReaction(emoji)` | Send floating emoji reaction |
| `call.switchDevice(kind, deviceId)` | Switch active device |
| `call.setLayout(layout)` | `'grid' \| 'speaker' \| 'spotlight'` |
| `call.pin(participantId)` | Pin a participant |

**Events:**

| Event | Payload |
|---|---|
| `connectionStateChanged` | `ConnectionState` |
| `participantJoined` / `participantLeft` | `Participant` |
| `participantUpdated` | `Participant` |
| `activeSpeakerChanged` | `Participant[]` |
| `screenShareStarted` / `screenShareStopped` | `Participant` / `id` |
| `messageReceived` | `Message` |
| `reactionReceived` | `(from: string, emoji: string)` |
| `transcriptReceived` | `TranscriptSegment` |
| `speakingStarted` | `(speakerId: string, speakerName: string)` |
| `speakingStopped` | `speakerId: string` |
| `recordingStarted` / `recordingStopped` | — |
| `reconnecting` / `reconnected` | — |
| `disconnected` | `reason?: string` |
| `devicesChanged` | `DeviceList` |

**For testing without a LiveKit server:**

```ts
import { createMockCall } from '@rtcstack/sdk/mock'
const call = createMockCall()
```

---

### `@rtcstack/ui-react`

React 18 component library. Batteries included — full conference room or individual composable pieces.

```tsx
import { CallProvider, VideoConference } from '@rtcstack/ui-react'
import '@rtcstack/ui-react/dist/styles.css'

function App() {
  return (
    <CallProvider call={call}>
      <VideoConference />
    </CallProvider>
  )
}
```

**Hooks:**

```ts
const participants   = useParticipants()
const local          = useLocalParticipant()
const speakers       = useActiveSpeakers()
const messages       = useMessages()
const transcripts    = useTranscription()     // TranscriptSegment[]
const speaking       = useSpeakingIndicators() // Map<speakerId, speakerName>
const { toggleMic }  = useMediaControls()
```

---

### `@rtcstack/ui-vue`

Vue 3 component library with Composition API composables.

```vue
<script setup>
import { VideoConference } from '@rtcstack/ui-vue'
import '@rtcstack/ui-vue/dist/styles.css'
</script>

<template>
  <VideoConference />
</template>
```

**Composables** mirror the React hooks: `useParticipants()`, `useLocalParticipant()`, `useTranscription()`, `useSpeakingIndicators()`, `useMediaControls()`, etc.

---

### `@rtcstack/ui-vanilla`

No-framework JavaScript library. Mount a full conference room into any `HTMLElement`.

```js
import { mountVideoConference } from '@rtcstack/ui-vanilla'

const { unmount } = mountVideoConference(document.getElementById('room'), { call })

// When done:
unmount()
```

---

### Native mobile (iOS + Android)

The same `Call` surface, event model, and data-channel wire format, implemented natively so mobile
and web clients share rooms:

- **iOS** — `RTCstackKit` / `RTCstackUI` (Swift Package, wraps `client-sdk-swift`)
- **Android** — `com.rtcstack:sdk` / `com.rtcstack:ui-compose` (wraps `io.livekit:livekit-android`)

Build/test/integration details: [`mobile/README.md`](mobile/README.md). Cross-platform wire-format
contract: [`packages/sdk/WIRE_FORMAT.md`](packages/sdk/WIRE_FORMAT.md).

---

## API Reference

All endpoints live under `/v1/`. Every request (except `/v1/health`) must be authenticated — see [Authentication](#authentication).

### Health

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/v1/health` | None | Status and feature flags |

### Tokens

| Method | Path | Description |
|---|---|---|
| POST | `/v1/token` | Generate a LiveKit JWT for a user |
| POST | `/v1/token/refresh` | Refresh an expiring token |

**POST `/v1/token` body:**
```json
{
  "roomId": "string",
  "userId": "string",
  "displayName": "string",
  "role": "host | moderator | participant | viewer",
  "metadata": {}
}
```

**Response:**
```json
{
  "token": "<jwt>",
  "url": "wss://your-domain/livekit",
  "expiresAt": 1714000000
}
```

### Rooms

| Method | Path | Description |
|---|---|---|
| POST | `/v1/rooms` | Create a room |
| GET | `/v1/rooms` | List active rooms |
| GET | `/v1/rooms/:roomId` | Room details |
| DELETE | `/v1/rooms/:roomId` | Close room (disconnects all participants) |

### Participants

| Method | Path | Description |
|---|---|---|
| GET | `/v1/rooms/:roomId/participants` | List participants |
| DELETE | `/v1/rooms/:roomId/participants/:participantId` | Kick participant |
| POST | `/v1/rooms/:roomId/participants/:participantId/mute` | Server-side mic mute |

### Recording

| Method | Path | Description |
|---|---|---|
| POST | `/v1/rooms/:roomId/recording/start` | Start composite recording |
| POST | `/v1/rooms/:roomId/recording/stop` | Stop recording |
| GET | `/v1/rooms/:roomId/recording` | Recording status and egress ID |

Recordings are stored in MinIO under the `rtcstack-recordings` bucket.

### Ingress (RTMP / WHIP)

| Method | Path | Description |
|---|---|---|
| POST | `/v1/rooms/:roomId/ingress` | Create RTMP or WHIP ingress endpoint |
| GET | `/v1/rooms/:roomId/ingress` | List ingress endpoints |
| DELETE | `/v1/rooms/:roomId/ingress/:ingressId` | Stop ingress |

### Webhooks

| Method | Path | Description |
|---|---|---|
| GET | `/v1/webhooks` | List webhooks |
| POST | `/v1/webhooks` | Create webhook (url, events[], secret) |
| GET | `/v1/webhooks/:id` | Webhook details |
| PATCH | `/v1/webhooks/:id` | Update webhook |
| DELETE | `/v1/webhooks/:id` | Delete webhook |
| POST | `/v1/webhooks/:id/test` | Send a test event |

**Webhook events:** `room_started`, `room_finished`, `participant_joined`, `participant_left`, `recording_started`, `recording_finished`, `transcription_completed`.

### Transcription (optional)

> Requires `TRANSCRIPTION_LIVE_ENABLED=true` or `TRANSCRIPTION_POST_ENABLED=true` and the `stt-live` / `stt-post` Docker profile.

| Method | Path | Description |
|---|---|---|
| POST | `/v1/rooms/:roomId/transcription/start` | Start live transcription (dispatches agent) |
| POST | `/v1/rooms/:roomId/transcription/stop` | Stop live transcription |
| GET | `/v1/rooms/:roomId/transcription` | Live transcript segments |
| PATCH | `/v1/rooms/:roomId/transcription/speakers` | Update speaker display names |
| POST | `/v1/recordings/:recordingId/transcribe` | Queue post-call transcription |
| GET | `/v1/transcriptions` | List all transcripts |
| GET | `/v1/transcriptions/:id` | Full transcript document |
| DELETE | `/v1/transcriptions/:id` | Delete transcript |

---

## Authentication

Every API request (except `/v1/health`) must include three headers:

```
X-Api-Key: <API_KEY>
X-RTCstack-Timestamp: <unix-seconds>
X-RTCstack-Signature: sha256=<hmac>
```

**Signature calculation:**

```
canonical_string = METHOD + "\n" + PATH + "\n" + TIMESTAMP + "\n" + SHA256(body || "")
signature = "sha256=" + HMAC-SHA256(API_SECRET, canonical_string).hex()
```

> Note: the body hash is always computed over the raw request body **before** it is parsed. For GET/DELETE requests with no body, use the SHA-256 of an empty string.

**Protections:**
- Timing-safe comparison — resistant to timing attacks
- Replay window — requests with timestamps older than 5 minutes are rejected
- Per-key rate limiting — configurable via `RATE_LIMIT_MAX` / `RATE_LIMIT_WINDOW_MS`

**Node.js example:**

```ts
import { createHmac, createHash } from 'crypto'

function sign(method: string, path: string, body: string, apiKey: string, apiSecret: string) {
  const ts = Math.floor(Date.now() / 1000).toString()
  const bodyHash = createHash('sha256').update(body).digest('hex')
  const canonical = `${method}\n${path}\n${ts}\n${bodyHash}`
  const sig = 'sha256=' + createHmac('sha256', apiSecret).update(canonical).digest('hex')
  return {
    'X-Api-Key': apiKey,
    'X-RTCstack-Timestamp': ts,
    'X-RTCstack-Signature': sig,
  }
}
```

---

## Configuration

Copy `docker/.env.example` to `docker/.env` and fill in your values.

### Core

| Variable | Default | Description |
|---|---|---|
| `DOMAIN` | `localhost` | Public domain name |
| `CADDY_TLS_MODE` | `internal` | `internal` (self-signed, dev) or `auto` (Let's Encrypt) |
| `API_KEY` | — | Pre-shared API key (generate with `openssl rand -base64 32`) |
| `API_SECRET` | — | HMAC signing secret |
| `NODE_ENV` | `production` | `development` disables some security checks |
| `TOKEN_TTL_SECONDS` | `21600` | LiveKit JWT lifetime (6 hours) |

### LiveKit

| Variable | Description |
|---|---|
| `LIVEKIT_API_KEY` | LiveKit server API key |
| `LIVEKIT_API_SECRET` | LiveKit server API secret |
| `LIVEKIT_WSS_URL` | Public WebSocket URL for clients (e.g. `wss://yourdomain/livekit`) |
| `LIVEKIT_RTC_EXTERNAL_IP` | Server's public IP (for ICE candidates) |

### Redis

| Variable | Default | Description |
|---|---|---|
| `REDIS_PASSWORD` | — | Redis auth password |
| `REDIS_URL` | — | Full Redis URL (used by API and agents) |

### MinIO (object storage)

| Variable | Description |
|---|---|
| `MINIO_ROOT_USER` | MinIO admin username |
| `MINIO_ROOT_PASSWORD` | MinIO admin password |
| `EGRESS_S3_BUCKET` | Bucket for recordings (default: `rtcstack-recordings`) |

### CORS & Rate Limiting

| Variable | Default | Description |
|---|---|---|
| `CORS_ORIGINS` | `*` | Comma-separated allowed origins. Empty = allow all |
| `RATE_LIMIT_MAX` | `100` | Requests per window per API key |
| `RATE_LIMIT_WINDOW_MS` | `60000` | Rate limit window in milliseconds |

### Ports

All host ports are configurable. Defaults:

| Variable | Default | Service |
|---|---|---|
| `PORT_HTTP` | `3240` | Caddy HTTP |
| `PORT_HTTPS` | `3241` | Caddy HTTPS |
| `PORT_API` | `3246` | RTCstack API |
| `PORT_LIVEKIT` | `3242` | LiveKit WS |
| `PORT_REDIS` | `3247` | Redis |
| `PORT_MINIO` | `3248` | MinIO API |
| `PORT_MINIO_CONSOLE` | `3249` | MinIO Console |
| `PORT_TURN` | `3244` | coturn STUN/TURN UDP |
| `PORT_WHISPER` | `3281` | Whisper HTTP (STT only) |

---

## Docker Profiles

The main stack runs without any profile flag. Optional services are gated behind profiles.

### Base stack (no profile)

```bash
docker compose up -d
```

Starts: Caddy, LiveKit, Egress, coturn, API, Redis, MinIO.

### Live transcription

```bash
docker compose --profile stt-live up -d
```

Adds: **Whisper** (STT HTTP engine) + **stt-live-agent** (LiveKit Agents worker that joins rooms and streams audio to Whisper in real time).

Requires in `.env`:
```
TRANSCRIPTION_LIVE_ENABLED=true
```

### Post-call transcription

```bash
docker compose --profile stt-post up -d
```

Adds: **Whisper** + **stt-worker** (processes recordings after calls end; queued via Redis).

Requires in `.env`:
```
TRANSCRIPTION_POST_ENABLED=true
```

### Both

```bash
docker compose --profile stt-live --profile stt-post up -d
```

### GPU acceleration for Whisper

```bash
docker compose --profile stt-live \
  -f docker-compose.yml \
  -f docker-compose.stt.gpu.yml \
  up -d
```

Requires NVIDIA GPU + [nvidia-container-toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html).

---

## Speech-to-Text Add-ons

### Live transcription

When started, the live transcription agent:

1. Joins the LiveKit room as an invisible participant
2. Subscribes to every audio track
3. Uses RMS energy detection to identify speech boundaries
4. Flushes completed speech chunks to Whisper (default: 1.5s silence or 30s max)
5. Applies hallucination filtering and word deduplication
6. Broadcasts each segment to all room participants via LiveKit data channel
7. Stores segments in Redis (retrievable via `GET /v1/rooms/:roomId/transcription`)

The SDK emits `transcriptReceived(segment)` and `speakingStarted(id, name)` / `speakingStopped(id)` events so your UI can show real-time indicators.

**STT tuning variables:**

| Variable | Default | Description |
|---|---|---|
| `PAUSE_THRESHOLD_SECONDS` | `1.5` | Silence gap that closes a speech chunk |
| `SHORT_PAUSE_SECONDS` | `0.3` | Flush early if last chunk ended a sentence |
| `MAX_CHUNK_SECONDS` | `30.0` | Hard ceiling per Whisper request |
| `SPEECH_RMS_THRESHOLD` | `200` | PCM energy threshold for speech detection |
| `WHISPER_MAX_CONCURRENT` | `2` | Max parallel Whisper requests per room |
| `STT_LANGUAGE` | `en` | ISO 639-1 language code or `auto` |
| `WHISPER_MODEL` | `base` | `tiny` / `base` / `small` / `medium` / `large-v3` |
| `WHISPER_DEVICE` | `cpu` | `cpu` or `cuda` |
| `WHISPER_FILTER_PHRASES` | — | Comma-separated phrases to drop (Whisper hallucinations) |

> **Tuning tip:** If transcription never triggers, lower `SPEECH_RMS_THRESHOLD` first (try `100`). If Whisper produces garbled output, increase `PAUSE_THRESHOLD_SECONDS` to give it longer chunks.

### Post-call transcription

After a recording finishes, call `POST /v1/recordings/:recordingId/transcribe`. The stt-worker downloads the recording from MinIO, submits it to Whisper in chunks, merges the per-speaker segments, and saves the result back to MinIO. A `transcription_completed` webhook fires when done.

---

## Examples

Three ready-to-run reference apps are in `apps/examples/`.

### React

```bash
cd apps/examples/react
pnpm install
pnpm dev
```

### Vue 3

```bash
cd apps/examples/vue
pnpm install
pnpm dev
```

### Vanilla JS

```bash
cd apps/examples/vanilla
pnpm install
pnpm dev
```

### Native (iOS / Android)

Native reference apps and build instructions live in [`mobile/`](mobile/README.md).

All examples expect the API to be running at `http://localhost:3246`. Edit `src/config.ts` (or equivalent) to point at a remote stack.

---

## Development

### Requirements

| Tool | Version |
|---|---|
| Node.js | ≥ 20 |
| pnpm | ≥ 9 |
| Docker | ≥ 24 with Compose v2 |
| Python | ≥ 3.11 (STT services only) |

### Install

```bash
pnpm install
```

### Build all packages

```bash
pnpm build
```

### Run tests

```bash
pnpm test
```

### Type check

```bash
pnpm typecheck
```

### Local dev — full stack

```bash
cd docker
docker compose up -d          # infrastructure

cd ../apps/api
pnpm dev                      # API in watch mode on :3246
```

### Local dev — SDK + UI kits only (no Docker)

```bash
pnpm dev                      # watches all packages in parallel
```

The React and Vue UI kits have Storybook:

```bash
cd packages/ui-react
pnpm storybook                # http://localhost:6006

cd packages/ui-vue
pnpm storybook
```

### Adding a package

RTCstack uses [pnpm workspaces](https://pnpm.io/workspaces) and [Turborepo](https://turbo.build/repo). New packages go in `packages/` or `apps/`; add them to `pnpm-workspace.yaml` if not already covered by the glob.

### Versioning

Packages use [Changesets](https://github.com/changesets/changesets):

```bash
pnpm changeset         # describe your change
pnpm changeset version # bump versions
pnpm changeset publish # publish to npm
```

---

## Infrastructure Services

| Service | Image | Role |
|---|---|---|
| **Caddy** | `caddy:2.8-alpine` | TLS termination, reverse proxy, WSS forwarding |
| **LiveKit** | `livekit/livekit-server:v1.11` | WebRTC SFU — audio, video, data channels |
| **Egress** | `livekit/egress:v1.12` | Room recording engine |
| **coturn** | `coturn/coturn:4.6.2-alpine` | STUN/TURN relay for symmetric NAT traversal |
| **RTCstack API** | Custom Dockerfile | Token generation, room management REST API |
| **Redis** | `redis:7-alpine` | Rate limiting, session state, STT queues |
| **MinIO** | `minio/minio` | S3-compatible storage for recordings and transcripts |
| **Whisper** *(optional)* | Custom Dockerfile | OpenAI Whisper speech-to-text HTTP service |
| **stt-live-agent** *(optional)* | Custom Dockerfile | Live transcription agent (LiveKit Agents SDK) |
| **stt-worker** *(optional)* | Custom Dockerfile | Post-call transcription queue worker |

---

## License

MIT
