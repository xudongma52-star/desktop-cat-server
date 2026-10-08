import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'shoot-step-04'

/** 双笋右侧小笋：独立直立嫩芽和三层笋壳，保留左侧高笋的模型与位置。 */
export function makeReferencePairShortShoot() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
