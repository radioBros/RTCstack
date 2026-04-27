export type { Call, Participant, Message, TranscriptSegment } from '@rtcstack/sdk'

export type VanillaButton = 'mic' | 'camera' | 'screenshare' | 'reactions' | 'devices' | 'leave'

export interface ParticipantTileOptions {
  showSpeakingIndicator?: boolean
  showMuteIndicator?: boolean
  showQualityBadge?: boolean
  showName?: boolean
  objectFit?: 'cover' | 'contain'
}

export interface MountOptions {
  call: import('@rtcstack/sdk').Call
  layout?: 'grid' | 'speaker'
  showTranscript?: boolean
  buttons?: VanillaButton[]
  onLeave?: () => void
  participantTile?: ParticipantTileOptions
}

const ALL_BUTTONS: VanillaButton[] = ['mic', 'camera', 'screenshare', 'reactions', 'devices', 'leave']

const QUALITY_COLOR: Record<string, string> = {
  excellent: '#2ecc71', good: '#f39c12', poor: '#e74c3c', lost: '#e74c3c', unknown: '#666',
}
const QUALITY_BARS: Record<string, number> = {
  excellent: 3, good: 2, poor: 1, lost: 0, unknown: 0,
}
const REACTIONS_LIST = ['👍', '❤️', '😂', '🎉', '👏', '🙌']

function qualityBadgeHtml(quality: string): string {
  const bars = QUALITY_BARS[quality] ?? 0
  const color = QUALITY_COLOR[quality] ?? '#666'
  return [1, 2, 3].map((n) =>
    `<span style="display:inline-block;width:3px;height:${4 + n * 3}px;border-radius:1px;background:${n <= bars ? color : 'rgba(255,255,255,0.2)'}"></span>`
  ).join('')
}

function renderTile(
  p: import('@rtcstack/sdk').Participant,
  isScreenShare = false,
  tileOpts: ParticipantTileOptions = {}
): HTMLElement {
  const {
    showSpeakingIndicator = true,
    showMuteIndicator = true,
    showQualityBadge = true,
    showName = true,
    objectFit = 'cover',
  } = tileOpts

  const tile = document.createElement('div')
  tile.dataset['participantId'] = p.id
  tile.dataset['isScreenShare'] = String(isScreenShare)
  tile.style.cssText = 'background:#1a1a1a;border-radius:8px;overflow:hidden;aspect-ratio:16/9;position:relative;display:flex;align-items:center;justify-content:center;transition:outline 0.1s'

  const track = isScreenShare ? p.screenShareTrack : p.videoTrack

  if (track) {
    const video = document.createElement('video')
    video.autoplay = true
    video.playsInline = true
    video.muted = true
    video.srcObject = new MediaStream([track])
    video.style.cssText = `width:100%;height:100%;object-fit:${objectFit}`
    if (!isScreenShare && p.isLocal) video.style.transform = 'scaleX(-1)'
    tile.appendChild(video)
  } else if (!isScreenShare) {
    const avatar = document.createElement('div')
    avatar.style.cssText = 'width:56px;height:56px;border-radius:50%;background:#4f9cf9;display:flex;align-items:center;justify-content:center;color:#fff;font-size:22px;font-weight:600'
    avatar.textContent = p.name.charAt(0).toUpperCase()
    tile.appendChild(avatar)
  }

  if (!isScreenShare && p.audioTrack && !p.isLocal) {
    const audio = document.createElement('audio')
    audio.autoplay = true
    ;(audio as HTMLAudioElement & { playsInline: boolean }).playsInline = true
    audio.srcObject = new MediaStream([p.audioTrack])
    audio.play().catch(() => {})
    tile.appendChild(audio)
  }

  if (showName || showSpeakingIndicator || showMuteIndicator || showQualityBadge) {
    const info = document.createElement('div')
    info.style.cssText = 'position:absolute;bottom:0;left:0;right:0;padding:4px 8px;background:linear-gradient(transparent,rgba(0,0,0,0.7));display:flex;align-items:center;gap:4px'

    if (showName) {
      const name = document.createElement('span')
      name.style.cssText = 'color:#fff;font-size:12px;flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap'
      name.textContent = isScreenShare ? `${p.name}'s screen` : p.name
      info.appendChild(name)
    }

    if (!isScreenShare) {
      if (showSpeakingIndicator && p.isSpeaking) {
        const sp = document.createElement('span')
        sp.textContent = '🎙'
        sp.style.fontSize = '12px'
        info.appendChild(sp)
      }
      if (showMuteIndicator && p.isMuted) {
        const mu = document.createElement('span')
        mu.textContent = '🔇'
        mu.style.fontSize = '12px'
        info.appendChild(mu)
      }
      if (showQualityBadge) {
        const quality = document.createElement('span')
        quality.style.cssText = 'display:inline-flex;align-items:flex-end;gap:2px'
        quality.innerHTML = qualityBadgeHtml(p.connectionQuality)
        info.appendChild(quality)
      }
    }
    tile.appendChild(info)
  }

  if (!isScreenShare && p.isSpeaking && showSpeakingIndicator) {
    tile.style.outline = '3px solid #4f9cf9'
    tile.style.outlineOffset = '-1px'
  }

  if (p.isScreenSharing && !isScreenShare) {
    const badge = document.createElement('span')
    badge.style.cssText = 'position:absolute;top:6px;right:6px;font-size:12px;background:rgba(0,0,0,.6);border-radius:4px;padding:2px 4px'
    badge.textContent = '🖥'
    tile.appendChild(badge)
  }

  return tile
}

