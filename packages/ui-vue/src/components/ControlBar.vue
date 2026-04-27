<script lang="ts">
export type ControlBarButton =
  | 'mic' | 'camera' | 'screenshare'
  | 'reactions' | 'layout' | 'devices' | 'leave'

export const ALL_BUTTONS: ControlBarButton[] = ['mic', 'camera', 'screenshare', 'reactions', 'layout', 'devices', 'leave']
</script>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useCall, useLocalParticipant, useLayout } from '../composables.js'
import DeviceSelector from './DeviceSelector.vue'

const props = withDefaults(defineProps<{
  buttons?: ControlBarButton[]
}>(), {
  buttons: () => ALL_BUTTONS,
})

const emit = defineEmits<{ leave: [] }>()
const call = useCall()
const local = useLocalParticipant()
const { layout, setLayout } = useLayout()

const isMuted = computed(() => local.value?.isMuted ?? true)
const isCameraOff = computed(() => local.value?.isCameraOff ?? true)
const isScreenSharing = computed(() => local.value?.isScreenSharing ?? false)
const devicesOpen = ref(false)

const REACTIONS = ['👍', '❤️', '😂', '🎉', '👏', '🙌']

const show = (btn: ControlBarButton) => props.buttons.includes(btn)

async function toggleMic() { await call.toggleMic() }
async function toggleCamera() { await call.toggleCamera() }
async function toggleScreenShare() {
  if (isScreenSharing.value) await call.stopScreenShare()
  else await call.startScreenShare()
}
async function sendReaction(emoji: string) { await call.sendReaction(emoji) }
async function handleLeave() {
  await call.disconnect()
  emit('leave')
}
function toggleLayout() {
  setLayout(layout.value === 'grid' ? 'spotlight' : 'grid')
}
</script>

<template>
  <div class="rtc-control-bar" role="toolbar" aria-label="Call controls" style="position:relative">
    <DeviceSelector v-if="devicesOpen" class="rtc-control-bar__devices-popover" />

    <button
      v-if="show('mic')"
      class="rtc-control-bar__btn"
      :class="{ 'rtc-control-bar__btn--off': isMuted }"
      :aria-pressed="isMuted"
      :aria-label="isMuted ? 'Unmute microphone' : 'Mute microphone'"
      @click="toggleMic"
    >
      {{ isMuted ? '🎤✕' : '🎤' }}
    </button>

    <button
      v-if="show('camera')"
      class="rtc-control-bar__btn"
      :class="{ 'rtc-control-bar__btn--off': isCameraOff }"
      :aria-pressed="isCameraOff"
      :aria-label="isCameraOff ? 'Turn camera on' : 'Turn camera off'"
      @click="toggleCamera"
    >
      {{ isCameraOff ? '📷✕' : '📷' }}
    </button>

    <button
      v-if="show('screenshare')"
      class="rtc-control-bar__btn"
      :class="{ 'rtc-control-bar__btn--active': isScreenSharing }"
      :aria-pressed="isScreenSharing"
      :aria-label="isScreenSharing ? 'Stop sharing screen' : 'Share screen'"
      @click="toggleScreenShare"
    >
      {{ isScreenSharing ? '🖥✕' : '🖥' }}
    </button>

    <div v-if="show('reactions')" class="rtc-control-bar__reactions" role="group" aria-label="Send reaction">
      <button
        v-for="emoji in REACTIONS"
        :key="emoji"
        class="rtc-control-bar__btn rtc-control-bar__btn--reaction"
        :aria-label="`React with ${emoji}`"
        @click="sendReaction(emoji)"
      >{{ emoji }}</button>
    </div>

    <button
      v-if="show('layout')"
      class="rtc-control-bar__btn"
      :title="`Switch to ${layout === 'grid' ? 'spotlight' : 'grid'} layout`"
      @click="toggleLayout"
    >
      {{ layout === 'grid' ? '⊞' : '⊟' }}
    </button>

    <button
      v-if="show('devices')"
      class="rtc-control-bar__btn"
      :class="{ 'rtc-control-bar__btn--active': devicesOpen }"
      :aria-pressed="devicesOpen"
      aria-label="Device settings"
      title="Device settings"
      @click="devicesOpen = !devicesOpen"
    >⚙</button>

    <button
      v-if="show('leave')"
      class="rtc-control-bar__btn rtc-control-bar__btn--danger"
      aria-label="Leave call"
      @click="handleLeave"
    >
      Leave
    </button>
  </div>
</template>
