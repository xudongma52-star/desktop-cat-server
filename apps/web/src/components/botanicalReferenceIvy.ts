import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'ivy'

/** 确认图逐片描边的四叶浅裂弯枝，原图 UV 与连续主茎、叶柄共用墙面锚点。 */
export function makeReferenceIvy() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