export function mountVideoConference(
  container: HTMLElement,
  options: MountOptions
): { unmount: () => void } {
  const {
    call,
    layout = 'grid',
    showTranscript = true,
    buttons = ALL_BUTTONS,
    onLeave,
    participantTile = {},
  } = options

  const show = (btn: VanillaButton) => buttons.includes(btn)

  container.innerHTML = `
    <div style="display:flex;flex-direction:column;height:100%;background:#0f0f0f;color:#f5f5f5;font-family:system-ui;position:relative;overflow:hidden">
      <div id="rtc-grid" style="flex:1;overflow:hidden;position:relative"></div>
      <div id="rtc-controls" style="display:flex;gap:6px;padding:10px 12px;background:#1a1a1a;border-top:1px solid #333;flex-wrap:wrap;align-items:center"></div>
      <div id="rtc-reactions" style="position:absolute;inset:0;pointer-events:none;overflow:hidden;z-index:20"></div>
    </div>
  `

  const root = container.querySelector<HTMLElement>('div')!
  const grid = container.querySelector<HTMLElement>('#rtc-grid')!
  const controls = container.querySelector<HTMLElement>('#rtc-controls')!
  const reactionsOverlay = container.querySelector<HTMLElement>('#rtc-reactions')!

  function makeBtn(label: string, onClick: () => void, style = ''): HTMLButtonElement {
    const btn = document.createElement('button')
    btn.innerHTML = label
    btn.style.cssText = `min-width:44px;min-height:44px;padding:0 14px;cursor:pointer;border-radius:8px;background:#252525;color:#f5f5f5;border:1px solid #333;font-size:14px;display:flex;align-items:center;gap:6px;${style}`
    btn.addEventListener('click', onClick)
    return btn
  }

  let micBtn: HTMLButtonElement | null = null
  let camBtn: HTMLButtonElement | null = null
  let ssBtn: HTMLButtonElement | null = null

  if (show('mic')) {
    micBtn = makeBtn('🎤', () => void call.toggleMic())
    controls.appendChild(micBtn)
  }

  if (show('camera')) {
    camBtn = makeBtn('📷', () => void call.toggleCamera())
    controls.appendChild(camBtn)
  }

  if (show('screenshare')) {
    ssBtn = makeBtn('🖥', () => void (call.isScreenSharing() ? call.stopScreenShare() : call.startScreenShare()))
    controls.appendChild(ssBtn)
  }

  if (show('reactions')) {
    const reactionsWrap = document.createElement('div')
    reactionsWrap.style.cssText = 'display:flex;gap:2px;border:1px solid #333;border-radius:8px;padding:2px;background:#252525'
    REACTIONS_LIST.forEach((emoji) => {
      const btn = document.createElement('button')
      btn.textContent = emoji
      btn.style.cssText = 'min-width:36px;min-height:36px;padding:0 4px;cursor:pointer;border-radius:6px;background:transparent;border:none;font-size:16px'
      btn.addEventListener('click', () => void call.sendReaction(emoji))
      reactionsWrap.appendChild(btn)
    })
    controls.appendChild(reactionsWrap)
  }

  let devicesPanel: HTMLElement | null = null
  let devBtn: HTMLButtonElement | null = null

  if (show('devices')) {
    function buildDevicesPanel(): HTMLElement {
      const panel = document.createElement('div')
      panel.style.cssText = 'position:absolute;bottom:calc(100% + 8px);left:50%;transform:translateX(-50%);background:#1a1a1a;border:1px solid #333;border-radius:12px;padding:14px;display:flex;flex-direction:column;gap:10px;min-width:260px;box-shadow:0 2px 16px rgba(0,0,0,.6);z-index:50'

      const title = document.createElement('div')
      title.textContent = 'Device Settings'
      title.style.cssText = 'font-size:11px;font-weight:600;color:#888;text-transform:uppercase;letter-spacing:.06em;padding-bottom:6px;border-bottom:1px solid #2a2a2a'
      panel.appendChild(title)

      const devicesInfo = call.devices
      const groups: Array<{ label: string; icon: string; kind: 'audioinput' | 'audiooutput' | 'videoinput'; list: MediaDeviceInfo[] }> = [
        { label: 'Microphone', icon: '🎤', kind: 'audioinput', list: devicesInfo.audioinput },
        { label: 'Speaker',    icon: '🔊', kind: 'audiooutput', list: devicesInfo.audiooutput },
        { label: 'Camera',     icon: '📷', kind: 'videoinput',  list: devicesInfo.videoinput },
      ]

      groups.forEach(({ label, icon, kind, list }) => {
        if (list.length === 0) return
        const group = document.createElement('div')
        group.style.cssText = 'display:flex;flex-direction:column;gap:4px'
        const lbl = document.createElement('label')
        lbl.style.cssText = 'font-size:11px;color:#888;display:flex;align-items:center;gap:5px'
        lbl.textContent = `${icon} ${label}`
        group.appendChild(lbl)
        const sel = document.createElement('select')
        sel.style.cssText = 'width:100%;padding:7px 10px;background:#252525;border:1px solid #333;border-radius:8px;color:#f5f5f5;font-size:13px;cursor:pointer'
        sel.setAttribute('aria-label', `Select ${label}`)
        list.forEach((d, i) => {
          const opt = document.createElement('option')
          opt.value = d.deviceId
          opt.textContent = d.label || `${label} ${i + 1}`
          sel.appendChild(opt)
        })
        sel.addEventListener('change', () => void call.switchDevice(kind, sel.value))
        group.appendChild(sel)
        panel.appendChild(group)
      })
      return panel
    }

    devBtn = makeBtn('⚙', () => {
      if (devicesPanel) {
        devicesPanel.remove()
        devicesPanel = null
        devBtn!.style.background = '#252525'
      } else {
        devicesPanel = buildDevicesPanel()
        controls.style.position = 'relative'
        controls.appendChild(devicesPanel)
        devBtn!.style.background = '#4f9cf9'
      }
    })
    devBtn.title = 'Device settings'
    controls.appendChild(devBtn)
  }

  if (show('leave')) {
    const leaveBtn = makeBtn('Leave', () => {
      void call.disconnect().then(() => onLeave?.())
    }, 'background:#e74c3c;border-color:#e74c3c;color:#fff;margin-left:auto')
    controls.appendChild(leaveBtn)
  }

  function floatReaction(emoji: string) {
    const el = document.createElement('span')
    el.textContent = emoji
    el.style.cssText = `position:absolute;bottom:80px;left:${10 + Math.random() * 80}%;font-size:28px;animation:rtc-float 2.4s ease-out forwards;pointer-events:none`
    reactionsOverlay.appendChild(el)
    setTimeout(() => el.remove(), 2500)
  }

  if (!document.getElementById('rtc-vanilla-styles')) {
    const style = document.createElement('style')
    style.id = 'rtc-vanilla-styles'
    style.textContent = '@keyframes rtc-float{0%{transform:translateY(0) scale(.5);opacity:1}60%{opacity:1}100%{transform:translateY(-220px) scale(1.2);opacity:0}}'
    document.head.appendChild(style)
  }

  function syncGrid() {
    grid.innerHTML = ''
    const participants = [...call.participants.values()]
    const local = call.localParticipant
    const all = local ? [local, ...participants] : participants
    const screenSharers = all.filter((p) => p.isScreenSharing)

    if (layout === 'speaker' || screenSharers.length > 0) {
      const featured = screenSharers[0] ?? all[0]
      if (!featured) return
      grid.style.cssText = 'flex:1;display:flex;flex-direction:column;overflow:hidden'
      const main = document.createElement('div')
      main.style.cssText = 'flex:1;min-height:0;padding:4px'
      const mainTile = renderTile(featured, screenSharers.length > 0, participantTile)
      mainTile.style.width = '100%'
      mainTile.style.height = '100%'
      mainTile.style.aspectRatio = 'unset'
      main.appendChild(mainTile)
      grid.appendChild(main)

      const strip = document.createElement('div')
      strip.style.cssText = 'display:flex;gap:4px;padding:4px;height:110px;overflow-x:auto;flex-shrink:0'
      all.forEach((p) => {
        const wrap = document.createElement('div')
        wrap.style.cssText = 'flex-shrink:0;width:155px'
        const t = renderTile(p, false, participantTile)
        t.style.width = '100%'
        t.style.height = '100%'
        t.style.aspectRatio = 'unset'
        wrap.appendChild(t)
        strip.appendChild(wrap)
      })
      grid.appendChild(strip)
    } else {
      const cols = all.length <= 1 ? 1 : all.length <= 4 ? 2 : 3
      grid.style.cssText = `flex:1;display:grid;gap:4px;padding:4px;grid-template-columns:repeat(${cols},1fr)`
      all.forEach((p) => grid.appendChild(renderTile(p, false, participantTile)))
    }

    const localP = call.localParticipant
    if (localP) {
      if (micBtn) { micBtn.textContent = localP.isMuted ? '🎤✕' : '🎤'; micBtn.style.opacity = localP.isMuted ? '0.55' : '1' }
      if (camBtn) { camBtn.textContent = localP.isCameraOff ? '📷✕' : '📷'; camBtn.style.opacity = localP.isCameraOff ? '0.55' : '1' }
      if (ssBtn) { ssBtn.textContent = localP.isScreenSharing ? '🖥✕' : '🖥'; ssBtn.style.background = localP.isScreenSharing ? '#4f9cf9' : '#252525' }
    }
  }

  call.on('participantJoined', syncGrid)
  call.on('participantLeft', syncGrid)
  call.on('participantUpdated', syncGrid)
  call.on('activeSpeakerChanged', syncGrid)
  call.on('screenShareStarted', syncGrid)
  call.on('screenShareStopped', syncGrid)
  call.on('connectionStateChanged', syncGrid)
  call.on('reactionReceived', (_from, emoji) => floatReaction(emoji))

  // Transcript panel
  const TRANSCRIPT_MERGE_THRESHOLD_MS = 5000
  let transcriptPanel: HTMLElement | null = null
  const transcriptState = new Map<string, { el: HTMLElement; lastTime: number; timeout: ReturnType<typeof setTimeout>; isInterim: boolean }>()

  function ensurePanel() {
    if (!transcriptPanel && showTranscript) {
      transcriptPanel = document.createElement('div')
      transcriptPanel.style.cssText = 'position:absolute;bottom:70px;left:12px;width:280px;max-height:160px;overflow-y:auto;display:flex;flex-direction:column;gap:4px;z-index:15;pointer-events:none'
      root.appendChild(transcriptPanel)
    }
    return transcriptPanel
  }

  call.on('speakingStarted', (speakerId, speakerName) => {
    if (!showTranscript || transcriptState.has(speakerId)) return
    const panel = ensurePanel()
    if (!panel) return
    const el = document.createElement('div')
    el.style.cssText = 'background:rgba(0,0,0,0.72);border-radius:6px;padding:5px 8px;font-size:12px'
    el.innerHTML = `<span style="color:#4f9cf9;font-size:10px;display:block">${speakerName}</span><span style="color:#888;font-style:italic">···</span>`
    panel.appendChild(el)
    panel.scrollTop = panel.scrollHeight
    const timeout = setTimeout(() => { el.remove(); transcriptState.delete(speakerId) }, 8000)
    transcriptState.set(speakerId, { el, lastTime: Date.now(), timeout, isInterim: true })
  })

  call.on('transcriptReceived', (segment) => {
    if (!showTranscript) return
    const panel = ensurePanel()
    if (!panel) return
    const now = Date.now()
    const key = segment.speakerId || segment.speaker
    const existing = transcriptState.get(key)
    if (existing && existing.isInterim) {
      existing.el.querySelector('span:last-child')!.textContent = segment.text;
      (existing.el.querySelector('span:last-child') as HTMLElement).style.cssText = 'color:#f5f5f5'
      existing.lastTime = now
      existing.isInterim = false
      clearTimeout(existing.timeout)
      existing.timeout = setTimeout(() => { existing.el.remove(); transcriptState.delete(key) }, 8000)
      panel.scrollTop = panel.scrollHeight
      return
    }
    if (existing && !existing.isInterim && (now - existing.lastTime) < TRANSCRIPT_MERGE_THRESHOLD_MS) {
      existing.el.querySelector('span:last-child')!.textContent += ' ' + segment.text
      existing.lastTime = now
      clearTimeout(existing.timeout)
      existing.timeout = setTimeout(() => { existing.el.remove(); transcriptState.delete(key) }, 8000)
      panel.scrollTop = panel.scrollHeight
      return
    }
    const el = document.createElement('div')
    el.style.cssText = 'background:rgba(0,0,0,0.72);border-radius:6px;padding:5px 8px;font-size:12px'
    el.innerHTML = `<span style="color:#4f9cf9;font-size:10px;display:block">${segment.speaker}</span><span style="color:#f5f5f5">${segment.text}</span>`
    panel.appendChild(el)
    panel.scrollTop = panel.scrollHeight
    const timeout = setTimeout(() => { el.remove(); transcriptState.delete(key) }, 8000)
    transcriptState.set(key, { el, lastTime: now, timeout, isInterim: false })
  })

  syncGrid()

  return {
    unmount() {
      call.off('participantJoined', syncGrid)
      call.off('participantLeft', syncGrid)
      call.off('participantUpdated', syncGrid)
      call.off('activeSpeakerChanged', syncGrid)
      call.off('screenShareStarted', syncGrid)
      call.off('screenShareStopped', syncGrid)
      call.off('connectionStateChanged', syncGrid)
      transcriptState.forEach((s) => clearTimeout(s.timeout))
      transcriptState.clear()
      transcriptPanel = null
      container.innerHTML = ''
    },
  }
}
