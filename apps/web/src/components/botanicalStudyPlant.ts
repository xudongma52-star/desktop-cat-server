import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'

type LeafShape = 'broad' | 'lower' | 'upper' | 'tip' | 'small'
type Margin = Array<[number, number]>

// 单株试件的叶缘和侧脉端点一起定义。四片大叶采用各自的圆缓不等裂片，避免周期锯齿与随机缺口。
const outlines: Record<LeafShape, { left: Margin; right: Margin; leftRibs: number[]; rightRibs: number[] }> = {
  broad: {
    left: [[0,0],[-.13,.075],[-.35,.135],[-.58,.19],[-.72,.235],[-.74,.285],[-.63,.34],[-.39,.375],[-.49,.405],[-.73,.445],[-.96,.515],[-1,.58],[-.91,.64],[-.66,.66],[-.45,.705],[-.58,.745],[-.71,.80],[-.66,.845],[-.48,.875],[-.32,.917],[-.17,.965],[0,1]],
    right: [[0,0],[.14,.085],[.35,.145],[.56,.215],[.72,.26],[.74,.31],[.58,.36],[.40,.395],[.65,.455],[.87,.535],[.85,.59],[.74,.625],[.52,.665],[.36,.704],[.54,.754],[.58,.802],[.46,.85],[.28,.91],[.13,.963],[0,1]],
    leftRibs: [4,11,16], rightRibs: [4,9,15],
  },
  lower: {
    left: [[0,0],[-.18,.055],[-.47,.12],[-.73,.205],[-.88,.29],[-.85,.36],[-.66,.405],[-.46,.445],[-.58,.49],[-.79,.55],[-.86,.615],[-.76,.67],[-.52,.715],[-.38,.755],[-.44,.80],[-.38,.855],[-.19,.922],[-.07,.975],[0,1]],
    right: [[0,0],[.20,.09],[.46,.175],[.65,.26],[.72,.335],[.66,.405],[.43,.47],[.57,.525],[.77,.595],[.80,.66],[.66,.73],[.43,.775],[.27,.855],[.13,.93],[.04,.981],[0,1]],
    leftRibs: [4,10,15], rightRibs: [4,9,12],
  },
  upper: {
    left: [[0,0],[-.08,.095],[-.29,.19],[-.47,.275],[-.61,.36],[-.63,.42],[-.52,.475],[-.34,.515],[-.48,.575],[-.67,.64],[-.65,.705],[-.49,.76],[-.31,.805],[-.18,.89],[-.07,.96],[0,1]],
    right: [[0,0],[.10,.09],[.28,.20],[.43,.29],[.51,.37],[.50,.435],[.32,.49],[.41,.55],[.57,.61],[.59,.685],[.46,.75],[.25,.825],[.12,.917],[.04,.973],[0,1]],
    leftRibs: [4,9,12], rightRibs: [4,9,11],
  },
  // 顶部叶片保留窄长姿态与浅裂片，不按宽主叶等比复制。
  tip: {
    left: [[0,0],[-.06,.10],[-.22,.205],[-.40,.315],[-.52,.41],[-.49,.475],[-.32,.525],[-.40,.59],[-.47,.67],[-.41,.725],[-.25,.78],[-.15,.87],[-.055,.96],[0,1]],
    right: [[0,0],[.08,.11],[.23,.235],[.38,.35],[.44,.43],[.39,.49],[.27,.54],[.35,.60],[.41,.66],[.34,.735],[.21,.805],[.10,.90],[.03,.972],[0,1]],
    leftRibs: [4,8,10], rightRibs: [4,8,10],
  },
  small: {
    left: [[0,0],[-.43,.20],[-1,.46],[-.67,.71],[-.12,.94],[0,1]],
    right: [[0,0],[.45,.17],[.89,.44],[.55,.69],[.16,.90],[0,1]],
    leftRibs: [], rightRibs: [],
  },
}

