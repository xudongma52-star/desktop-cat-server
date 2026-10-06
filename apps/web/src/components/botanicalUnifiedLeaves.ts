import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'

type Margin = Array<[number, number]>
type Outline = { left: Margin; right: Margin; leftRibs: number[]; rightRibs: number[] }

// 右侧裂叶另外描三组不等裂片；与已验收的左侧单株共享薄度标准，不复制或镜像其轮廓。
const lobes: Outline[] = [
  {left:[[0,0],[-.22,.12],[-.61,.21],[-.76,.30],[-.65,.39],[-.45,.44],[-.78,.52],[-.93,.63],[-.67,.71],[-.49,.78],[-.31,.88],[0,1]],
    right:[[0,0],[.18,.10],[.49,.20],[.65,.32],[.51,.41],[.83,.51],[.86,.58],[.58,.66],[.68,.77],[.35,.85],[.16,.95],[0,1]],leftRibs:[3,7,9],rightRibs:[3,6,8]},
  {left:[[0,0],[-.12,.09],[-.47,.18],[-.64,.28],[-.60,.37],[-.35,.43],[-.71,.53],[-.78,.60],[-.51,.70],[-.58,.78],[-.30,.90],[0,1]],
    right:[[0,0],[.23,.13],[.62,.25],[.73,.35],[.62,.40],[.43,.45],[.88,.57],[.80,.66],[.51,.74],[.37,.85],[.13,.95],[0,1]],leftRibs:[3,7,9],rightRibs:[3,6,9]},
  {left:[[0,0],[-.16,.11],[-.49,.23],[-.56,.31],[-.32,.42],[-.70,.54],[-.74,.63],[-.45,.72],[-.28,.84],[-.14,.94],[0,1]],
    right:[[0,0],[.18,.12],[.51,.20],[.70,.29],[.58,.38],[.36,.43],[.67,.54],[.75,.65],[.50,.76],[.25,.90],[0,1]],leftRibs:[3,6,8],rightRibs:[3,7,9]},
]
const blades: Outline[] = [
  {left:[[0,0],[-.20,.12],[-.63,.29],[-1,.47],[-.76,.69],[-.35,.87],[0,1]],right:[[0,0],[.21,.10],[.64,.31],[.91,.53],[.67,.75],[.25,.91],[0,1]],leftRibs:[2,3,4],rightRibs:[2,3,4]},
  {left:[[0,0],[-.16,.09],[-.61,.25],[-.89,.43],[-.79,.63],[-.43,.84],[0,1]],right:[[0,0],[.26,.14],[.78,.37],[1,.57],[.58,.78],[.19,.92],[0,1]],leftRibs:[2,3,4],rightRibs:[2,3,4]},
]

