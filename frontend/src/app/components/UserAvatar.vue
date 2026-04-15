<template>
  <div class="user-avatar" :style="avatarStyle" aria-hidden="true">
    <img v-if="avatarUrl" :src="avatarUrl" alt="" />
    <span v-else>{{ fallbackText }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    avatarUrl?: string | null
    nickname?: string | null
    size?: number
  }>(),
  {
    avatarUrl: '',
    nickname: '',
    size: 72
  }
)

const fallbackText = computed(() => {
  const raw = (props.nickname || '').trim()
  return raw ? raw.slice(0, 1) : '我'
})

const fallbackHue = computed(() => {
  const seed = [...(props.nickname || 'default')].reduce((sum, char) => sum + char.charCodeAt(0), 0)
  return seed % 360
})

const avatarStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`,
  background: props.avatarUrl
    ? '#f5efe6'
    : `linear-gradient(135deg, hsl(${fallbackHue.value} 70% 44%), hsl(${(fallbackHue.value + 46) % 360} 75% 58%))`
}))
</script>
