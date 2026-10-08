<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import type { HomeArticle } from '../api/records'
import InkDissolveText from './InkDissolveText.vue'

defineProps<{ article: HomeArticle; layout: number }>()
const imageRoot = ref<HTMLElement | null>(null)
let motionQuery: MediaQueryList | undefined
let reduceMotion = false

function moveImage(event: PointerEvent) {
  if (event.pointerType === 'touch' || reduceMotion || !imageRoot.value) return
  const box = imageRoot.value.getBoundingClientRect()
  imageRoot.value.style.setProperty('--pointer-x', `${event.clientX - box.left}px`)
  imageRoot.value.style.setProperty('--pointer-y', `${event.clientY - box.top}px`)
  imageRoot.value.style.setProperty('--focus-opacity', '1')
}

function leaveImage() { imageRoot.value?.style.setProperty('--focus-opacity', '0') }

function motionChanged(event: MediaQueryListEvent) {
  reduceMotion = event.matches
  if (reduceMotion) leaveImage()
}
onMounted(() => {
  motionQuery = matchMedia('(prefers-reduced-motion: reduce)')
  reduceMotion = motionQuery.matches
  motionQuery.addEventListener('change', motionChanged)
})
onBeforeUnmount(() => { motionQuery?.removeEventListener('change', motionChanged) })
</script>

<template>
  <article class="home-article-pair" :class="`home-article-pair--${layout}`" :data-record-id="article.recordId">
    <RouterLink :to="`/records/${article.recordId}`" class="home-article-image-link" :aria-label="article.title || '阅读文章'">
      <div ref="imageRoot" class="home-article-image" @pointermove="moveImage" @pointerleave="leaveImage">
        <img :src="article.imageUrl" :alt="article.title || '文章配图'" loading="lazy" decoding="async" />
        <img class="home-article-image-focus" :src="article.imageUrl" alt="" aria-hidden="true" loading="lazy" decoding="async" />
      </div>
    </RouterLink>
    <RouterLink :to="`/records/${article.recordId}`" class="home-article-text-link">
      <InkDissolveText class="home-article-text" :title="article.title" :text="article.excerpt" />
    </RouterLink>
  </article>
</template>

<style scoped>
.home-article-pair { position: relative; display: grid; grid-template-columns: minmax(0, .85fr) minmax(0, 1.45fr); align-items: center; gap: clamp(45px, 7vw, 112px); margin: 0 0 clamp(140px, 20vw, 280px); }
.home-article-image-link, .home-article-text-link { display: block; min-width: 0; color: #292d25; }
.home-article-image-link { grid-column: 2; grid-row: 1; }
.home-article-text-link { grid-column: 1; grid-row: 1; }
.home-article-image { position: relative; overflow: hidden; --focus-opacity: 0; }
.home-article-image img { display: block; width: 100%; height: auto; max-height: 70vh; object-fit: contain; }
.home-article-image .home-article-image-focus { position: absolute; inset: 0; width: 100%; height: 100%; filter: blur(12px); opacity: var(--focus-opacity); mask-image: radial-gradient(ellipse 135px 100px at var(--pointer-x) var(--pointer-y), #000 10%, transparent 100%); transition: opacity .8s ease; pointer-events: none; }
.home-article-text { position: relative; font-family: 'Noto Serif SC', 'Songti SC', SimSun, serif; font-size: clamp(22px, 2.4vw, 34px); line-height: 1.6; font-weight: 400; }
.home-article-pair--1 { grid-template-columns: minmax(0, 1.45fr) minmax(0, .85fr); }
.home-article-pair--1 .home-article-image-link { grid-column: 1; }
.home-article-pair--1 .home-article-text-link { grid-column: 2; }
.home-article-pair--2, .home-article-pair--3 { grid-template-columns: 1fr; gap: 52px; }
.home-article-pair--2 .home-article-image-link, .home-article-pair--3 .home-article-image-link { grid-column: 1; width: 72%; justify-self: end; }
.home-article-pair--2 .home-article-text-link, .home-article-pair--3 .home-article-text-link { grid-column: 1; width: 40%; justify-self: end; }
.home-article-pair--2 .home-article-text-link { grid-row: 2; }
.home-article-pair--3 .home-article-image-link { grid-row: 2; width: 72%; justify-self: start; }
.home-article-pair--3 .home-article-text-link { grid-row: 1; justify-self: start; }
@media (max-width: 700px) {
  .home-article-pair, .home-article-pair--1 { grid-template-columns: 1fr; gap: 35px; margin-bottom: 120px; }
  .home-article-pair .home-article-image-link { grid-column: 1; grid-row: 1; width: 100%; }
  .home-article-pair .home-article-text-link { grid-column: 1; grid-row: 2; width: 90%; justify-self: start; }
  .home-article-pair--3 .home-article-image-link { grid-row: 2; }
  .home-article-pair--3 .home-article-text-link { grid-row: 1; }
}
</style>
