import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'gap-sprig'

/** 空隙五叶小枝和两条细草，确认图 UV、连续叶柄与浅浮雕统一导出。 */
export function makeReferenceGapSprig() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
