import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'flower-stems'

/** 确认图逐段描边的连续木质茎秆与附属细叶、花苞，UV 使用独立原图。 */
export function makeReferenceFlowerStems() {
  const geometry=makeDeferredReferenceGeometry(mesh)

  // 与花托共用墙面锚点和影响场，显现抬升时接点不跳层。
  return geometry
}
