import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const wide = 'birds-wide'
const gathered = 'birds-gathered'

/** 确认图的两种翼姿逐部位描边；原图 UV、真实厚度和墙面锚点来自 Blender。 */
export function makeReferenceBirds() {
  return [wide,gathered].map(mesh=>{
    return makeDeferredReferenceGeometry(mesh)
  })
}
