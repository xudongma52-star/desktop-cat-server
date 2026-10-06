import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'

/** 底中部三片低矮心形阔叶；浅凹叶基和短尖端区别于裂叶及银杏扇叶。 */
export function makeLowHeartLeaves() {
  const parts:THREE.BufferGeometry[]=[],p=(x:number,y:number,z:number)=>new THREE.Vector3(x,y,z)
  function stalk(points:THREE.Vector3[],radius:number,segments=24) {
    parts.push(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(points,false,'centripetal'),segments,radius,6,false))
  }
  // 低枝由底中偏右群落向左上延伸，三个不同节点分出叶柄，不把新枝抬进中央留白。
  stalk([p(1.72,-4.40,.022),p(1.27,-3.79,.026),p(.79,-3.43,.023),p(.25,-3.18,.012)],.009,40)
  const leaves=[
    {root:p(.39,-3.24,.018),base:p(-.14,-2.77,.004),width:1.08,length:.92,angle:.76,bend:.008},
    {root:p(.91,-3.52,.024),base:p(1.19,-2.93,.004),width:.72,length:.72,angle:-.50,bend:-.006},
    {root:p(1.43,-4.00,.024),base:p(1.72,-3.63,.004),width:.48,length:.47,angle:-.24,bend:.005},
  ]
  // 主叶绕原叶柄终点顺时针微转10°，下缘避开旧窄叶尖；反算叶基保持接点，不改变叶面高度或其余叶片。
  const mainLeaf=leaves[0]!,jointOffset=.10*mainLeaf.length
  const joint=mainLeaf.base.clone().add(p(-Math.sin(mainLeaf.angle)*jointOffset,Math.cos(mainLeaf.angle)*jointOffset,0))
  mainLeaf.angle-=Math.PI/18
  mainLeaf.base=joint.clone().sub(p(-Math.sin(mainLeaf.angle)*jointOffset,Math.cos(mainLeaf.angle)*jointOffset,0))
  // 一条圆缓闭合轮廓定义浅凹叶基、宽叶腹和短柔尖；不复用带密集侧脉的既有细叶生成器。
  const outline=new THREE.CatmullRomCurve3([
    p(0,.10,0),p(-.24,-.025,0),p(-.45,.10,0),p(-.49,.38,0),p(-.35,.69,0),p(-.12,.91,0),p(0,1,0),
    p(.16,.88,0),p(.38,.65,0),p(.48,.34,0),p(.43,.08,0),p(.22,-.02,0),
  ],true,'centripetal')
  for(const leaf of leaves) {
    const c=Math.cos(leaf.angle),s=Math.sin(leaf.angle)
    function position(x:number,y:number,z:number) {return p(leaf.base.x+x*c-y*s,leaf.base.y+x*s+y*c,z)}
    const joint=position(0,.10*leaf.length,leaf.base.z)
    stalk([leaf.root,leaf.root.clone().lerp(joint,.55).add(p(.014,0,.005)),joint],.005)
    const positions:number[]=[],uv:number[]=[],indices:number[]=[],rings=22,segments=72
    for(let i=0;i<=rings;i++)for(let j=0;j<=segments;j++) {
      const r=i/rings,edge=outline.getPoint(j/segments),x=edge.x*leaf.width*r,y=(.40+(edge.y-.40)*r)*leaf.length
      // 所有自由边缘降到z=.004；叶腹只有一次宽缓的偏折，没有刻纹、凸起叶脉或侧墙。
      const interior=Math.pow(1-r*r,2),z=leaf.base.z+(.048+leaf.bend*edge.x)*interior
      positions.push(...position(x,y,z).toArray());uv.push(.5+x,y)
    }
    for(let i=0;i<rings;i++)for(let j=0;j<segments;j++){const a=i*(segments+1)+j,b=a+segments+1;indices.push(a,a+1,b,a+1,b+1,b)}
    const geometry=new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2))
    geometry.setIndex(indices);geometry.computeVertexNormals();parts.push(geometry)
  }
  const geometry=mergeGeometries(parts,false)!
  parts.forEach(part=>part.dispose());geometry.computeBoundingSphere();return geometry
}