/** 桌面真叶及手机少量细叶共用的薄曲面。花簇、种穗和蕨叶仍由各自生成器负责。 */
export function makeThinLeaf(base: THREE.Vector3,length: number,width: number,angle: number,phase: number,lobed=false,variant=0) {
  const outline=(lobed ? lobes : blades)[variant%(lobed ? lobes.length : blades.length)]!
  const point=(x:number,y:number,z:number)=>new THREE.Vector3(x,y,z)
  const margin=(nodes:Margin)=>new THREE.CatmullRomCurve3(nodes.map(([x,y])=>point(x,y,0)),false,'centripetal')
  const left=margin(outline.left),right=margin(outline.right),c=Math.cos(angle),s=Math.sin(angle),bend=Math.sin(phase)*.34
  const veins=[...outline.leftRibs.map(end=>({side:-1,end:end/(outline.left.length-1)})),...outline.rightRibs.map(end=>({side:1,end:end/(outline.right.length-1)}))]
  const startOf=(end:number)=>Math.max(.04,end-(lobed ? .17 : .13))
  function surface(t:number,u:number) {
    const l=left.getPoint(t),r=right.getPoint(t),edge=u<0 ? l : r,a=Math.abs(u)
    const x=Math.sin(Math.PI*t)*width*.08*bend+edge.x*width*a
    const centerY=(l.y+r.y)*.5,y=(centerY+(edge.y-centerY)*a)*length
    let fold=0
    if(lobed)for(const vein of veins) {
      const start=startOf(vein.end),progress=(t-start)/(vein.end-start)
      if(progress<=0||progress>=1)continue
      const distance=u-vein.side*Math.sin(progress*Math.PI*.5)*.97
      fold+=Math.exp(-distance*distance/.018)*Math.sin(progress*Math.PI)*.0045*length*(1-u*u)
    }
    // 与左侧试件相同的薄边和低拱度；没有侧墙，静止及抬起时都由真实曲面参与阴影。
    // 静态校准只恢复中脉附近的连续拱度；u=±1 时该项严格为零，叶缘不加厚、不挤出侧墙。
    const z=base.z+Math.sin(Math.PI*t)*(length*.036*Math.pow(1-u*u,2)+length*.0035*(1+u*bend*.5))+.003*t+fold
    return point(base.x+x*c-y*s,base.y+x*s+y*c,z)
  }
  const parts:THREE.BufferGeometry[]=[]
  function mesh(positions:number[],uv:number[],indices:number[]) {
    const geometry=new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2))
    geometry.setIndex(indices);geometry.computeVertexNormals();parts.push(geometry)
  }
  const rows=lobed ? 100 : 32,columns=lobed ? 30 : 10,positions:number[]=[],uv:number[]=[],indices:number[]=[]
  for(let i=0;i<=rows;i++)for(let j=0;j<=columns;j++){positions.push(...surface(i/rows,j/columns*2-1).toArray());uv.push(j/columns,i/rows)}
  for(let i=0;i<rows;i++)for(let j=0;j<columns;j++){const a=i*(columns+1)+j,b=a+columns+1;indices.push(a,a+1,b,a+1,b+1,b)}
  mesh(positions,uv,indices)
  function rib(sample:(t:number)=>THREE.Vector3,width:number,segments:number) {
    const positions:number[]=[],uv:number[]=[],indices:number[]=[],sides=6
    for(let i=0;i<=segments;i++) {
      const t=i/segments,p=sample(t),tangent=sample(Math.min(1,t+.005)).sub(sample(Math.max(0,t-.005))).normalize()
      const across=point(-tangent.y,tangent.x,0).normalize(),radius=width*Math.pow(1-t,.75)+.0003
      for(let j=0;j<=sides;j++) {
        const angle=j/sides*Math.PI*2,v=p.clone().addScaledVector(across,Math.cos(angle)*radius)
        v.z+=radius*(.12+.42*Math.sin(angle));positions.push(...v.toArray());uv.push(j/sides,t)
      }
    }
    for(let i=0;i<segments;i++)for(let j=0;j<sides;j++){const a=i*(sides+1)+j,b=a+sides+1;indices.push(a,b,a+1,a+1,b,b+1)}
    mesh(positions,uv,indices)
  }
  rib(t=>surface(t,0),lobed ? .008 : Math.min(.0032,length*.0075),lobed ? 36 : 24)
  for(const [index,vein]of veins.entries()) {
    const {side,end}=vein,start=startOf(end)
    rib(t=>surface(start+(end-start)*t,side*Math.sin(t*Math.PI*.5)*.97),lobed ? .0045 : Math.min(.0015,length*.0038),lobed ? 30 : 14)
    if(!lobed)continue
    for(const [n,fork]of (index%2 ? [.31,.66] : [.40,.75]).entries()) {
      const t0=start+(end-start)*fork,u0=Math.sin(fork*Math.PI*.5)*.97,endT=Math.min(.97,end+(n ? -.025 : .049))
      rib(t=>surface(t0+(endT-t0)*t,side*(u0+(.96-u0)*Math.sin(t*Math.PI*.5))),.0016,18)
    }
  }
  const geometry=mergeGeometries(parts,false)!
  parts.forEach(part=>part.dispose());geometry.computeBoundingSphere();return geometry
}
