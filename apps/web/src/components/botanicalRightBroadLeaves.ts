import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'

type Margin = Array<[number, number]>
type Outline = { left: Margin; right: Margin; leftRibs: number[]; rightRibs: number[] }

// 右侧四片宽叶各自描出圆缓不等裂片；保留原叶柄、朝向和长宽，不复制左株轮廓。
const outlines: Outline[] = [
  {left:[[0,0],[-.17,.08],[-.43,.15],[-.67,.23],[-.75,.29],[-.72,.35],[-.57,.40],[-.40,.45],[-.56,.50],[-.78,.57],[-.89,.63],[-.83,.68],[-.64,.72],[-.42,.77],[-.27,.86],[-.11,.95],[0,1]],
    right:[[0,0],[.18,.09],[.40,.17],[.59,.26],[.64,.32],[.57,.39],[.43,.44],[.61,.50],[.79,.56],[.82,.62],[.66,.68],[.50,.72],[.59,.77],[.47,.83],[.25,.90],[.09,.965],[0,1]],leftRibs:[4,10,13],rightRibs:[4,9,12]},
  {left:[[0,0],[-.10,.085],[-.28,.15],[-.49,.23],[-.62,.29],[-.64,.34],[-.56,.39],[-.34,.435],[-.47,.48],[-.68,.555],[-.77,.62],[-.72,.67],[-.55,.715],[-.44,.77],[-.48,.815],[-.35,.865],[-.16,.94],[0,1]],
    right:[[0,0],[.19,.10],[.41,.18],[.62,.27],[.71,.33],[.68,.38],[.53,.42],[.40,.46],[.60,.52],[.80,.58],[.85,.63],[.78,.685],[.62,.735],[.42,.80],[.25,.865],[.08,.956],[0,1]],leftRibs:[4,10,14],rightRibs:[4,10,13]},
  {left:[[0,0],[-.10,.10],[-.30,.19],[-.46,.28],[-.55,.34],[-.53,.39],[-.40,.45],[-.30,.50],[-.48,.56],[-.68,.625],[-.72,.675],[-.62,.72],[-.45,.775],[-.24,.855],[-.09,.95],[0,1]],
    right:[[0,0],[.14,.09],[.36,.17],[.58,.25],[.67,.30],[.64,.355],[.47,.41],[.32,.47],[.48,.53],[.65,.59],[.72,.65],[.68,.705],[.52,.76],[.31,.84],[.11,.937],[0,1]],leftRibs:[4,10,12],rightRibs:[4,10,12]},
  {left:[[0,0],[-.13,.10],[-.35,.205],[-.58,.295],[-.66,.36],[-.61,.415],[-.43,.465],[-.36,.51],[-.55,.565],[-.76,.63],[-.80,.68],[-.64,.735],[-.43,.79],[-.24,.87],[-.08,.956],[0,1]],
    right:[[0,0],[.15,.10],[.37,.205],[.54,.305],[.60,.37],[.54,.43],[.37,.49],[.47,.545],[.64,.605],[.71,.675],[.66,.735],[.49,.79],[.30,.865],[.10,.95],[0,1]],leftRibs:[4,10,12],rightRibs:[4,9,11]},
]

