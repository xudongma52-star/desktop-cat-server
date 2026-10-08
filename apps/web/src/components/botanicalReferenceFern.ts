import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'fern-v3'

/** 高清 alpha 轮廓的蕨羽薄壳，含背面及封闭侧缘，确认图 UV、连续叶柄与浅浮雕统一导出。 */
export function makeReferenceFern() {
  return makeDeferredReferenceGeometry(mesh,geometry=>{
    // 根部细柄延伸到页面底边之外；上方已确认的蕨羽轮廓、颜色和 UV 保持不变。
    const positions=geometry.getAttribute('position')
    for(let i=0;i<positions.count;i++){
      const y=positions.getY(i)
      if(y< -3.7)positions.setY(i,-3.7+(y+3.7)*2.2)
    }
    geometry.computeVertexNormals()
  })
}
