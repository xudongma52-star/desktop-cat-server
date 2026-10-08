import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'bottom-left-additions-v2'

/** 三组配植薄壳；细花枝独立加宽叶面和侧柄，原色 UV、连续根茎和离墙锚点保持一致。 */
export function makeReferenceBottomLeftAdditions() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
