import { makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const mesh = 'left-sprig-v2'

/** 高清确认图轮廓薄壳；五叶、小花序与连续茎柄保留原色 UV，并共用墙面锚点。 */
export function makeReferenceLeftSprig() {
  return makeDeferredReferenceGeometry(mesh,geometry=>{
    // 茎尾继续长到画面底边之外，避免完整资产的切口露在画面内；叶片和花序坐标不变。
    const positions=geometry.getAttribute('position')
    for(let i=0;i<positions.count;i++){
      const y=positions.getY(i)
      if(y< -3.2)positions.setY(i,-3.2+(y+3.2)*1.8)
    }
    geometry.computeVertexNormals()
  })
}
