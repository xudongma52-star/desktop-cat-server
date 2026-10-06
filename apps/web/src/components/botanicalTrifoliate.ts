import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'

/** 三出复叶植株：主组保持三片圆卵形小叶，较小侧组从主柄真实节点分出；各组短柄同节点汇接。 */
export function makeTrifoliateUnit() {
  const parts:THREE.BufferGeometry[]=[],p=(x:number,y:number,z:number)=>new THREE.Vector3(x,y,z)
  function stalk(points:THREE.Vector3[],radius:number,segments=24) {
    const curve=new THREE.CatmullRomCurve3(points,false,'centripetal')
    parts.push(new THREE.TubeGeometry(curve,segments,radius,6,false));return curve
  }
  const node=p(-1,-13/18,.022)
  const main=stalk([p(-2.18,-4.55,.032),p(-1.66,-2.65,.034),p(-1.15,-1.25,.029),node],.007,54)
  const leaves=[
    {base:node.clone().add(p(-.040,.100,-.010)),length:40/90,width:28/90,angle:.18,bend:.010,tilt:-.008},
    {base:node.clone().add(p(-.100,.020,-.010)),length:34/90,width:25/90,angle:1.10,bend:-.013,tilt:.012},
    {base:node.clone().add(p(.090,-.040,-.010)),length:30/90,width:23/90,angle:-1.30,bend:.012,tilt:-.015},
  ]
  // 圆卵状叶腹、短柔尖及浅叶基；轻微左右不对称，不复刻心叶凹口或窄叶密脉。
  const outline=new THREE.CatmullRomCurve3([
    p(0,0,0),p(-.20,.055,0),p(-.42,.25,0),p(-.48,.49,0),p(-.35,.76,0),p(-.10,.94,0),p(0,1,0),
    p(.13,.91,0),p(.37,.71,0),p(.48,.40,0),p(.28,.10,0),p(.10,.012,0),
  ],true,'centripetal')
  for(const leaf of leaves) {
    const c=Math.cos(leaf.angle),s=Math.sin(leaf.angle)
    const position=(x:number,y:number,z:number)=>p(leaf.base.x+x*c-y*s,leaf.base.y+x*s+y*c,z)
    // 叶柄终点与闭合轮廓的实际叶基相同；三条短柄从主柄的同一末端分开，不另造黑色节点球。
    stalk([node,node.clone().lerp(leaf.base,.56).add(p(.008,0,.002)),leaf.base],.004,18)
    const positions:number[]=[],uv:number[]=[],indices:number[]=[],rings=22,segments=64
    for(let i=0;i<=rings;i++)for(let j=0;j<=segments;j++) {
      const r=i/rings,edge=outline.getPoint(j/segments),x=edge.x*leaf.width*r,y=(.43+(edge.y-.43)*r)*leaf.length
      // 沿用已接受宽叶的一次宽缓叶腹，三片倾向各异；自由边回到基面，无厚边圈、锯齿或刻线。
      const interior=Math.pow(1-r*r,2)
      const z=leaf.base.z+(.048+leaf.bend*edge.x+leaf.tilt*(edge.y-.43))*interior
      positions.push(...position(x,y,z).toArray());uv.push(.5+x/leaf.width,y/leaf.length)
    }
    for(let i=0;i<rings;i++)for(let j=0;j<segments;j++) {
      const a=i*(segments+1)+j,b=a+segments+1;indices.push(a,a+1,b,a+1,b+1,b)
    }
    const geometry=new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3))
    geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2));geometry.setIndex(indices)
    geometry.computeVertexNormals();parts.push(geometry)
  }
  // 仅追加一枚较小侧生复叶；根取既有主柄真实曲线点，原主组三叶及其生成表达式不变。
  const sideRoot=main.getPoint(.49),sideNode=p(-185/90,-19/9,.022)
  stalk([sideRoot,sideRoot.clone().lerp(sideNode,.52).add(p(-.050,-.085,-.003)),sideNode],.005,32)
  const smallLeaves=[
    {base:sideNode.clone().add(p(-.010,.070,-.010)),length:30/90,width:20/90,angle:.22,height:.036,bend:.006,tilt:-.004},
    {base:sideNode.clone().add(p(-.050,.010,-.010)),length:25/90,width:18/90,angle:.98,height:.031,bend:-.005,tilt:.006},
    {base:sideNode.clone().add(p(.060,-.030,-.010)),length:22/90,width:16/90,angle:-1.30,height:.028,bend:.005,tilt:-.006},
  ]
  for(const leaf of smallLeaves) {
    const c=Math.cos(leaf.angle),s=Math.sin(leaf.angle)
    stalk([sideNode,sideNode.clone().lerp(leaf.base,.56).add(p(.005,0,.002)),leaf.base],.003,16)
    const positions:number[]=[],uv:number[]=[],indices:number[]=[],rings=22,segments=64
    for(let i=0;i<=rings;i++)for(let j=0;j<=segments;j++) {
      const r=i/rings,edge=outline.getPoint(j/segments),x=edge.x*leaf.width*r,y=(.43+(edge.y-.43)*r)*leaf.length
      // 新小叶倾向按实际面内坐标变化，中心收敛到同一XYZ；较小叶腹和薄边沿用原圆卵轮廓。
      const interior=Math.pow(1-r*r,2)
      const z=leaf.base.z+(leaf.height+leaf.bend*x/leaf.width+leaf.tilt*(y/leaf.length-.43))*interior
      positions.push(leaf.base.x+x*c-y*s,leaf.base.y+x*s+y*c,z);uv.push(.5+x/leaf.width,y/leaf.length)
    }
    for(let i=0;i<rings;i++)for(let j=0;j<segments;j++) {
      const a=i*(segments+1)+j,b=a+segments+1;indices.push(a,a+1,b,a+1,b+1,b)
    }
    const geometry=new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3))
    geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2));geometry.setIndex(indices)
    geometry.computeVertexNormals();parts.push(geometry)
  }
  const geometry=mergeGeometries(parts,false)!
  parts.forEach(part=>part.dispose());geometry.computeBoundingSphere();return geometry
}
