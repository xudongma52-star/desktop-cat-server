import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'right-pair'

/** 确认图逐片描边的右侧叶枝与单根高穗，原图 UV 与连续主茎、叶柄共用墙面锚点。 */
export function makeReferenceRightPair() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
