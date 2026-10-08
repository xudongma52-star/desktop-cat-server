import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'bamboo-tall-02'

/** 双长竹中的第二株：独立右向叶簇、连续竹秆，保持已验收第一株不变。 */
export function makeReferenceSecondTallBamboo() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
