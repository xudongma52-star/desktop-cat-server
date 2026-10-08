import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'bamboo'

/** 双竹薄壳：独立叶脉起伏、连续竹秆与竹枝、原色 UV、根部离墙锚点。 */
export function makeReferenceBamboo() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
