import {
  Room,
  RoomEvent,
  LocalParticipant,
  RemoteParticipant,
  ConnectionQuality as LKQuality,
  type Participant as LKParticipant,
  Track,
} from 'livekit-client'
import { EventEmitter } from './events.js'
import type {
  CallOptions,
  CallEventMap,
  ConnectionState,
  Participant,
  Message,
  TranscriptSegment,
  DeviceList,
  Layout,
  ConnectionQuality,
} from './types.js'

function mapQuality(q: LKQuality): ConnectionQuality {
  switch (q) {
    case LKQuality.Excellent: return 'excellent'
    case LKQuality.Good: return 'good'
    case LKQuality.Poor: return 'poor'
    case LKQuality.Lost: return 'lost'
    default: return 'unknown'
  }
}

function mapParticipant(p: LKParticipant, isLocal = false): Participant {
  let meta: Record<string, unknown> = {}
  try { meta = p.metadata ? JSON.parse(p.metadata) : {} } catch { /* ignore */ }

  const videoTrack = p.getTrackPublication(Track.Source.Camera)?.track?.mediaStreamTrack ?? null
  const audioTrack = p.getTrackPublication(Track.Source.Microphone)?.track?.mediaStreamTrack ?? null
  const screenSharePub = p.getTrackPublication(Track.Source.ScreenShare)
  const screenShareTrack = screenSharePub?.track?.mediaStreamTrack ?? null

  return {
    id: p.identity,
    name: p.name ?? p.identity,
    role: (meta['role'] as Participant['role']) ?? 'participant',
    isMuted: !p.isMicrophoneEnabled,
    isCameraOff: !p.isCameraEnabled,
    isSpeaking: p.isSpeaking,
    connectionQuality: mapQuality(p.connectionQuality),
    videoTrack,
    audioTrack,
    screenShareTrack,
    isScreenSharing: screenSharePub?.isSubscribed !== false && screenShareTrack !== null,
    isLocal,
    metadata: meta,
  }
}

let _msgCounter = 0

export class Call extends EventEmitter<CallEventMap> {
  private room: Room
  private options: CallOptions
  private _connectionState: ConnectionState = 'idle'
  private _participants = new Map<string, Participant>()
  private _activeSpeakers: Participant[] = []
  private _messages: Message[] = []
  private _devices: DeviceList = { audioinput: [], audiooutput: [], videoinput: [] }
  private _layout: Layout = 'grid'
  private _pinnedParticipant: string | null = null
  private _tokenExpiresAt: Date = new Date(0)

  constructor(options: CallOptions) {
    super()
    this.options = options
    this.room = new Room()
    this._setupRoomListeners()
  }

  get connectionState(): ConnectionState { return this._connectionState }
  get participants(): Map<string, Participant> { return this._participants }
  get localParticipant(): Participant | null {
    const lp = this.room.localParticipant
    return lp ? mapParticipant(lp, true) : null
  }
  get activeSpeakers(): Participant[] { return this._activeSpeakers }
  get messages(): Message[] { return this._messages }
  get devices(): DeviceList { return this._devices }
  get layout(): Layout { return this._layout }
  get pinnedParticipant(): string | null { return this._pinnedParticipant }
  get tokenExpiresAt(): Date { return this._tokenExpiresAt }
  get livekitUrl(): string { return this.options.url }

  private setState(state: ConnectionState) {
    this._connectionState = state
    this.emit('connectionStateChanged', state)
  }

  private _syncParticipants(emitUpdates = false) {
    this._participants.clear()
    for (const [, p] of this.room.remoteParticipants) {
      const mapped = mapParticipant(p)
      this._participants.set(p.identity, mapped)
      if (emitUpdates) this.emit('participantUpdated', mapped)
    }
    if (emitUpdates) {
      const local = this.localParticipant
      if (local) this.emit('participantUpdated', local)
    }
  }

