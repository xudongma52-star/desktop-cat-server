import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'bamboo-tall-01'

/** 双长竹中靠左的一株：逐叶轮廓、连续弯秆和确认图 UV，独立进行验收。 */
export function makeReferenceTallBamboo() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