/** 左侧单株试件；主叶模板保持，另三片按各自姿态统一圆缓叶缘与连续曲面，沿用真实几何及外层深度/阴影交互。 */
export function makeStudyPlant() {
  const parts: THREE.BufferGeometry[] = []
  const point=(x: number,y: number,z: number)=>new THREE.Vector3(x,y,z)
  function twig(points: THREE.Vector3[],radius: number,segments=24) {
    const curve=new THREE.CatmullRomCurve3(points)
    parts.push(new THREE.TubeGeometry(curve,segments,radius,6,false))
    return curve
  }
  function addSurface(positions: number[],uv: number[],indices: number[]) {
    const geometry=new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3))
    geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2))
    geometry.setIndex(indices);geometry.computeVertexNormals();parts.push(geometry)
  }
  function rib(sample: (t: number)=>THREE.Vector3,startWidth: number,segments=24) {
    const positions:number[]=[],uv:number[]=[],indices:number[]=[],sides=8
    for(let i=0;i<=segments;i++) {
      const t=i/segments,p=sample(t)
      const tangent=sample(Math.min(1,t+.005)).sub(sample(Math.max(0,t-.005))).normalize()
      const across=point(-tangent.y,tangent.x,0).normalize()
      const radius=startWidth*Math.pow(1-t,.75)+.0004
      // 叶脉是沿曲面逐渐变细的扁隆起；下半部埋入叶面，端部自然消失，不另贴直线管。
      for(let j=0;j<=sides;j++) {
        const angle=j/sides*Math.PI*2
        const v=p.clone().addScaledVector(across,Math.cos(angle)*radius)
        v.z+=radius*(.12+.42*Math.sin(angle))
        positions.push(...v.toArray());uv.push(j/sides,t)
      }
    }
    for(let i=0;i<segments;i++)for(let j=0;j<sides;j++) {
      const a=i*(sides+1)+j,b=a+sides+1;indices.push(a,b,a+1,a+1,b,b+1)
    }
    addSurface(positions,uv,indices)
  }
  function blade(base: THREE.Vector3,length: number,width: number,angle: number,shape: LeafShape,bend: number) {
    const outline=outlines[shape]
    const main=shape==='broad'
    const soft=shape!=='small'
    const margin=(nodes: Margin)=>new THREE.CatmullRomCurve3(nodes.map(([x,y])=>point(x,y,0)),false,'centripetal')
    const left=margin(outline.left),right=margin(outline.right),c=Math.cos(angle),s=Math.sin(angle)
    const majorRibs=[...outline.leftRibs.map(endpoint=>({side:-1,end:endpoint/(outline.left.length-1)})),
      ...outline.rightRibs.map(endpoint=>({side:1,end:endpoint/(outline.right.length-1)}))]
    // 叶面起伏与实际侧脉路径共用参数，隆起只在脉络附近延展、向边缘消失，不叠加全叶噪声。
    const ribStart=(end: number)=>Math.max(.02,end-(main ? .31 : shape==='tip' ? .27 : soft ? .32 : .23))
    // 大叶侧脉先顺着中脉前行，再弯向各自裂片；同一路径用于叶脉和宽缓曲面起伏。
    const ribU=(t: number)=>soft ? t*(.24+t*(1.23-.51*t)) : Math.sin(t*Math.PI*.5)*.98
    // 主脉分叉同时驱动曲面褶皱和细脉几何，避免在光滑叶面上另贴不相关线条。
    const sideVeins=majorRibs.map(({side,end},index)=>{
      const start=ribStart(end)
      const branches=soft ? (index%2 ? [.30,.59] : [.38,.71]) : (index%2 ? [.29,.61,.82] : [.36,.70])
      return {side,end,start,branches:branches.map((branch,branchIndex)=>({
        branch,branchIndex,t0:start+(end-start)*branch,u0:ribU(branch),
        endT:Math.min(.98,end+(branchIndex===1 ? -.035 : .043+index%3*.011)),endU:soft ? (branchIndex===0 ? .72 : .90) : (branchIndex===0 ? .90 : .97),
      }))}
    })
    const smoothRange=(lo: number,hi: number,value: number)=>{
      const q=THREE.MathUtils.clamp((value-lo)/(hi-lo),0,1)
      return q*q*(3-2*q)
    }
    function surface(t: number,u: number) {
      const l=left.getPoint(t),r=right.getPoint(t),edge=u<0 ? l : r,a=Math.abs(u)
      const centerY=(l.y+r.y)*.5
      const centerX=main ? width*(.12*Math.sin(Math.PI*t)-.055*Math.sin(2*Math.PI*t)) : soft ? width*(.11*bend*Math.sin(Math.PI*t)-.03*Math.sin(2*Math.PI*t)) : Math.sin(Math.PI*t)*width*.08*bend
      const x=centerX+edge.x*width*a
      const y=(centerY+(edge.y-centerY)*a)*length
      // 无侧壁、无厚度挤出。中脉附近略隆起，向叶缘渐薄；小叶的曲率按自身长度缩小。
      const envelope=Math.sin(Math.PI*t)
      let ribFold=0,mainFold=0,mainWeight=0
      for(const [index,vein] of sideVeins.entries()) {
        const {side,start,end}=vein,progress=(t-start)/(end-start)
        if(soft) {
          // 大叶起伏向中脉和叶缘平缓收束，起止过渡的坡度归零；邻脉交汇用权重融合，避免叠成尖包。
          const along=smoothRange(start-.06,start+.10,t)*(1-smoothRange(end-.06,end+.09,t))
          const across=smoothRange(0,.20,u*side)*(1-smoothRange(.72,1,a))
          const distance=u-side*ribU(THREE.MathUtils.clamp(progress,0,1))
          const weight=along*across
          const foldHeight=main ? .030 : shape==='lower' ? .027 : shape==='upper' ? .022 : .018
          mainFold+=(Math.exp(-distance*distance/.11)-.25*Math.exp(-distance*distance/.32))*weight*(foldHeight+index%3*.004)*length
          mainWeight+=Math.exp(-distance*distance/.28)*weight
          for(const fork of vein.branches) {
            const q=(t-fork.t0)/(fork.endT-fork.t0)
            if(q<=0||q>=1)continue
            const branchU=side*(fork.u0+(fork.endU-fork.u0)*Math.sin(q*Math.PI*.5)),d=u-branchU
            mainFold+=Math.exp(-d*d/.04)*Math.pow(Math.sin(q*Math.PI),2)*across*(main ? .0035 : shape==='tip' ? .0018 : .0025)*length
          }
          continue
        }
        const distance=u-side*ribU(progress)
        // 侧脉两旁是宽缓凹折、脉线上是较窄隆起；各裂片幅度略异，根部与末端连续归零。
        if(progress>0&&progress<1)ribFold+=(Math.exp(-distance*distance/.007)-.42*Math.exp(-distance*distance/.05))*Math.sin(progress*Math.PI)*(.009+index%3*.0012)*length
        for(const fork of vein.branches) {
          const q=(t-fork.t0)/(fork.endT-fork.t0)
          if(q<=0||q>=1)continue
          const branchU=side*(fork.u0+(fork.endU-fork.u0)*Math.sin(q*Math.PI*.5)),d=u-branchU
          ribFold+=Math.exp(-d*d/.0035)*Math.sin(q*Math.PI)*.0032*length
        }
      }
      if(soft)ribFold=mainFold/(1+.6*mainWeight)
      // 保留薄叶缘与连续拱度；大叶的宽缓拱面延伸至边缘，边界仍不增加厚度。
      // 主叶参数冻结，其余大叶按大小降低翻折幅度；小叶保持原参数，不增加侧墙或随机麻点。
      const edgeTurn=shape==='small' ? 0 : envelope*length*(main ? .012 : shape==='lower' ? .008 : shape==='upper' ? .007 : .005)*u*a*a*Math.sin(t*5+bend*3)
      const arch=main ? .028 : shape==='lower' ? .034 : shape==='upper' ? .024 : shape==='tip' ? .020 : .036
      const z=base.z+envelope*(length*arch*Math.pow(1-u*u,soft ? 1.15 : 2)+length*.0035*(1+u*bend*.5))+.003*t+ribFold*(1-u*u)+edgeTurn
      return point(base.x+x*c-y*s,base.y+x*s+y*c,z)
    }
    const rows=shape==='small' ? 28 : 112,columns=shape==='small' ? 8 : 30
    const positions:number[]=[],uv:number[]=[],indices:number[]=[]
    for(let i=0;i<=rows;i++)for(let j=0;j<=columns;j++) {
      positions.push(...surface(i/rows,j/columns*2-1).toArray());uv.push(j/columns,i/rows)
    }
    for(let i=0;i<rows;i++)for(let j=0;j<columns;j++) {
      const a=i*(columns+1)+j,b=a+columns+1;indices.push(a,a+1,b,a+1,b+1,b)
    }
    addSurface(positions,uv,indices)
    rib(t=>surface(t,0),shape==='small' ? .0026 : .008,36)
    if(shape==='small') {
      for(const side of [-1,1])for(const end of [.38,.62,.78]) {
        rib(t=>surface(end-.15+t*.15,side*Math.sin(t*Math.PI*.5)*.92),.0014,14)
      }
      return
    }
    for(const [index,vein] of sideVeins.entries()) {
        const {side,end,start}=vein
        const path=(t: number)=>surface(start+(end-start)*t,side*ribU(t))
        rib(path,.0045*(.94-end*.26),30)
        // 每条侧脉仅有少量非等距支脉。长短及前后朝向不同，末梢继续分级变浅，保持全景安静。
        for(const {branchIndex,branch,t0,u0,endT,endU} of vein.branches) {
          const branchPath=(t: number)=>surface(t0+(endT-t0)*t,side*(u0+(endU-u0)*Math.sin(t*Math.PI*.5)))
          rib(branchPath,.0019*(1-branch*.35),22)
          if(branchIndex!==0)continue
          const fork=.48+index%3*.055,forkT=t0+(endT-t0)*fork
          const forkU=u0+(endU-u0)*Math.sin(fork*Math.PI*.5)
          // 大叶细脉停在裂片内，避免多条末梢沿叶缘形成连续描边。
          const tipT=Math.min(.98,endT+.027),tipU=soft ? .86 : .96
          rib(t=>surface(forkT+(tipT-forkT)*t,side*(forkU+(tipU-forkU)*Math.sin(t*Math.PI*.5))),.0010,16)
        }
    }
  }
  const x=-4.30,height=3.55,lean=.86
  const stem=twig([point(x,-4.55,.05),point(x+lean*.12,-4.2+height*.30,.055),point(x+lean*.66,-4.25+height*.7,.045),point(x+lean,-4.1+height,.032)],.0095,56)
  const leaves=[
    {t:.20,l:1.40,w:.43,a:.90,shape:'lower' as const,bend:-.35},
    {t:.41,l:1.65,w:.70,a:-.87,shape:'broad' as const,bend:.45},
    {t:.64,l:1.42,w:.49,a:.58,shape:'upper' as const,bend:-.22},
    {t:.82,l:1.28,w:.41,a:-.30,shape:'tip' as const,bend:.30},
  ]
  for(const leaf of leaves) {
    const p=stem.getPoint(leaf.t),side=leaf.a<0 ? 1 : -1
    const base=p.clone().add(point(side*.20,.24,0));base.z=.011
    twig([p,p.clone().lerp(base,.58),base],.0048,18)
    blade(base,leaf.l,leaf.w,leaf.a,leaf.shape,leaf.bend)
  }
  // 两组有限的次级枝作为连接试件；叶柄接至中脉，薄尖端与叶面连贯，避免米粒状芽头。
  for(const spec of [{t:.34,dx:.42,dy:.52,bend:.35},{t:.72,dx:-.38,dy:.46,bend:-.3}]) {
    const root=stem.getPoint(spec.t),tip=root.clone().add(point(spec.dx,spec.dy,0));tip.z=.015
    const branch=twig([root,root.clone().lerp(tip,.5),tip],.003,24)
    for(const [index,t] of [.42,.79,1].entries()) {
      const p=branch.getPoint(t),side=index%2 ? -1 : 1
      const direction=-Math.atan2(spec.dx,spec.dy)+side*(index===2 ? .12 : .64)
      const base=p.clone().add(point(-Math.sin(direction)*.055,Math.cos(direction)*.055,0));base.z=.010+index*.002
      twig([p,p.clone().lerp(base,.55),base],.0018,10)
      blade(base,.31+index*.04,.069+index*.005,direction,'small',spec.bend)
    }
  }
  const geometry=mergeGeometries(parts,false)!
  parts.forEach(part=>part.dispose());geometry.computeBoundingSphere()
  return geometry
}
