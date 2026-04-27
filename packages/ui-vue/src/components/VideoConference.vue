<script setup lang="ts">
import { ref, provide } from 'vue'
import type { Call } from '@rtcstack/sdk'
import { CALL_KEY, useConnectionState } from '../composables.js'
import VideoGrid from './VideoGrid.vue'
import ControlBar from './ControlBar.vue'
import type { ControlBarButton } from './ControlBar.vue'
import ChatPanel from './ChatPanel.vue'
import ParticipantList from './ParticipantList.vue'
import TranscriptPanel from './TranscriptPanel.vue'

const props = withDefaults(defineProps<{
  call: Call
  class?: string
  showChat?: boolean
  showTranscript?: boolean
  showParticipants?: boolean
  controlBarButtons?: ControlBarButton[]
  participantVideoProps?: Record<string, unknown>
}>(), {
  showChat: false,
  showTranscript: false,
  showParticipants: false,
})

const emit = defineEmits<{ leave: [] }>()

provide(CALL_KEY, props.call)

const connectionState = useConnectionState()
const chatOpen = ref(props.showChat)
const transcriptOpen = ref(props.showTranscript)
const listOpen = ref(props.showParticipants)
</script>

<template>
  <div v-if="connectionState === 'disconnected'" class="rtc-conference rtc-conference--disconnected">
    <p>You have left the call.</p>
  </div>
  <div
    v-else-if="connectionState === 'connecting' || connectionState === 'idle'"
    class="rtc-conference rtc-conference--connecting"
    aria-busy="true"
  >
    <p>Connecting…</p>
  </div>
  <div v-else class="rtc-conference" :class="props.class">
    <div class="rtc-conference__main">
      <VideoGrid class="rtc-conference__grid" v-bind="props.participantVideoProps !== undefined ? { participantVideoProps: props.participantVideoProps } : {}" />
      <ControlBar
        class="rtc-conference__controls"
        v-bind="props.controlBarButtons !== undefined ? { buttons: props.controlBarButtons } : {}"
        @leave="emit('leave')"
      />
    </div>
    <ChatPanel v-if="chatOpen" class="rtc-conference__chat" />
    <ParticipantList v-if="listOpen" class="rtc-conference__participants" />
    <TranscriptPanel v-if="transcriptOpen" class="rtc-conference__transcript" />

    <div class="rtc-conference__sidebars">
      <button
        class="rtc-conference__toggle-btn"
        :class="{ 'rtc-conference__toggle-btn--active': chatOpen }"
        :aria-pressed="chatOpen"
        :aria-label="chatOpen ? 'Close chat' : 'Open chat'"
        @click="chatOpen = !chatOpen; listOpen = false"
      >💬</button>
      <button
        class="rtc-conference__toggle-btn"
        :class="{ 'rtc-conference__toggle-btn--active': listOpen }"
        :aria-pressed="listOpen"
        :aria-label="listOpen ? 'Close participant list' : 'Open participant list'"
        @click="listOpen = !listOpen; chatOpen = false"
      >👥</button>
      <button
        class="rtc-conference__toggle-btn"
        :class="{ 'rtc-conference__toggle-btn--active': transcriptOpen }"
        :aria-pressed="transcriptOpen"
        :aria-label="transcriptOpen ? 'Close transcript' : 'Open transcript'"
        @click="transcriptOpen = !transcriptOpen"
      >📝</button>
    </div>
  </div>
</template>
