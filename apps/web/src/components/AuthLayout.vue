<script setup lang="ts">
import { computed, ref } from 'vue'
import AuthDayScene from './AuthDayScene.vue'

defineProps<{
  kicker: string
  title: string
  description: string
}>()

type SceneKey = 'morning' | 'afternoon' | 'dusk' | 'night'

const scenePhase = ref<SceneKey>('morning')
const sceneCopies: Record<SceneKey, { title: string; text: string }> = {
  morning: { title: '清晨好。', text: '它在玩毛线球。' },
  afternoon: { title: '中午。', text: '晒会儿太阳。' },
  dusk: { title: '天快黑了。', text: '它还在窗边。' },
  night: { title: '晚安。', text: '明天再见。' },
}
const sceneCopy = computed(() => sceneCopies[scenePhase.value])
</script>

<template>
  <div class="auth-page">
    <aside class="auth-scene" :class="`auth-scene--${scenePhase}`">
      <div class="auth-scene-backdrops" aria-hidden="true">
        <span class="backdrop-morning"></span>
        <span class="backdrop-afternoon"></span>
        <span class="backdrop-dusk"></span>
        <span class="backdrop-night"></span>
      </div>

      <RouterLink class="auth-brand" to="/" aria-label="猫的角落首页">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 32 32">
            <ellipse cx="16" cy="20" rx="7.5" ry="6.2" />
            <circle cx="8" cy="13" r="3" />
            <circle cx="14" cy="9" r="3" />
            <circle cx="21" cy="10" r="3" />
            <circle cx="25" cy="15" r="3" />
          </svg>
        </span>
        <span>猫的角落</span>
      </RouterLink>

      <div class="auth-scene-copy">
        <Transition name="scene-copy" mode="out-in">
          <div :key="scenePhase">
            <h2>{{ sceneCopy.title }}</h2>
            <p>{{ sceneCopy.text }}</p>
          </div>
        </Transition>
      </div>

      <AuthDayScene @phase-change="scenePhase = $event" />
    </aside>

    <section class="auth-card">
      <header class="auth-heading">
        <p class="auth-kicker">{{ kicker }}</p>
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
      </header>

      <slot />

      <footer class="auth-card-footer">
        <slot name="footer" />
      </footer>
    </section>
  </div>
</template>
