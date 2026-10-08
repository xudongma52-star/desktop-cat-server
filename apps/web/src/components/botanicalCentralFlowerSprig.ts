import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'
import { makeThinLeaf } from './botanicalUnifiedLeaves'
import flowerMesh from '../assets/central-flowers.blender.json'
import tracedFlower from '../assets/main-flower.traced.json'

/** 中部单组头状花序：宽薄叠压花瓣、颗粒花心与侧倾五瓣次花，沿真实弯茎连接。 */
export function makeCentralFlowerSprig(includeMainHead=true) {
  const parts:THREE.BufferGeometry[]=[],p=(x:number,y:number,z:number)=>new THREE.Vector3(x,y,z)
  function stalk(points:THREE.Vector3[],radius:number,segments=36,render=true) {
    const curve=new THREE.CatmullRomCurve3(points,false,'centripetal')
    if(render)parts.push(new THREE.TubeGeometry(curve,segments,radius,6,false));return curve
  }
  function surface(rows:number,columns:number,sample:(t:number,u:number)=>THREE.Vector3) {
    const positions:number[]=[],uv:number[]=[],indices:number[]=[]
    for(let i=0;i<=rows;i++)for(let j=0;j<=columns;j++){
      positions.push(...sample(i/rows,j/columns*2-1).toArray());uv.push(j/columns,i/rows)
    }
    // 花瓣沿径向推进、横向展开时，三角形正面朝向墙外；双面显示不能替代正确法线，否则阴影偏移会造成整片自遮蔽。
    for(let i=0;i<rows;i++)for(let j=0;j<columns;j++){const a=i*(columns+1)+j,b=a+columns+1;indices.push(a,b,a+1,a+1,b,b+1)}
    const geometry=new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2))
    geometry.setIndex(indices);geometry.computeVertexNormals();parts.push(geometry)
  }
  // 花头来自 Blender 独立花瓣的细分曲面与薄片厚度烘焙；保持现有合并网格和鼠标深度场。
  const heads=new THREE.BufferGeometry()
  heads.setAttribute('position',new THREE.Float32BufferAttribute(flowerMesh.position,3))
  heads.setAttribute('normal',new THREE.Float32BufferAttribute(flowerMesh.normal,3))
  const headUv:number[]=[]
  for(let i=0;i<flowerMesh.position.length;i+=3)headUv.push(flowerMesh.position[i]!/14.22+.5,flowerMesh.position[i+1]!/8+.5)
  heads.setAttribute('uv',new THREE.Float32BufferAttribute(headUv,2))
  // 桌面两朵花及重建主副茎均由投影网格承载；本组只补花苞和附着细叶，手机保留原花头。
  const headIndices=includeMainHead ? flowerMesh.index : []
  heads.setIndex(headIndices);parts.push(heads)
  // 新主花锚定墙面，茎端降至花盘背后，避免原较高接点穿过薄花瓣正面。
  // 副花缩小并侧倾，枝端同步到花托末端，避免沿用原接点造成悬空。
  const secondaryDx=(911-986)*(.43/273)*.78,secondaryDy=(820-944)*(.43/273)*.78,secondaryAngle=-.22
  const mainHead=p(.14,.56,includeMainHead ? .019 : -.008),secondaryHead=includeMainHead ? p(1.28,-.24,.020) : p(1.28+secondaryDx*Math.cos(secondaryAngle)-secondaryDy*Math.sin(secondaryAngle),-.24+secondaryDx*Math.sin(secondaryAngle)+secondaryDy*Math.cos(secondaryAngle),-.008),budCenter=p(.71,1.28,.025)
  const legacyMain=new THREE.CatmullRomCurve3([p(.68,-4.55,.018),p(.50,-2.55,.020),p(-.05,-.85,.020),mainHead],false,'centripetal')
  // 新主茎在投影网格里绘制，曲线仍用于所有真实接点；不再叠一根旧细管。
  const main=includeMainHead ? stalk(legacyMain.points,.012,56) : stalk(tracedFlower.stemControlPoints.map(point=>p(point[0]!,point[1]!,point[2]!)),.012,56,false)
  function mainAtHeight(y:number){
    let low=0,high=1
    for(let i=0;i<24;i++){const mid=(low+high)/2;if(main.getPoint(mid).y<y)low=mid;else high=mid}
    return {point:main.getPoint((low+high)/2),tangent:main.getTangent((low+high)/2)}
  }
  // 分枝保持旧纵向位置，只沿新弯茎重新接合；次花和花苞端点不移动。
  const sideRoot=includeMainHead ? main.getPoint(.53) : p(...tracedFlower.secondaryStemControlPoints[0] as [number,number,number])
  // 桌面副茎从 Blender 的同一控制点取接点和切线；不再叠加旧细管，侧叶仍沿实际副茎附着。
  const sidePoints=includeMainHead ? [sideRoot,sideRoot.clone().lerp(secondaryHead,.46).add(p(.16,-.12,.002)),secondaryHead] : tracedFlower.secondaryStemControlPoints.map(point=>p(point[0]!,point[1]!,point[2]!))
  const side=stalk(sidePoints,.008,36,includeMainHead)
  const budRoot=includeMainHead ? main.getPoint(.82) : mainAtHeight(legacyMain.getPoint(.82).y).point,budBase=budCenter.clone().add(p(0,-.125,0))
  stalk([budRoot,budRoot.clone().lerp(budBase,.55).add(p(.12,.08,.005)),budBase],.0045,32)
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
    const attachment=!includeMainHead&&curve===main ? mainAtHeight(legacyMain.getPointAt(t).y) : {point:curve.getPointAt(t),tangent:curve.getTangentAt(t)}
    const root=attachment.point,tangent=attachment.tangent,angle=-Math.atan2(tangent.x,tangent.y)+angleOffset
    const base=root.clone().add(p(-Math.sin(angle)*.10,Math.cos(angle)*.10,0));base.z=.012
    stalk([root,root.clone().lerp(base,.5),base],.0034,12)
    parts.push(makeThinLeaf(base,length,width,angle,variant*.9,false,variant))
  }
  lance(main,.25,.81,.77,.057,0);lance(main,.45,-.74,.65,.052,1);lance(main,.67,.68,.53,.044,0)
  lance(side,.56,-.70,.48,.039,1)
  const geometry=mergeGeometries(parts,false)!
  parts.forEach(part=>part.dispose());geometry.computeBoundingSphere();return geometry
}
