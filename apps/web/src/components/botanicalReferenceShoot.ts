import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'shoot-step-01'

/** 第一株左侧竹笋：确认图轮廓与 UV、连续笋体和笋壳起伏，单独等待验收。 */
export function makeReferenceShoot() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
