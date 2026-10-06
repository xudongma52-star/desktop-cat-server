import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'
import { makeThinLeaf } from './botanicalUnifiedLeaves'

/** 中部单组头状花序：薄长圆舌状瓣、侧倾次花与合拢花苞，沿真实弯茎连接。 */
export function makeCentralFlowerSprig() {
  const parts:THREE.BufferGeometry[]=[],p=(x:number,y:number,z:number)=>new THREE.Vector3(x,y,z)
  function stalk(points:THREE.Vector3[],radius:number,segments=36) {
    const curve=new THREE.CatmullRomCurve3(points,false,'centripetal')
    parts.push(new THREE.TubeGeometry(curve,segments,radius,6,false));return curve
  }
  function surface(rows:number,columns:number,sample:(t:number,u:number)=>THREE.Vector3) {
    const positions:number[]=[],uv:number[]=[],indices:number[]=[]
    for(let i=0;i<=rows;i++)for(let j=0;j<=columns;j++){
      positions.push(...sample(i/rows,j/columns*2-1).toArray());uv.push(j/columns,i/rows)
    }
    for(let i=0;i<rows;i++)for(let j=0;j<columns;j++){const a=i*(columns+1)+j,b=a+columns+1;indices.push(a,a+1,b,a+1,b+1,b)}
    const geometry=new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2))
    geometry.setIndex(indices);geometry.computeVertexNormals();parts.push(geometry)
  }
  function head(center:THREE.Vector3,radius:number,turn:number,sideways:boolean) {
    const lengths=sideways ? [.98,1.06,.92,1.03,.96,1.07,.94] : [1.04,.94,1.08,.98,.91,1.03,.96,1.06]
    const scale=radius/.43,c=Math.cos(turn),s=Math.sin(turn)
    function local(x:number,y:number,z:number) {
      const flatY=y*(sideways ? .68 : 1)
      return p(center.x+x*c-flatY*s,center.y+x*s+flatY*c,center.z+z+y*(sideways ? .085 : .015))
    }
    for(const [index,length]of lengths.entries()) {
      const angle=index/lengths.length*Math.PI*2+.035*Math.sin(index*1.9),ca=Math.cos(angle),sa=Math.sin(angle)
      surface(28,14,(t,u)=>{
        // 窄根藏在小花盘下，延后展宽使中段留出瓣间凹缝；中外段及半圆端保持长圆轮廓。
        const breadth=.091*scale*(1+.08*Math.sin(index*1.4))
        const opening=THREE.MathUtils.clamp((t-.22)/.48,0,1),neck=opening*opening*(3-2*opening)
        const width=.014*scale+(breadth-.014*scale)*neck
        const cap=breadth*(1-Math.sqrt(Math.max(0,1-u*u)))
        const radial=.040*scale+t*(radius*length-.040*scale-cap),across=width*u+.004*scale*Math.sin(Math.PI*t)*Math.sin(index*1.7)
        // 整片轻弯与少量前后倾角形成遮挡，边缘不叠加鼓包或等厚侧墙。
        const tilt=[-.006,.012,.002,-.009,.004,.014,-.005,.003][index]!
        const bend=.004*scale*Math.sin(Math.PI*t)+tilt*scale*t
        const twist=.003*scale*u*Math.sin(Math.PI*t)*Math.sin(index*1.3)
        return local(radial*ca-across*sa,radial*sa+across*ca, -.006*scale+bend+twist)
      })
    }
    // 浅小花盘覆盖并连接窄瓣根与原茎端，略高于根部以读出中心，不加等厚圆片侧墙。
    surface(14,40,(t,u)=>{
      const angle=(u+1)*Math.PI,r=.074*scale*t
      return local(r*Math.cos(angle),r*Math.sin(angle),scale*(.009+.004*(1-t*t)))
    })
  }
  const mainHead=p(.14,.56,.065),secondaryHead=p(1.28,-.24,.060),budCenter=p(.71,1.28,.056)
  const main=stalk([p(.68,-4.55,.035),p(.50,-2.55,.052),p(-.05,-.85,.055),mainHead],.009,56)
  const sideRoot=main.getPoint(.53)
  const side=stalk([sideRoot,sideRoot.clone().lerp(secondaryHead,.46).add(p(.16,-.12,.006)),secondaryHead],.006,36)
  const budRoot=main.getPoint(.82),budBase=budCenter.clone().add(p(0,-.125,0))
  stalk([budRoot,budRoot.clone().lerp(budBase,.55).add(p(.12,.08,.005)),budBase],.0045,32)
  head(mainHead,.43,.18,false);head(secondaryHead,.31,-.30,true)
  // 三片细长薄瓣合拢为花苞，保留原基部接点；不再用整颗椭圆球及两根凸起分缝。
  for(const [index,offset]of [-.028,.012,.036].entries()) {
    surface(26,12,(t,u)=>{
      const envelope=Math.sin(Math.PI*t),width=index===1 ? .041 : index===0 ? .028 : .026
      return budBase.clone().add(p(offset*Math.pow(envelope,.7)+u*width*Math.sqrt(envelope),.267*t,
        (.009+.003*index)*envelope*(1-u*u)))
    })
  }
  // 萼片由同一茎端托住合拢瓣团，沿基部向两侧展开少量薄尖片。
  for(const side of [-1,0,1]) {
    surface(14,8,(t,u)=>{
      const envelope=Math.sin(Math.PI*t)
      return budBase.clone().add(p(side*.052*t+u*.014*envelope,(side===0 ? .085 : .105)*t,
        (.006+(side===0 ? .003 : 0))*envelope*(1-u*u)))
    })
  }
  function lance(curve:THREE.CatmullRomCurve3,t:number,angleOffset:number,length:number,width:number,variant:number) {
    const root=curve.getPointAt(t),tangent=curve.getTangentAt(t),angle=-Math.atan2(tangent.x,tangent.y)+angleOffset
    const base=root.clone().add(p(-Math.sin(angle)*.10,Math.cos(angle)*.10,0));base.z=.012
    stalk([root,root.clone().lerp(base,.5),base],.0034,12)
    parts.push(makeThinLeaf(base,length,width,angle,variant*.9,false,variant))
  }
  lance(main,.25,.81,.77,.057,0);lance(main,.45,-.74,.65,.052,1);lance(main,.67,.68,.53,.044,0)
  lance(side,.56,-.70,.48,.039,1)
  const geometry=mergeGeometries(parts,false)!
  parts.forEach(part=>part.dispose());geometry.computeBoundingSphere();return geometry
}
