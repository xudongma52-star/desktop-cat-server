import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'

/** 单片连续五浅裂掌状叶；一处宽缓叶腹与短柔裂尖，不拆成五片独立叶。 */
export function makePalmLeaf() {
  const p=(x:number,y:number,z:number)=>new THREE.Vector3(x,y,z),base=p(107/90,-1.8,.012)
  // 叶柄实际终点就是轮廓首点的叶基；下部宽缓偏弯，末段轻转，不额外堆节点球。
  const axis=new THREE.CatmullRomCurve3([p(107/90,-4.55,.032),p(96/90,-29/9,.028),p(95/90,-106/45,.023),base],false,'centripetal')
  const stalk=new THREE.TubeGeometry(axis,54,.007,6,false)
  // 以叶基为原点的轮廓：上中最长、上左下收，两侧基部裂片更小；谷口止于外半段。
  const outline=new THREE.CatmullRomCurve3([
    p(0,0,0),p(-11/90,5/90,0),p(-26/90,13/90,0),p(-31/90,24/90,0),
    p(-20/90,26/90,0),p(-19/90,32/90,0),p(-24/90,43/90,0),p(-16/90,43/90,0),
    p(-10/90,42/90,0),p(-6/90,52/90,0),p(0,62/90,0),p(5/90,54/90,0),
    p(10/90,45/90,0),p(17/90,43/90,0),p(27/90,48/90,0),p(26/90,37/90,0),
    p(29/90,32/90,0),p(40/90,26/90,0),p(33/90,19/90,0),p(21/90,16/90,0),
    p(13/90,6/90,0),p(5/90,2/90,0),
  ],true,'centripetal')
  const positions:number[]=[],uv:number[]=[],indices:number[]=[],rings=24,segments=96,bellyY=28/90
  for(let i=0;i<=rings;i++)for(let j=0;j<=segments;j++) {
    const r=i/rings,edge=outline.getPoint(j/segments),x=edge.x*r,y=bellyY+(edge.y-bellyY)*r
    // 主叶腹按实际面内位置形成纵向宽弧和横向缓坡，不再沿长短不同的射线分别压到凹裂谷平面。
    // 中心各方向仍收敛；薄自由边随同一曲面轻微起伏，叶基保持原XYZ，不刻脉或加厚侧墙。
    const along=THREE.MathUtils.clamp(y/(62/90),0,1),arc=Math.sin(Math.PI*along)
    const across=Math.cos(Math.PI*(x-.018*arc)/.95)
    const z=base.z+(.048+.006*x)*arc*arc*across*across
    positions.push(base.x+x,base.y+y,z);uv.push(.5+x,.5+y)
  }
  for(let i=0;i<rings;i++)for(let j=0;j<segments;j++) {
    const a=i*(segments+1)+j,b=a+segments+1;indices.push(a,a+1,b,a+1,b+1,b)
  }
  const blade=new THREE.BufferGeometry()
  blade.setAttribute('position',new THREE.Float32BufferAttribute(positions,3))
  blade.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2));blade.setIndex(indices);blade.computeVertexNormals()
  const geometry=mergeGeometries([stalk,blade],false)!
  stalk.dispose();blade.dispose();geometry.computeBoundingSphere();return geometry
}
