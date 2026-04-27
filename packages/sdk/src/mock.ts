import { EventEmitter } from './events.js'
import type { CallEventMap, Participant, Message, DeviceList, Layout, ConnectionState } from './types.js'

interface MockCallOptions {
  participants?: Partial<Participant>[]
  activeSpeakers?: string[]
  connectionState?: ConnectionState
}

interface MockControls {
  addParticipant: (p: Partial<Participant>) => void
  removeParticipant: (id: string) => void
  sendMessage: (from: string, text: string) => void
  setConnectionState: (state: ConnectionState) => void
}

export class MockCall extends EventEmitter<CallEventMap> {
  private _connectionState: ConnectionState
  private _participants = new Map<string, Participant>()
  private _activeSpeakers: Participant[] = []
  private _messages: Message[] = []
  private _devices: DeviceList = {
    audioinput: [{ deviceId: 'default', kind: 'audioinput', label: 'Default Mic', groupId: '', toJSON: () => ({} as MediaDeviceInfo) }],
    audiooutput: [{ deviceId: 'default', kind: 'audiooutput', label: 'Default Speaker', groupId: '', toJSON: () => ({} as MediaDeviceInfo) }],
    videoinput: [{ deviceId: 'default', kind: 'videoinput', label: 'Default Camera', groupId: '', toJSON: () => ({} as MediaDeviceInfo) }],
  }
  private _layout: Layout = 'grid'
  private _pinnedParticipant: string | null = null
  readonly _mock: MockControls

  constructor(options: MockCallOptions = {}) {
    super()
    this._connectionState = options.connectionState ?? 'connected'

    for (const p of options.participants ?? []) {
      const participant = this._makeParticipant(p)
      this._participants.set(participant.id, participant)
    }

    this._mock = {
      addParticipant: (p) => {
        const participant = this._makeParticipant(p)
        this._participants.set(participant.id, participant)
        this.emit('participantJoined', participant)
      },
      removeParticipant: (id) => {
        const p = this._participants.get(id)
        if (p) {
          this._participants.delete(id)
          this.emit('participantLeft', p)
        }
      },
      sendMessage: (from, text) => {
        const msg: Message = { id: String(Date.now()), from, fromName: from, text, timestamp: new Date(), to: null }
        this._messages = [...this._messages.slice(-499), msg]
        this.emit('messageReceived', msg)
      },
      setConnectionState: (state) => {
        this._connectionState = state
        this.emit('connectionStateChanged', state)
      },
    }
  }

  private _makeParticipant(p: Partial<Participant>): Participant {
    return {
      id: p.id ?? `user-${Math.random().toString(36).slice(2, 7)}`,
      name: p.name ?? 'Unknown',
      role: p.role ?? 'participant',
      isMuted: p.isMuted ?? false,
      isCameraOff: p.isCameraOff ?? false,
      isSpeaking: p.isSpeaking ?? false,
      connectionQuality: p.connectionQuality ?? 'excellent',
      videoTrack: null,
      audioTrack: null,
      screenShareTrack: null,
      isScreenSharing: false,
      isLocal: p.isLocal ?? false,
      metadata: p.metadata ?? {},
    }
  }

  get connectionState(): ConnectionState { return this._connectionState }
  get participants(): Map<string, Participant> { return this._participants }
  get localParticipant(): Participant | null {
    for (const [, p] of this._participants) if (p.isLocal) return p
    return null
  }
  get activeSpeakers(): Participant[] { return this._activeSpeakers }
  get messages(): Message[] { return this._messages }
  get devices(): DeviceList { return this._devices }
  get layout(): Layout { return this._layout }
  get pinnedParticipant(): string | null { return this._pinnedParticipant }
  get tokenExpiresAt(): Date { return new Date(Date.now() + 6 * 3600 * 1000) }
  get livekitUrl(): string { return 'wss://mock.rtcstack.dev/livekit' }

  async connect(): Promise<void> {
    this._mock.setConnectionState('connected')
  }
  async disconnect(): Promise<void> {
    this._mock.setConnectionState('disconnected')
  }
  async toggleMic(): Promise<void> {
    const lp = this.localParticipant
    if (lp) { lp.isMuted = !lp.isMuted; this.emit('participantUpdated', lp) }
  }
  async toggleCamera(): Promise<void> {
    const lp = this.localParticipant
    if (lp) { lp.isCameraOff = !lp.isCameraOff; this.emit('participantUpdated', lp) }
  }
  async startScreenShare(): Promise<void> {}
  async stopScreenShare(): Promise<void> {}
  async switchDevice(_kind: string, _deviceId: string): Promise<void> {}
  async sendMessage(text: string): Promise<void> {
    const lp = this.localParticipant
    this._mock.sendMessage(lp?.id ?? 'local', text)
  }
  async sendReaction(emoji: string): Promise<void> {
    this.emit('reactionReceived', this.localParticipant?.id ?? 'local', emoji)
  }
  setLayout(layout: Layout): void { this._layout = layout }
  pin(id: string | null): void { this._pinnedParticipant = id }
}

export function createMockCall(options: MockCallOptions = {}): MockCall {
  return new MockCall(options)
}
