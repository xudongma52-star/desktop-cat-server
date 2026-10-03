<script setup lang="ts">
import logoUrl from '../assets/max-logo-original.jpg'
import { useId } from 'vue'

const emit = defineEmits<{ ready: [] }>()
const filterId = `max-paper-${useId()}`
</script>

<template>
  <!-- 只裁切原图白色留边，以原像素亮度转为透明度；不重画交织轮廓、负空间或 MAX 字样。 -->
  <svg class="corner-brand-mark" viewBox="205 335 860 592" aria-hidden="true" focusable="false">
    <defs>
      <filter :id="filterId" color-interpolation-filters="sRGB" x="0" y="0" width="100%" height="100%">
        <!-- 原图纸白有轻微 JPEG 灰度；2% 亮度余量消除纸底，深色轮廓仍按原像素呈现。 -->
        <feColorMatrix type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  -.216852 -.729504 -.073644 0 1" />
      </filter>
    </defs>
    <image :href="logoUrl" width="1254" height="1254" :filter="`url(#${filterId})`" @load="emit('ready')" @error="emit('ready')" />
  </svg>
</template>
