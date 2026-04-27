<script setup lang="ts">
import { useParticipants, useLocalParticipant } from '../composables.js'
import { computed } from 'vue'

const props = withDefaults(defineProps<{ class?: string }>(), {})

const remote = useParticipants()
const local = useLocalParticipant()
const all = computed(() => (local.value ? [local.value, ...remote.value] : remote.value))
</script>

<template>
  <div class="rtc-participant-list" :class="props?.class" role="list" aria-label="Participants">
    <div
      v-for="p in all"
      :key="p.id"
      class="rtc-participant-list__item"
      role="listitem"
    >
      <span class="rtc-participant-list__avatar" aria-hidden="true">
        {{ p.name.charAt(0).toUpperCase() }}
      </span>
      <span class="rtc-participant-list__name">
        {{ p.name }}
        <span v-if="p.isLocal" class="rtc-participant-list__you">(you)</span>
      </span>
      <span v-if="p.role !== 'participant'" class="rtc-participant-list__role">{{ p.role }}</span>
      <span v-if="p.isSpeaking" class="rtc-participant-list__icon" aria-label="Speaking">🎙</span>
      <span v-if="p.isMuted" class="rtc-participant-list__icon" aria-label="Muted">🔇</span>
      <span v-if="p.isCameraOff" class="rtc-participant-list__icon" aria-label="Camera off">📷✕</span>
    </div>
  </div>
</template>
