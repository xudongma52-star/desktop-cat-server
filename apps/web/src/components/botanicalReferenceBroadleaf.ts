import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'broadleaf'

/** 已确认四片尖卵叶、顶端合拢花苞及木质弯枝的 Blender 描边网格。 */
export function makeReferenceBroadleaf() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
