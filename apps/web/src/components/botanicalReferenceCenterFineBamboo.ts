import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'center-bamboo-02'

/** 中右部后侧细竹：独立描出倾斜竹秆与三簇叶，沿用确认图 UV 和共享动态阴影。 */
export function makeReferenceCenterFineBamboo() {
  const geometry=makeDeferredReferenceGeometry(mesh)
  return geometry
}
