import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'umbel'

/** 确认图逐片描边的疏花复伞形植株，原图 UV 与连续主茎、叶柄共用墙面锚点。 */
export function makeReferenceUmbel() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