/** 桌面右侧四片宽叶及手机少量宽叶共用的自然曲面；其他叶片继续使用各自生成器。 */
export function makeRightBroadLeaf(base: THREE.Vector3,length: number,width: number,angle: number,phase: number,variant: number) {
  const index=variant%outlines.length,outline=outlines[index]!,parts:THREE.BufferGeometry[]=[]
  const point=(x:number,y:number,z:number)=>new THREE.Vector3(x,y,z)
  const margin=(nodes:Margin)=>new THREE.CatmullRomCurve3(nodes.map(([x,y])=>point(x,y,0)),false,'centripetal')
  const left=margin(outline.left),right=margin(outline.right),c=Math.cos(angle),s=Math.sin(angle),bend=Math.sin(phase+index*.43)*.42
  const sideVeins=[...outline.leftRibs.map(end=>({side:-1,end:end/(outline.left.length-1)})),...outline.rightRibs.map(end=>({side:1,end:end/(outline.right.length-1)}))].map(({side,end},ribIndex)=>{
    const start=Math.max(.02,end-([.32,.31,.29,.27][index]!))
    return {side,end,start,branches:(ribIndex%2 ? [.30,.59] : [.38,.71]).map((branch,branchIndex)=>({
      branch,branchIndex,t0:start+(end-start)*branch,u0:ribU(branch),
      endT:Math.min(.98,end+(branchIndex===1 ? -.035 : .043+ribIndex%3*.011)),endU:branchIndex===0 ? .72 : .90,
    }))}
  })
  // 沿用已验收的弯曲脉路与平滑收束：交汇用权重融合，避免v18式层叠横脊。
  function ribU(t:number){return t*(.24+t*(1.23-.51*t))}
  function smoothRange(lo:number,hi:number,value:number){const q=THREE.MathUtils.clamp((value-lo)/(hi-lo),0,1);return q*q*(3-2*q)}
  function surface(t:number,u:number) {
    const l=left.getPoint(t),r=right.getPoint(t),edge=u<0 ? l : r,a=Math.abs(u)
    const centerY=(l.y+r.y)*.5,centerX=width*(.11*bend*Math.sin(Math.PI*t)-(.024+index*.004)*Math.sin(2*Math.PI*t))
    const x=centerX+edge.x*width*a,y=(centerY+(edge.y-centerY)*a)*length,envelope=Math.sin(Math.PI*t)
    let fold=0,weightSum=0
    for(const [ribIndex,vein]of sideVeins.entries()) {
      const {side,start,end}=vein,progress=(t-start)/(end-start)
      const along=smoothRange(start-.06,start+.10,t)*(1-smoothRange(end-.06,end+.09,t))
      const across=smoothRange(0,.20,u*side)*(1-smoothRange(.72,1,a)),distance=u-side*ribU(THREE.MathUtils.clamp(progress,0,1)),weight=along*across
      const foldHeight=[.024,.030,.021,.018][index]!
      fold+=(Math.exp(-distance*distance/.11)-.25*Math.exp(-distance*distance/.32))*weight*(foldHeight+ribIndex%3*.004)*length
      weightSum+=Math.exp(-distance*distance/.28)*weight
      for(const fork of vein.branches) {
        const q=(t-fork.t0)/(fork.endT-fork.t0)
        if(q<=0||q>=1)continue
        const branchU=side*(fork.u0+(fork.endU-fork.u0)*Math.sin(q*Math.PI*.5)),d=u-branchU
        fold+=Math.exp(-d*d/.04)*Math.pow(Math.sin(q*Math.PI),2)*across*(index===3 ? .0018 : .0025)*length
      }
    }
    fold/=1+.6*weightSum
    // 连续宽缓拱面延伸至薄边；翻卷按各叶姿态轻微变化，不挤出侧墙或添加锯齿噪声。
    const edgeTurn=envelope*length*[.006,.009,.005,.006][index]!*u*a*a*Math.sin(t*5+bend*3)
    const z=base.z+envelope*(length*[.029,.028,.025,.022][index]!*Math.pow(1-u*u,1.15)+length*.0035*(1+u*bend*.5))+.003*t+fold*(1-u*u)+edgeTurn
    return point(base.x+x*c-y*s,base.y+x*s+y*c,z)
  }
  function mesh(positions:number[],uv:number[],indices:number[]) {
    const geometry=new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2));geometry.setIndex(indices);geometry.computeVertexNormals();parts.push(geometry)
  }
  function rib(sample:(t:number)=>THREE.Vector3,startWidth:number,segments:number) {
    const positions:number[]=[],uv:number[]=[],indices:number[]=[],sides=8
    for(let i=0;i<=segments;i++) {
      const t=i/segments,p=sample(t),tangent=sample(Math.min(1,t+.005)).sub(sample(Math.max(0,t-.005))).normalize()
      const across=point(-tangent.y,tangent.x,0).normalize(),radius=startWidth*Math.pow(1-t,.75)+.0004
      // 扁脉下半部埋入实际曲面，末梢渐细；不另贴同宽的直线描边。
      for(let j=0;j<=sides;j++) {const a=j/sides*Math.PI*2,v=p.clone().addScaledVector(across,Math.cos(a)*radius);v.z+=radius*(.12+.42*Math.sin(a));positions.push(...v.toArray());uv.push(j/sides,t)}
    }
    for(let i=0;i<segments;i++)for(let j=0;j<sides;j++){const a=i*(sides+1)+j,b=a+sides+1;indices.push(a,b,a+1,a+1,b,b+1)}
    mesh(positions,uv,indices)
  }
  const rows=112,columns=30,positions:number[]=[],uv:number[]=[],indices:number[]=[]
  for(let i=0;i<=rows;i++)for(let j=0;j<=columns;j++){positions.push(...surface(i/rows,j/columns*2-1).toArray());uv.push(j/columns,i/rows)}
  for(let i=0;i<rows;i++)for(let j=0;j<columns;j++){const a=i*(columns+1)+j,b=a+columns+1;indices.push(a,a+1,b,a+1,b+1,b)}
  mesh(positions,uv,indices)
  rib(t=>surface(t,0),[.007,.0075,.0063,.0056][index]!,36)
  for(const [ribIndex,vein]of sideVeins.entries()) {
    const {side,end,start}=vein
    rib(t=>surface(start+(end-start)*t,side*ribU(t)),.0045*(.94-end*.26),30)
    for(const {branchIndex,branch,t0,u0,endT,endU}of vein.branches) {
      const path=(t:number)=>surface(t0+(endT-t0)*t,side*(u0+(endU-u0)*Math.sin(t*Math.PI*.5)))
      rib(path,.0019*(1-branch*.35),22)
      if(branchIndex!==0)continue
      const fork=.48+ribIndex%3*.055,forkT=t0+(endT-t0)*fork,forkU=u0+(endU-u0)*Math.sin(fork*Math.PI*.5)
      const tipT=Math.min(.98,endT+.027)
      rib(t=>surface(forkT+(tipT-forkT)*t,side*(forkU+(.86-forkU)*Math.sin(t*Math.PI*.5))),.0010,16)
    }
  }
  const geometry=mergeGeometries(parts,false)!
  parts.forEach(part=>part.dispose());geometry.computeBoundingSphere();return geometry
}