  private _setupRoomListeners() {
    const { room } = this

    room.on(RoomEvent.Connected, () => {
      this.setState('connected')
      this._syncParticipants()
    })

    room.on(RoomEvent.Disconnected, (reason) => {
      this.setState('disconnected')
      this.emit('disconnected', reason?.toString())
    })

    room.on(RoomEvent.Reconnecting, () => {
      this.setState('reconnecting')
      this.emit('reconnecting', 1)
    })

    room.on(RoomEvent.Reconnected, () => {
      this.setState('connected')
      this.emit('reconnected')
    })

    room.on(RoomEvent.ParticipantConnected, (p: RemoteParticipant) => {
      const participant = mapParticipant(p)
      this._participants.set(p.identity, participant)
      this.emit('participantJoined', participant)
    })

    room.on(RoomEvent.ParticipantDisconnected, (p: RemoteParticipant) => {
      const participant = this._participants.get(p.identity) ?? mapParticipant(p)
      this._participants.delete(p.identity)
      this.emit('participantLeft', participant)
    })

    room.on(RoomEvent.ActiveSpeakersChanged, (speakers: LKParticipant[]) => {
      this._activeSpeakers = speakers.map((s) => mapParticipant(s, s instanceof LocalParticipant))
      this.emit('activeSpeakerChanged', this._activeSpeakers)
      this._syncParticipants(true)
    })

    room.on(RoomEvent.TrackPublished, (pub, participant) => {
      this._syncParticipants(true)
      if (pub.source === Track.Source.ScreenShare) {
        const mapped = mapParticipant(participant)
        this.emit('screenShareStarted', mapped)
      }
    })

    room.on(RoomEvent.TrackUnpublished, (pub, participant) => {
      this._syncParticipants(true)
      if (pub.source === Track.Source.ScreenShare) {
        this.emit('screenShareStopped', participant.identity)
      }
    })

    room.on(RoomEvent.TrackSubscribed, (track, pub, participant) => {
      this._syncParticipants(true)
      if (pub.source === Track.Source.ScreenShare) {
        const mapped = mapParticipant(participant)
        this.emit('screenShareStarted', mapped)
      }
    })

    room.on(RoomEvent.TrackUnsubscribed, (track, pub, participant) => {
      this._syncParticipants(true)
      if (pub.source === Track.Source.ScreenShare) {
        this.emit('screenShareStopped', participant.identity)
      }
    })

    room.on(RoomEvent.TrackMuted, () => this._syncParticipants(true))
    room.on(RoomEvent.TrackUnmuted, () => this._syncParticipants(true))
    room.on(RoomEvent.ParticipantMetadataChanged, () => this._syncParticipants(true))
    room.on(RoomEvent.ConnectionQualityChanged, () => this._syncParticipants(true))

    room.on(RoomEvent.LocalTrackPublished, (pub) => {
      const local = this.localParticipant
      if (local) this.emit('participantUpdated', local)
      if (pub.source === Track.Source.ScreenShare) {
        if (local) this.emit('screenShareStarted', local)
      }
    })

    room.on(RoomEvent.LocalTrackUnpublished, (pub) => {
      const local = this.localParticipant
      if (local) this.emit('participantUpdated', local)
      if (pub.source === Track.Source.ScreenShare) {
        this.emit('screenShareStopped', this.room.localParticipant.identity)
      }
    })

    room.on(RoomEvent.RecordingStatusChanged, (recording: boolean) => {
      if (recording) this.emit('recordingStarted')
      else this.emit('recordingStopped')
    })

    room.on(RoomEvent.AudioPlaybackStatusChanged, () => {
      if (!room.canPlaybackAudio) this.emit('audioPlaybackBlocked')
    })

    room.on(RoomEvent.DataReceived, (payload: Uint8Array, participant?: RemoteParticipant) => {
      try {
        const text = new TextDecoder().decode(payload)
        const data = JSON.parse(text) as { type: string; text?: string; emoji?: string; id?: string; speakerId?: string; speaker?: string; startMs?: number }

        if (data.type === 'chat' && data.text) {
          const msg: Message = {
            id: data.id ?? String(++_msgCounter),
            from: participant?.identity ?? 'unknown',
            fromName: participant?.name ?? participant?.identity ?? 'unknown',
            text: data.text,
            timestamp: new Date(),
            to: null,
          }
          this._messages = [...this._messages.slice(-499), msg]
          this.emit('messageReceived', msg)
        } else if (data.type === 'reaction' && data.emoji) {
          this.emit('reactionReceived', participant?.identity ?? 'unknown', data.emoji)
        } else if (data.type === 'speaking') {
          const speakerId = data.speakerId ?? participant?.identity ?? 'unknown'
          const speakerName = data.speaker ?? participant?.name ?? participant?.identity ?? 'unknown'
          this.emit('speakingStarted', speakerId, speakerName)
        } else if (data.type === 'transcript' && data.text) {
          const speakerId = data.speakerId ?? participant?.identity ?? 'unknown'
          this.emit('speakingStopped', speakerId)
          const segment: TranscriptSegment = {
            text: data.text as string,
            speaker: data.speaker ?? participant?.name ?? participant?.identity ?? 'unknown',
            speakerId,
            timestamp: new Date(),
            ...(data.startMs !== undefined && { startMs: data.startMs }),
          }
          this.emit('transcriptReceived', segment)
        }
      } catch { /* ignore malformed data */ }
    })
  }

