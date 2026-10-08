import * as THREE from 'three'
import { MeshoptDecoder } from 'three/examples/jsm/libs/meshopt_decoder.module.js'
import manifest from '../assets/optimized/manifest.json'

const urls = import.meta.glob('../assets/optimized/*.mesh.gz', { eager: true, query: '?url', import: 'default' }) as Record<string, string>
const models = manifest.models as Record<string, { bytes: number; decodedBytes: number }>
const textureUrls = import.meta.glob('../assets/optimized/*.{png,webp}', { eager: true, query: '?url', import: 'default' }) as Record<string, string>
const textureManifest = manifest.textures as Record<string, { file: string; bytes: number }>
const keys = new WeakMap<THREE.BufferGeometry, string>()
const refinements = new WeakMap<THREE.BufferGeometry, (geometry: THREE.BufferGeometry) => void>()
export const wallAssetBytes = {
  normal: manifest.wall['beige_wall_001_nor_gl_1k.jpg'].bytes,
  rough: manifest.wall['beige_wall_001_rough_1k.jpg'].bytes,
}

export function botanicalTextureUrl(name: string) {
  return textureUrls[`../assets/optimized/${textureManifest[name]?.file}`]!
}

export function botanicalTextureBytes(url: string) {
  const record = Object.values(textureManifest).find(item => textureUrls[`../assets/optimized/${item.file}`] === url)
  return record?.bytes ?? 0
}

/** 保留原有材质和场景结构；网格就绪前由加载队列隐藏对象，不上传空网格。 */
export function makeDeferredReferenceGeometry(key: string, refine?: (geometry: THREE.BufferGeometry) => void) {
  const geometry = new THREE.BufferGeometry()
  keys.set(geometry, key)
  if (refine) refinements.set(geometry, refine)
  return geometry
}

export function referenceGeometryKey(geometry: THREE.BufferGeometry) {
  return keys.get(geometry)
}

export function referenceGeometryBytes(geometry: THREE.BufferGeometry) {
  return models[referenceGeometryKey(geometry) ?? '']?.bytes ?? 0
}

type Attribute = { key: string; count: number; stride: number; offset: number; length: number }
export async function fetchBotanicalBytes(url: string, signal: AbortSignal, progress: (bytes: number) => void, packed?: { bytes: number; decodedBytes: number }) {
  const response = await fetch(url, { signal })
  if (!response.ok) throw Error(`Botanical asset HTTP ${response.status}: ${url}`)
  // 浏览器对 Content-Encoding:gzip 自动解压，进度仍按实际传输量计量。
  let progressScale = 1
  const detectProgressScale = (value: Uint8Array) => {
    if (packed && value.length >= 2) progressScale = value[0] === 0x1f && value[1] === 0x8b ? 1 : packed.bytes / packed.decodedBytes
  }
  const parts: Uint8Array[] = []
  let received = 0
  const reader = response.body?.getReader()
  if (reader) {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      if (!parts.length) detectProgressScale(value)
      parts.push(value); received += value.length; progress(Math.round(received * progressScale))
    }
  } else {
    const value = new Uint8Array(await response.arrayBuffer())
    detectProgressScale(value)
    parts.push(value); received = value.length; progress(Math.round(received * progressScale))
  }
  const payload = new Uint8Array(received)
  let cursor = 0
  for (const part of parts) { payload.set(part, cursor); cursor += part.length }
  return payload
}

export async function loadReferenceGeometry(geometry: THREE.BufferGeometry, signal: AbortSignal, progress: (bytes: number) => void) {
  const key = referenceGeometryKey(geometry)
  const url = urls[`../assets/optimized/${key}.mesh.gz`]
  if (!url) throw Error(`Missing botanical geometry: ${key}`)
  const payload = await fetchBotanicalBytes(url, signal, progress, models[key!])
  // 文件本身带 gzip；兼容代理已通过 Content-Encoding 解压的响应。
  const bytes = payload[0] === 0x1f && payload[1] === 0x8b
    ? new Uint8Array(await new Response(new Blob([payload]).stream().pipeThrough(new DecompressionStream('gzip'))).arrayBuffer())
    : payload
  if (signal.aborted) throw new DOMException('Aborted', 'AbortError')
  if (new TextDecoder().decode(bytes.subarray(0, 4)) !== 'BTR1') throw Error(`Invalid botanical geometry: ${key}`)
  const headerLength = new DataView(bytes.buffer, bytes.byteOffset).getUint32(4, true)
  const header = JSON.parse(new TextDecoder().decode(bytes.subarray(8, 8 + headerLength))) as { version: number; attributes: Attribute[] }
  if (header.version !== 1) throw Error(`Unsupported botanical geometry: ${key}`)
  await MeshoptDecoder.ready
  if (signal.aborted) throw new DOMException('Aborted', 'AbortError')
  for (const attribute of header.attributes) {
    const decoded = new Uint8Array(attribute.count * attribute.stride)
    const source = bytes.subarray(8 + headerLength + attribute.offset, 8 + headerLength + attribute.offset + attribute.length)
    if (attribute.key === 'index') {
      MeshoptDecoder.decodeIndexSequence(decoded, attribute.count, attribute.stride, source)
      const indices = new Uint32Array(decoded.buffer)
      let maxIndex = 0
      for (const index of indices) maxIndex = Math.max(maxIndex, index)
      // 与原 BufferGeometry.setIndex(number[]) 的 Uint16/Uint32 选择完全一致。
      geometry.setIndex(new THREE.BufferAttribute(maxIndex >= 65535 ? indices : Uint16Array.from(indices), 1))
    } else {
      MeshoptDecoder.decodeVertexBuffer(decoded, attribute.count, attribute.stride, source)
      geometry.setAttribute(attribute.key === 'anchor' ? 'referenceAnchor' : attribute.key, new THREE.BufferAttribute(new Float32Array(decoded.buffer), attribute.stride / 4))
    }
  }
  refinements.get(geometry)?.(geometry)
  geometry.computeBoundingSphere()
}
