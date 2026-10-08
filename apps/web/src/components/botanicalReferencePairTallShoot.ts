import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'shoot-step-03'

/** 本轮双笋中左侧较高的一株：独立轮廓、笋壳层片与参考 UV，逐株验收。 */
export function makeReferencePairTallShoot() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