  async connect(): Promise<void> {
    this.setState('connecting')
    let token = this.options.token

    if (this.options.tokenRefresher) {
      const expires = this._parseTokenExpiry(token)
      this._tokenExpiresAt = expires
      if (Date.now() >= expires.getTime() - 60_000) {
        token = await this.options.tokenRefresher()
        this._tokenExpiresAt = this._parseTokenExpiry(token)
      }
    }

    await this.room.connect(this.options.url, token)
    await this._enumerateDevices()
  }

  async disconnect(): Promise<void> {
    await this.room.disconnect()
    this.setState('disconnected')
  }

  async toggleMic(): Promise<void> {
    await this.room.localParticipant.setMicrophoneEnabled(
      !this.room.localParticipant.isMicrophoneEnabled
    )
  }

  async setMicEnabled(enabled: boolean): Promise<void> {
    await this.room.localParticipant.setMicrophoneEnabled(enabled)
  }

  async toggleCamera(): Promise<void> {
    await this.room.localParticipant.setCameraEnabled(
      !this.room.localParticipant.isCameraEnabled
    )
  }

  async setCameraEnabled(enabled: boolean): Promise<void> {
    await this.room.localParticipant.setCameraEnabled(enabled)
  }

  async startScreenShare(): Promise<void> {
    await this.room.localParticipant.setScreenShareEnabled(true)
  }

  async stopScreenShare(): Promise<void> {
    await this.room.localParticipant.setScreenShareEnabled(false)
  }

  isScreenSharing(): boolean {
    return !!this.room.localParticipant?.getTrackPublication(Track.Source.ScreenShare)
  }

  async switchDevice(kind: 'audioinput' | 'audiooutput' | 'videoinput', deviceId: string): Promise<void> {
    await this.room.switchActiveDevice(kind, deviceId)
  }

  async sendMessage(text: string, options?: { to?: string[] }): Promise<void> {
    const data = JSON.stringify({ type: 'chat', text, id: String(++_msgCounter) })
    const payload = new TextEncoder().encode(data)
    if (options?.to?.length) {
      await this.room.localParticipant.publishData(payload, {
        reliable: true,
        destinationIdentities: options.to,
      })
    } else {
      await this.room.localParticipant.publishData(payload, { reliable: true })
    }
  }

  async sendReaction(emoji: string): Promise<void> {
    const data = JSON.stringify({ type: 'reaction', emoji })
    const payload = new TextEncoder().encode(data)
    await this.room.localParticipant.publishData(payload, { reliable: true })
  }

  setLayout(layout: Layout): void {
    this._layout = layout
  }

  pin(participantId: string | null): void {
    this._pinnedParticipant = participantId
  }

  private _requireApi(): { apiUrl: string; roomName: string } {
    const { apiUrl, roomName } = this.options
    if (!apiUrl) throw new Error('CallOptions.apiUrl is required for recording/transcription control')
    if (!roomName) throw new Error('CallOptions.roomName is required for recording/transcription control')
    return { apiUrl, roomName }
  }

  private async _apiPost(path: string): Promise<void> {
    const { apiUrl, roomName } = this._requireApi()
    const url = `${apiUrl.replace(/\/$/, '')}/rooms/${encodeURIComponent(roomName)}${path}`
    const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' } })
    if (!res.ok) throw new Error(`API request failed: ${res.status} ${res.statusText}`)
  }

  async startRecording(): Promise<void> {
    await this._apiPost('/recording/start')
  }

  async stopRecording(): Promise<void> {
    await this._apiPost('/recording/stop')
  }

  async startTranscription(): Promise<void> {
    await this._apiPost('/transcription/start')
  }

  async stopTranscription(): Promise<void> {
    await this._apiPost('/transcription/stop')
  }

  private _parseTokenExpiry(token: string): Date {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]!)) as { exp?: number }
      return payload.exp ? new Date(payload.exp * 1000) : new Date(Date.now() + 6 * 3600 * 1000)
    } catch {
      return new Date(Date.now() + 6 * 3600 * 1000)
    }
  }

  private async _enumerateDevices(): Promise<void> {
    try {
      const devices = await navigator.mediaDevices.enumerateDevices()
      this._devices = {
        audioinput: devices.filter((d) => d.kind === 'audioinput'),
        audiooutput: devices.filter((d) => d.kind === 'audiooutput'),
        videoinput: devices.filter((d) => d.kind === 'videoinput'),
      }
      this.emit('devicesChanged', this._devices)
    } catch { /* permissions not granted yet */ }
  }
}
