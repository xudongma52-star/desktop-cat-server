import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'center-bamboo-01'

/** 中下部第一株短竹：逐叶追踪与连续竹秆，保护已验收植物，单独等待验收。 */
export function makeReferenceCenterBamboo() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
