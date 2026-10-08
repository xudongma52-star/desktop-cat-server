import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'ginkgo'

/** 已确认银杏三叶与木质弯茎的描边网格，细叶脉沿原图 UV 投影。 */
export function makeReferenceGinkgo() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
