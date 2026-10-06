<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

defineProps<{ visible: boolean }>()
const audio = ref<HTMLAudioElement | null>(null)
const playing = ref(false)
const enabled = ref(true)
let pending = false
let needsGesture = false

async function play() {
  const player = audio.value
  if (!enabled.value || !player || pending || !player.paused) return
  pending = true
  try {
    await player.play()
    needsGesture = false
    // play() 可能还在等待音频加载；用户在等待期间关闭时，不能被晚到的播放结果重新开启。
    if (!enabled.value) player.pause()
  } catch (error) {
    needsGesture = error instanceof DOMException && error.name === 'NotAllowedError'
  } finally {
    pending = false
  }
}

function toggle() {
  if (pending || (audio.value && !audio.value.paused)) {
    enabled.value = false
    needsGesture = false
    audio.value?.pause()
  } else {
    enabled.value = true
    void play()
  }
}

function resumeAfterGesture(event: Event) {
  // 有声自动播放被浏览器拦截后，首次操作接续播放；黑胶自身的点击只交给 toggle 处理。
  if (!needsGesture || (event.target instanceof Element && event.target.closest('[data-background-music-toggle]'))) return
  if (event instanceof KeyboardEvent && (event.repeat || !['Enter', ' '].includes(event.key))) return
  void play()
}

onMounted(() => {
  window.addEventListener('pointerdown', resumeAfterGesture)
  window.addEventListener('keydown', resumeAfterGesture)
  // 音乐从加载页开始播放；开屏状态只控制黑胶开关显隐，不能等待揭幕或重置播放进度。
  void play()
})
onBeforeUnmount(() => {
  enabled.value = false
  audio.value?.pause()
  window.removeEventListener('pointerdown', resumeAfterGesture)
  window.removeEventListener('keydown', resumeAfterGesture)
})
</script>

<template>
  <audio ref="audio" src="/audio/deadman.mp3" loop preload="none" @playing="playing = true" @pause="playing = false" @waiting="playing = false" @error="playing = false" />
  <button v-if="visible" class="corner-music-toggle" type="button" data-background-music-toggle
    :aria-label="playing ? '暂停背景音乐' : '播放背景音乐'" :aria-pressed="playing"
    :title="playing ? '暂停 · Deadman — 蔡徐坤' : '播放 · Deadman — 蔡徐坤'" @click="toggle">
    <span class="corner-vinyl" :class="{ 'is-playing': playing }" aria-hidden="true"><span class="corner-vinyl-label" /></span>
  </button>
</template>

<style scoped>
.corner-music-toggle { display: grid; place-items: center; flex: 0 0 44px; width: 44px; height: 44px; padding: 0; border: 0; border-radius: 50%; background: transparent; cursor: pointer; }
.corner-music-toggle:hover { background: transparent; }
.corner-music-toggle:focus-visible { outline: 1px solid currentColor; outline-offset: 2px; }
.corner-vinyl { position: relative; display: grid; place-items: center; width: 30px; height: 30px; border-radius: 50%; background: repeating-radial-gradient(circle, transparent 0 1px, #ffffff15 1px 1.6px, transparent 1.6px 2.8px), conic-gradient(from 25deg, #131313, #4c4c4c 42deg, #181818 80deg, #101010 180deg, #444 225deg, #161616 270deg, #131313); box-shadow: inset 0 0 0 1px #111, 0 1px 3px #0003; animation: corner-vinyl-spin 4s linear infinite; animation-play-state: paused; }
.corner-vinyl.is-playing { animation-play-state: running; }
.corner-vinyl-label { display: grid; place-items: center; width: 11px; height: 11px; border-radius: 50%; background: #bc9d79; box-shadow: inset 0 0 0 1px #e1caaa55; }
.corner-vinyl-label::after { content: ''; width: 3px; height: 3px; border-radius: 50%; background: #252622; }
@keyframes corner-vinyl-spin { to { transform: rotate(360deg); } }
@media (max-width: 700px) { .corner-vinyl { width: 28px; height: 28px; } }
@media (prefers-reduced-motion: reduce) { .corner-vinyl { animation: none; } }
</style>
