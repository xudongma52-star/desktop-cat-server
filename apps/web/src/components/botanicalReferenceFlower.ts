import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'main-flower'

/** 原图逐瓣描边的浅浮雕，UV 保留原图坐标；不以图像明暗直接生成几何深度。 */
export function makeReferenceMainFlower() {
  const geometry=makeDeferredReferenceGeometry(mesh)

  // 花头保持已确认锚点；独立重建的主副茎、叶柄与花苞共享这一锚点和动态抬升，避免接点跳层。
  return geometry
}
