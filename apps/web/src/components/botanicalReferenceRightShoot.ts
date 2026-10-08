import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'shoot-step-02'

/** 长竹右侧的一株竹笋：独立笋壳轮廓、嫩芽与确认图 UV，不改已有植株。 */
export function makeReferenceRightShoot() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
