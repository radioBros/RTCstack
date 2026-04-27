<script setup lang="ts">
import { watch, nextTick, ref } from 'vue'
import { useTranscription, useSpeakingIndicators } from '../composables.js'

const props = withDefaults(defineProps<{
  maxItems?: number
  showSpeakerName?: boolean
}>(), {
  maxItems: 100,
  showSpeakerName: true,
})

const transcripts = useTranscription()
const speaking = useSpeakingIndicators()
const bottomRef = ref<HTMLElement | null>(null)

watch([transcripts, speaking], async () => {
  await nextTick()
  bottomRef.value?.scrollIntoView({ behavior: 'smooth' })
}, { deep: true })
</script>

<template>
  <div class="rtc-transcript-panel" role="log" aria-live="polite" aria-label="Live transcript">
    <div
      v-for="(seg, i) in props.maxItems > 0 ? transcripts.slice(-props.maxItems) : transcripts"
      :key="i"
      class="rtc-transcript-panel__entry"
    >
      <span v-if="props.showSpeakerName" class="rtc-transcript-panel__speaker">{{ seg.speaker }}</span>
      <span class="rtc-transcript-panel__text">{{ seg.text }}</span>
    </div>
    <div
      v-for="[id, name] in speaking"
      :key="`interim-${id}`"
      class="rtc-transcript-panel__entry rtc-transcript-panel__entry--interim"
    >
      <span v-if="showSpeakerName" class="rtc-transcript-panel__speaker">{{ name }}</span>
      <span class="rtc-transcript-panel__text rtc-transcript-panel__text--interim" aria-label="Speaking">···</span>
    </div>
    <div ref="bottomRef" />
  </div>
</template>
