import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'bottom-layer-without-fern'

/** 底部圆卵叶与细草，旧蕨叶索引已移除，确认图 UV、连续叶柄与浅浮雕统一导出。 */
export function makeReferenceBottomLayer() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
