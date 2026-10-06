import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'
import { makeStudyPlant } from './botanicalStudyPlant'
import { makeThinLeaf } from './botanicalUnifiedLeaves'
import { makeMainUmbel } from './botanicalMainUmbel'
import { makeSecondaryUmbel } from './botanicalSecondaryUmbel'
import { makeRightBroadLeaf } from './botanicalRightBroadLeaves'
import { makeLowHeartLeaves } from './botanicalLowHeartLeaves'
import { makeCentralFlowerSprig } from './botanicalCentralFlowerSprig'
import { makeTrifoliateUnit } from './botanicalTrifoliate'
import { makePalmLeaf } from './botanicalPalmLeaf'

/** 自制草木曲面：叶缘、叶脉和花梗都是几何；没有采样付费模型或参考站资产。 */
export function makeBotanicalRelief(mobile: boolean) {
  const parts: THREE.BufferGeometry[] = []
  let seed = 417
  const random = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296 }
  const point = (x: number, y: number, z = .075) => new THREE.Vector3(x, y, z)
  function shallowAccent(start:number) {
    // 静态基础深度提高后，原高鼓包的花/穗/蕨整组只沿 z 压浅；共享接点一起变换，轮廓与拓扑不变。
    if(!mobile)for(const geometry of parts.slice(start))geometry.scale(1,1,.70)
  }
  function twig(points: THREE.Vector3[], radius: number, segments = 20) {
    const curve = new THREE.CatmullRomCurve3(points)
    const geometry = new THREE.TubeGeometry(curve, mobile ? Math.max(5, segments >> 1) : segments, radius, mobile ? 4 : 5, false)
    parts.push(geometry)
    return curve
  }
  function leaf(base: THREE.Vector3, length: number, width: number, angle: number, phase: number, lobed = false, veins = true, feather = false) {
    const rows = mobile ? 12 : 22, columns = mobile ? 4 : 6
    const positions: number[] = [], uv: number[] = [], indices: number[] = []
    const c = Math.cos(angle), s = Math.sin(angle)
    const local = (x: number, y: number, z: number) => point(base.x + x*c - y*s, base.y + x*s + y*c, base.z + z)
    const center = (t: number) => Math.sin(Math.PI*t) * length*.11 * Math.sin(phase)
    for (let i = 0; i <= rows; i++) {
      const t = i / rows
      // 非对称叶缘、细小锯齿与中脉隆起；不同叶片有独立的弯曲和翻卷。
      const silhouette = Math.pow(Math.sin(Math.PI*t), .72) * (lobed ? (feather ? .94 + .06*Math.cos(t*12*Math.PI) : .76 + .24*Math.cos(t*12*Math.PI)) : 1 + .035*Math.sin(t*32*Math.PI))
      for (let j = 0; j <= columns; j++) {
        const across = j/columns*2-1
        const x = center(t) + across*width*silhouette*(across < 0 ? .95 : 1.05)
        const z = feather ? Math.sin(Math.PI*t)*(.024+.034*(1-across*across))+.008*across*Math.sin(t*5+phase) : Math.sin(Math.PI*t)*(.08 + .105*(1-across*across)) + .035*across*Math.sin(t*5 + phase)
        positions.push(...local(x, t*length, z).toArray()); uv.push(j/columns,t)
      }
    }
    for (let i = 0; i < rows; i++) for (let j = 0; j < columns; j++) {
      const a=i*(columns+1)+j,b=a+columns+1
      indices.push(a,a+1,b,a+1,b+1,b)
    }
    const geometry = new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3))
    geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2)); geometry.setIndex(indices); geometry.computeVertexNormals(); parts.push(geometry)
    twig([local(0,0,.009),local(center(.5),length*.5,feather ? .062 : .197),local(0,length,.013)], feather ? .003 : .005, 14)
    if (veins) for (let i=1;i<7;i++) for (const side of [-1,1]) {
      const t=i/8, end=Math.min(.98,t+.15)
      const edge=Math.pow(Math.sin(Math.PI*end),.72)*width*side*.9
      twig([local(center(t),t*length,.19*Math.sin(Math.PI*t)+.01),local(center(t)+edge*.5,(t+.07)*length,.17*Math.sin(Math.PI*t)+.014),local(center(end)+edge,end*length,.09*Math.sin(Math.PI*end)+.01)], .0028, 6)
    }
  }
  function fern(x: number, height: number, lean: number, phase: number, pose?: { scale: [number,number]; angle: number; offset: [number,number] }) {
    const start=parts.length
    // 桌面两株退到较浅的辅助层：只降低羽片鼓包、茎高和齿幅，手机保留既有轮廓与深度。
    const curve = twig([point(x,-4.55,.05),point(x+lean*.35,-4.1+height*.35,mobile ? .11 : .055),point(x+lean*.8,-4.35+height*.73,mobile ? .13 : .067),point(x+lean,-4.3+height,mobile ? .075 : .045)],mobile ? .015 : .009,48)
    const pairs=mobile ? 12 : 17
    for (let i=1;i<pairs;i++) {
      const t=i/pairs, base=curve.getPoint(t), taper=Math.pow(Math.sin(Math.PI*t),.8)
      for (const side of [-1,1]) leaf(base,(.12+taper*.58)*(.93+random()*.14),(.035+taper*.052)*(.92+random()*.12),-side*(1.02+.22*t+.09*Math.sin(i*1.8+phase)),phase+i*.8,true,false,!mobile)
    }
    shallowAccent(start)
    if(pose) {
      // 桌面蕨叶按完整实例退到底部辅助层；绕根部变换全部羽片、脉线和主轴，不重建叶片或改变随机序列。
      const transform=new THREE.Matrix4().makeTranslation(x+pose.offset[0],-4.55+pose.offset[1],0)
        .multiply(new THREE.Matrix4().makeRotationZ(pose.angle))
        .multiply(new THREE.Matrix4().makeScale(pose.scale[0],pose.scale[1],1))
        .multiply(new THREE.Matrix4().makeTranslation(-x,4.55,0))
      for(const geometry of parts.slice(start))geometry.applyMatrix4(transform)
    }
  }
  function thinLeaf(base: THREE.Vector3,length: number,width: number,angle: number,phase: number,lobed=false,variant=0,continuous=false) {
    // 固定枝条附着点和叶尖的平面位置；左侧弯茎的叶柄延长并沿接点高度过渡，其余叶片保留原低薄叶面。
    const petiole=continuous ? Math.min(.14,length*.22) : Math.min(.07,length*.16)
    const direction=point(-Math.sin(angle),Math.cos(angle),0)
    const bladeBase=base.clone().addScaledVector(direction,petiole);bladeBase.z=continuous ? base.z-.004 : .011
    if(continuous) {
      // 叶柄末端沿中脉方向嵌入叶面；接点不再骤降到固定z，显露抬起时保持连续，不改变叶片厚度模板。
      const shoulder=base.clone().addScaledVector(direction,petiole*.35)
      const neck=bladeBase.clone().addScaledVector(direction,-petiole*.20)
      const overlap=bladeBase.clone().addScaledVector(direction,.018)
      gradedTwig([base,shoulder,neck,overlap],Math.min(.005,length*.009),.002,20)
    } else twig([base,base.clone().lerp(bladeBase,.5),bladeBase],Math.min(.0035,length*.006),14)
    parts.push(makeThinLeaf(bladeBase,length-petiole,width,angle,phase,lobed,variant))
  }
  function broad(x: number, height: number, lean: number, phase: number, bowDegrees=0) {
    const stemPoints=[point(x,-4.55,.065),point(x+lean*.2,-4.45+height*.3,.07),point(x+lean*.62,-4.3+height*.67,.075),point(x+lean,-4.2+height,.055)]
    const originalStem=bowDegrees ? new THREE.CatmullRomCurve3(stemPoints.map(p=>p.clone())) : undefined
    // 宽缓弧线的根和顶端固定；沿高度增加侧偏，角度描述弓形的尺度，各株使用不同幅度而非同一轮廓。
    if(bowDegrees)for(const p of stemPoints) {
      const t=(p.y+4.55)/(height+.35)
      p.x-=Math.sin(Math.PI*t)*(height+.35)*Math.tan(THREE.MathUtils.degToRad(bowDegrees))/Math.PI
    }
    const stem=twig(stemPoints,.017,50)
    for (let i=1;i<=7;i++) {
      const t=i/8, p=stem.getPoint(t), side=i%2 ? -1 : 1
      const length=(.63+random()*.48)*(1-t*.35)
      const before=originalStem?.getTangent(t),after=stem.getTangent(t)
      const turn=before ? Math.atan2(after.y,after.x)-Math.atan2(before.y,before.x) : 0
      const angle=-side*(.74+random()*.45)+turn
      const offset=point(side*.09,.05,bowDegrees ? -.004 : -.06).applyAxisAngle(point(0,0,1),turn)
      const b=p.clone().add(offset)
      const leafStart=parts.length
      twig([p,p.clone().lerp(b,.55),b],.008,6)
      const width=length*(.17+random()*.10)
      if(mobile)leaf(b,length,width,angle,phase+i*.7,i%3===0)
      else thinLeaf(b,length,width,angle,phase+i*.7,i%3===0,i,!!bowDegrees)
      if(!mobile&&phase===5&&i===5) {
        // 仅底中这株主茎第5叶连同原两段短梗绕真实接点正向转10°；固定根及全部z，保持叶形与内部连接。
        const transform=new THREE.Matrix4().makeTranslation(p.x,p.y,0)
          .multiply(new THREE.Matrix4().makeRotationZ(Math.PI/18))
          .multiply(new THREE.Matrix4().makeTranslation(-p.x,-p.y,0))
        for(const geometry of parts.slice(leafStart))geometry.applyMatrix4(transform)
      }
    }
    if(mobile)leaf(stem.getPoint(.94),.48,.085,-.13,phase)
    else {
      const before=originalStem?.getTangent(.94),after=stem.getTangent(.94)
      const turn=before ? Math.atan2(after.y,after.x)-Math.atan2(before.y,before.x) : 0
      thinLeaf(stem.getPoint(.94),.48,.085,-.13+turn,phase,false,1,!!bowDegrees)
    }
    if(!mobile)growLower(stem,phase,phase===5 ? [{t:.40,dx:-.72,dy:.63,n:3},{t:.62,dx:.76,dy:.59,n:3}] : [{t:.29,dx:phase===2 ? 1.05 : -.92,dy:.92,n:4}])
  }
  function grasses(x: number, height: number, lean: number, bends?: Array<[number,number]>,continuous=false) {
    const start=parts.length
    // 可选控制点用相对高度和横向偏移描述宽缓转向；根与穗顶固定，未传参的植株保留原曲线。
    const middle=bends ? bends.map(([t,offset])=>point(x+lean*t+offset,-4.55+(height+.25)*t,.05+.045*t)) : [point(x+lean*.4,-4.3+height*.5,.075)]
    const stem=twig([point(x,-4.55,.05),...middle,point(x+lean,-4.3+height,.095)],.009,36)
    // 弯茎的附着点按弧长分布，并沿同一曲线切线转向，避免主轴改变后叶柄悬空或仍机械竖直。
    const attachment=(t:number)=>bends ? stem.getPointAt(t) : stem.getPoint(t)
    const direction=(t:number)=>{if(!bends)return 0;const tangent=stem.getTangentAt(t);return -Math.atan2(tangent.x,tangent.y)}
    for (let i=0;i<12;i++) {
      const t=.72+i*.022,p=attachment(t),side=i%2 ? 1 : -1
      leaf(p,.20*(1-(t-.7)*2)*(bends ? 1+.07*Math.sin(i*1.7+.4) : 1),.037,direction(t)-side*(.65+(bends ? .08*Math.sin(i*1.9) : 0)),i*.7,false,false)
    }
    // 穗部保留粒状种子；下部三条是实际草叶，桌面与其他真叶采用同一薄面标准。
    for (let i=0;i<3;i++) {
      const t=.15+i*.19,base=attachment(t),angle=direction(t)+(i%2 ? 1 : -1)*(.46+(bends ? (i===1 ? .045 : -.025) : 0))
      if(mobile)leaf(base,.85-i*.08,.035,angle,i*.9,false,false)
      else thinLeaf(base,.85-i*.08,.035,angle,i*.9,false,i,continuous)
    }
    shallowAccent(start)
  }
  function flowerCluster(base: THREE.Vector3, size: number, phase: number) {
    // 花簇只长在放射梗末端；短二级梗之间留空，避免连成实心花盘。
    for (let i=0;i<5;i++) {
      const angle=phase+i*Math.PI*2/5
      const tip=base.clone().add(point(Math.cos(angle)*size, .035+Math.sin(angle)*size*.45, .028))
      twig([base,base.clone().lerp(tip,.6),tip],.0025,5)
      const floret=new THREE.SphereGeometry(.019+(i%2)*.004,5,3)
      floret.scale(1,.8,.55);floret.translate(tip.x,tip.y,tip.z);parts.push(floret)
    }
  }
  function openUmbel(rootX: number, crownX: number, crownY: number, spread: number, phase: number, rays: number) {
    const start=parts.length
    const root=point(rootX,-4.55,.05),crown=point(crownX,crownY,.105)
    twig([root,point(rootX+.10,-2.25,.085),point(crownX+.35,crownY-1.3,.11),crown],.010,56)
    // 扇形张开而非俯视圆盘：主花梗约一屏百余像素长，缩小全景仍能辨认分组轮廓。
    for (let i=0;i<rays;i++) {
      const angle=-1.19+i/(rays-1)*2.27
      const extent=spread*(.94+.07*Math.sin(i*1.6+phase))
      const tip=crown.clone().add(point(Math.sin(angle)*extent,Math.cos(angle)*extent*.97,.018+Math.sin(i*.8)*.035))
      const bow=crown.clone().lerp(tip,.55).add(point(Math.sin(angle)*.045,.02,.015))
      twig([crown,bow,tip],.008,16)
      flowerCluster(tip,spread*.075,phase+i*.7)
    }
    shallowAccent(start)
  }
  function splitLeaf(base: THREE.Vector3, length: number, width: number, angle: number, phase: number) {
    // 两侧裂片各有独立结构；宽裂叶不再用蕨叶等间距的羽片或周期锯齿拼成。
    const profiles=[
      [[0,.02],[.10,.24],[.17,.52],[.24,.29],[.32,.92],[.39,1.04],[.46,.44],[.55,1],[.63,.94],[.70,.48],[.79,.82],[.87,.56],[.94,.24],[1,0]],
      [[0,.02],[.12,.24],[.22,.72],[.31,.75],[.38,.36],[.47,1.04],[.56,.90],[.65,.43],[.74,.82],[.83,.66],[.92,.31],[1,0]],
    ]
    function edge(t: number, side: number) {
      const profile=profiles[side]!
      for(let i=1;i<profile.length;i++) {
        const a=profile[i-1]!,b=profile[i]!
        if(t<=b[0]!) {const blend=(1-Math.cos(Math.PI*(t-a[0]!)/(b[0]!-a[0]!)))/2;return a[1]!+(b[1]!-a[1]!)*blend}
      }
      return 0
    }
    const c=Math.cos(angle),s=Math.sin(angle),rows=64,columns=8
    const center=(t: number)=>Math.sin(Math.PI*t)*length*.08*Math.sin(phase)
    const local=(x: number,y: number,z: number)=>point(base.x+x*c-y*s,base.y+x*s+y*c,base.z+z)
    const surface=(t: number,across: number)=>local(center(t)+across*width*edge(t,across<0 ? 0 : 1),t*length,Math.sin(Math.PI*t)*(.065+.105*(1-across*across)))
    const positions:number[]=[],uv:number[]=[],indices:number[]=[]
    for(let i=0;i<=rows;i++)for(let j=0;j<=columns;j++){
      positions.push(...surface(i/rows,j/columns*2-1).toArray());uv.push(j/columns,i/rows)
    }
    for(let i=0;i<rows;i++)for(let j=0;j<columns;j++){
      const a=i*(columns+1)+j,b=a+columns+1;indices.push(a,a+1,b,a+1,b+1,b)
    }
    const geometry=new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3))
    geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2));geometry.setIndex(indices);geometry.computeVertexNormals();parts.push(geometry)
    const veinPoint=(t: number,across: number)=>surface(t,across).add(point(0,0,.018))
    twig(Array.from({length:13},(_,i)=>veinPoint(i/12,0)),.0085,36)
    // 侧脉随每一侧裂片的峰值分叉，端点落在真实曲面上。
    for(const [side,levels] of [[-1,[.16,.34,.56,.80]],[1,[.23,.48,.75]]] as const) {
      for(const t of levels) {
        const start=t-.14
        twig([veinPoint(start,0),veinPoint(t-.06,side*.47),veinPoint(t,side*.96)],.006,12)
        twig([veinPoint(t-.06,side*.47),veinPoint(t-.04,side*.70),veinPoint(t-.02,side*.84)],.003,6)
      }
    }
  }
  function dividedPlant(x: number, height: number, lean: number, phase: number) {
    const stem=twig([point(x,-4.55,.05),point(x+lean*.12,-4.2+height*.30,.07),point(x+lean*.66,-4.25+height*.7,.08),point(x+lean,-4.1+height,.06)],.012,48)
    const leaves=[{t:.20,l:1.40,w:.43,a:.90},{t:.41,l:1.75,w:.56,a:-.87},{t:.64,l:1.42,w:.49,a:.58},{t:.82,l:1.28,w:.41,a:-.30}]
    for(const item of leaves) {
      const p=stem.getPoint(item.t),side=item.a<0 ? 1 : -1
      const tip=p.clone().add(point(side*.20,.24,mobile ? .022 : 0));if(!mobile)tip.z=.011
      twig([p,p.clone().lerp(tip,.55),tip],.006,10)
      if(mobile)splitLeaf(tip,item.l,item.w,item.a,phase+item.t)
      // 仅这株桌面四片宽叶使用圆缓曲面；叶柄接点、长宽及朝向保持，其余真叶与手机仍用原生成器。
      else parts.push(makeRightBroadLeaf(tip,item.l,item.w,item.a,phase+item.t,leaves.indexOf(item)))
    }
    if(!mobile)growLower(stem,phase,[{t:.18,dx:-.92,dy:.74,n:3},{t:.49,dx:.58,dy:.70,n:3}])
  }
  function gradedTwig(points:THREE.Vector3[],width:number,tipWidth:number,segments=26) {
    const curve=new THREE.CatmullRomCurve3(points,false,'centripetal'),frames=curve.computeFrenetFrames(segments,false)
    const positions:number[]=[],uv:number[]=[],indices:number[]=[],sides=6
    for(let i=0;i<=segments;i++)for(let j=0;j<=sides;j++) {
      const t=i/segments,a=j/sides*Math.PI*2,r=tipWidth+(width-tipWidth)*Math.pow(1-t,.8)
      const p=curve.getPoint(t).addScaledVector(frames.normals[i]!,Math.cos(a)*r).addScaledVector(frames.binormals[i]!,Math.sin(a)*r)
      positions.push(...p.toArray());uv.push(j/sides,t)
    }
    for(let i=0;i<segments;i++)for(let j=0;j<sides;j++){const a=i*(sides+1)+j,b=a+sides+1;indices.push(a,b,a+1,a+1,b,b+1)}
    const geometry=new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2));geometry.setIndex(indices);geometry.computeVertexNormals();parts.push(geometry)
    return curve
  }
  function growLower(parent:THREE.Curve<THREE.Vector3>,phase:number,specs:{t:number;dx:number;dy:number;n:number}[]) {
    // 每条新增枝由现有轴的真实节点长出；有限的三级叉承担小叶，按下部缺口定向，不用随机散点铺满墙面。
    for(const [branchIndex,b]of specs.entries()) {
      const root=parent.getPoint(b.t),tip=root.clone().add(point(b.dx,b.dy,0));tip.z=.047+(branchIndex%2)*.020
      const axis=gradedTwig([root,root.clone().add(point(b.dx*.36,b.dy*.18,-.006)),tip],.0085,.0027,30)
      const direction=-Math.atan2(b.dx,b.dy)
      for(let i=0;i<b.n;i++) {
        const t=.22+i*.17+(branchIndex%2)*.035,p=axis.getPoint(t),side=(i+branchIndex+phase)%2 ? -1 : 1
        const length=.38+(i%3)*.075
        thinLeaf(p,length,length*(.18+(i%2)*.025),direction+side*(.65+(i%2)*.13),phase+i*.7,false,i+phase)
      }
      const forkRoot=axis.getPoint(.48+branchIndex*.08),side=branchIndex%2 ? -1 : 1
      const forkTip=forkRoot.clone().add(point(b.dx*.23+side*.20,b.dy*.39+.12,0));forkTip.z=.037+(phase%3)*.010
      const fork=gradedTwig([forkRoot,forkRoot.clone().lerp(forkTip,.46).add(point(side*.035,.016,0)),forkTip],.0047,.0018,20)
      for(const [i,t]of [.39,.77,1].entries()) {
        const p=fork.getPoint(t),angle=-Math.atan2(forkTip.x-forkRoot.x,forkTip.y-forkRoot.y)+(i===2 ? .10 : (i%2 ? -.72 : .63))
        thinLeaf(p,.29+i*.055,.055+i*.010,angle,phase+branchIndex+i,false,i+branchIndex)
      }
      thinLeaf(tip,.32,.062,direction+.11,phase+branchIndex,false,phase)
    }
  }
  function branchedSprig(x: number, height: number, lean: number, phase: number, stemOffsets?: [number,number], stemRadiusScale=1) {
    const stemPoints=[point(x,-4.55,.055),point(x+lean*.23,-4.2+height*.3,.085),point(x+lean*.75,-4.1+height*.7,.1),point(x+lean,-4.2+height,.08)]
    const originalStem=stemOffsets ? new THREE.CatmullRomCurve3(stemPoints.map(p=>p.clone()),false,'centripetal') : undefined
    if(stemOffsets){stemPoints[1]!.x+=stemOffsets[0];stemPoints[2]!.x+=stemOffsets[1]}
    const stem=gradedTwig(stemPoints,.012*stemRadiusScale,.0048*stemRadiusScale,54)
    // 改主轴后，整条侧枝连同叶柄、叶片和花簇绕新附着点跟随切线转向；内部接点共同变换。
    function followStem(start:number,t:number) {
      if(!originalStem)return
      const base=stem.getPoint(t),before=originalStem.getTangent(t),after=stem.getTangent(t)
      const angle=Math.atan2(after.y,after.x)-Math.atan2(before.y,before.x)
      const transform=new THREE.Matrix4().makeTranslation(base.x,base.y,0)
        .multiply(new THREE.Matrix4().makeRotationZ(angle)).multiply(new THREE.Matrix4().makeTranslation(-base.x,-base.y,0))
      for(const geometry of parts.slice(start))geometry.applyMatrix4(transform)
    }
    // 大小、方向与分叉层级不同的枝条，打破一根主轴配等距成对叶片的轮廓。
    const branches=[{t:.24,dx:-.85,dy:.83,n:4},{t:.43,dx:1.15,dy:.70,n:5},{t:.61,dx:-1.20,dy:.62,n:3},{t:.78,dx:.80,dy:.87,n:4}]
    for(let branchIndex=0;branchIndex<branches.length;branchIndex++) {
      const branchStart=parts.length
      const b=branches[branchIndex]!,base=stem.getPoint(b.t)
      const tip=base.clone().add(point(b.dx,b.dy,.022))
      const curve=gradedTwig([base,base.clone().add(point(b.dx*.46,b.dy*.20,.018)),tip],.0075,.0027,28)
      for(let i=1;i<=b.n;i++) {
        const t=i/(b.n+1),p=curve.getPoint(t),side=(i+branchIndex)%2 ? -1 : 1
        const length=.33+(i%3)*.08
        const direction=-Math.atan2(b.dx,b.dy)+side*(.70+.10*t)
        thinLeaf(p,length,length*.16,direction,phase+i,false,i+branchIndex)
      }
      const forkBase=curve.getPoint(.68),forkTip=forkBase.clone().add(point(b.dx*.19,.34,.045))
      twig([forkBase,forkBase.clone().lerp(forkTip,.55),forkTip],.003,12)
      thinLeaf(forkTip,.22,.043,-.28*Math.sign(b.dx),phase+branchIndex,false,branchIndex)
      if(branchIndex%2===0)flowerCluster(tip,.032,phase+branchIndex)
      else thinLeaf(tip,.27,.05,-.16*Math.sign(b.dx),phase+branchIndex,false,branchIndex+1)
      if(base.y<-.65&&branchIndex<2)growLower(curve,phase+branchIndex,[{t:.36,dx:b.dx*.25+(branchIndex ? -.22 : .22),dy:.40+(phase%3)*.09,n:3}])
      followStem(branchStart,b.t)
    }
    const lowerStart=parts.length,lowerT=.26+(phase%2)*.025
    growLower(stem,phase,[{t:lowerT,dx:phase===3 ? 1.2 : phase===7 ? -1.20 : (phase%2 ? .94 : -.91),dy:.92+(phase%3)*.13,n:4}])
    followStem(lowerStart,lowerT)
    const tipStart=parts.length
    thinLeaf(stem.getPoint(.98),.33,.052,-.17,phase,false,1)
    followStem(tipStart,.98)
  }
  // 植物由视口底部生长：两侧成簇，中间较低，上方留出墙面与 Menu 的呼吸空间。
  if (mobile) {
    // 窄屏只复用一主一次已验收的花序，不引入整幅桌面密集构图；两冠高度错开，右上仍给 Menu 留白。
    parts.push(makeMainUmbel().scale(.78,.91,.70).translate(-.57,-.36,0))
    parts.push(makeSecondaryUmbel().scale(.58,.64,.70).translate(2.59,-1.30,0))
    // 四片自然宽叶由不同真实节点长出，姿态和轮廓沿用桌面生成器；短叶柄落到同一薄叶面。
    const axis=gradedTwig([point(-.80,-4.55,.048),point(-.94,-3.10,.055),point(-.45,-1.80,.043),point(-.20,-.95,.026)],.008,.0025,30)
    const blades=[{t:.25,l:1.50,w:.57,a:-.85,p:1.1,v:0},{t:.48,l:1.55,w:.62,a:.67,p:2.8,v:1},{t:.72,l:1.18,w:.46,a:-.40,p:4.2,v:2}]
    for(const blade of blades) {
      const base=axis.getPoint(blade.t),tip=base.clone().add(point(-Math.sin(blade.a)*.09,Math.cos(blade.a)*.09,0));tip.z=.011
      twig([base,base.clone().lerp(tip,.5),tip],.0034,12)
      parts.push(makeRightBroadLeaf(tip,blade.l,blade.w,blade.a,blade.p,blade.v))
    }
    // 主花第二个实际控制节点经同一缩放平移后为此接点，侧叶与花茎连成一株。
    const mainNode=point(1.6*.78-.57,-2.25*.91-.36,.085*.70),leafBase=point(1.03,-2.10,.011)
    twig([mainNode,mainNode.clone().lerp(leafBase,.5),leafBase],.004,16)
    parts.push(makeRightBroadLeaf(leafBase,1.35,.48,-.40,5.3,3))
    // 少量细叶承担下部枝层；不再用重复高蕨沿两边铺满。
    const sprig=gradedTwig([point(.12,-4.50,.032),point(.35,-3.55,.038),point(.92,-3.04,.020)],.005,.0017,24)
    for(const [i,t]of [.24,.49,.72,.96].entries())thinLeaf(sprig.getPoint(t),.30+(i%2)*.08,.060+(i%2)*.012,i%2 ? -.65 : .92,1.6+i*.9,false,i)
    thinLeaf(axis.getPoint(.98),.31,.057,-.12,3.2,false,1)
    // 仅一个既有蕨实例缩小并压浅到左下配角；羽片生成方式保留，不新造手机细雕。
    const fernStart=parts.length
    fern(-1.40,1.40,.15,5,{scale:[.70,.65],angle:-.30,offset:[.12,-.08]})
    for(const geometry of parts.slice(fernStart))geometry.scale(1,1,.32)
  } else {
    // 上中偏左的银杏三叶与整条弯茎改由独立参考投影网格承载；其他植物布局与显露场保持。
    // 仅在底中植物带补一组低矮心形阔叶，保留上方中央留白及其他已验收植物。
    parts.push(makeLowHeartLeaves())
    // 第一轮结构修订：宽裂叶、疏花梗和斜向分枝为主体；两株蕨叶以不同尺度和斜向姿态退到底部配角。
    // 左高株主管体保留原截面与侧枝；相邻阔叶主轴采用14°尺度的缓弓，叶柄随切线和接点高度连接。
    branchedSprig(-6.25,7.40,1.22,1,[-.20,.20],1.33);broad(-6.50,4.65,.70,2,14)
    parts.push(makeSecondaryUmbel().scale(1,1,.70));openUmbel(-4.4,-3.82,-1.1,.48,3,8)
    // 第二轮左侧单株试件已冻结保留；第三轮只统一其余桌面真叶，构图与手机分支不变。
    // 左细草采用约9°尺度的另一条缓弓；根和穗顶保持，三片长叶随新曲线转向并连续接入主轴。
    parts.push(makeStudyPlant());grasses(-6.45,6.45,.08,[[.32,-.33],[.67,-.29]],true)
    fern(-6.80,2.55,.48,1,{scale:[.78,.76],angle:.15,offset:[.12,-.08]});branchedSprig(-5.1,4.75,3.25,3)
    // 中下部阔叶株沿用连续叶柄与切线跟随，采用11°尺度的缓弓；第5叶原有10°姿态调整仍绕真实接点保留。
    branchedSprig(-1.65,3.70,1.3,4);broad(-.15,1.65,.78,5,11)
    // 第四轮右上主花已阶段接受并保留；第五轮只协调左侧次花与下部枝叶层级。
    parts.push(makeMainUmbel().scale(1,1,.70))
    branchedSprig(3.15,5.55,1.45,6);branchedSprig(5.10,4.85,-3.6,7)
    dividedPlant(4.45,3.30,1.20,5);broad(6.55,3.8,-.13,6)
    // 最右侧高草保留两处不等幅转向，右内侧用单次偏弯；左侧细草使用自身较缓的弓形，不镜像右侧。
    grasses(6.70,7.55,-.26,[[.40,-.53],[.73,.07]]);grasses(5.10,6.1,-.55,[[.58,.42]]);fern(1.20,1.75,-.65,8,{scale:[.72,.80],angle:.23,offset:[.25,-.10]})
    // 中央空白补一组主花、侧花和合拢花苞，不迁移两侧已验收植物。
    parts.push(makeCentralFlowerSprig(false))
    // 中偏左仅补一枚同节点三出复叶，原植物及手机构图保持。
    parts.push(makeTrifoliateUnit())
    // 原右侧长杆左方仅补一片连续浅裂掌状叶，旧植物与三出复叶保持。
    parts.push(makePalmLeaf())
  }
  const geometry=mergeGeometries(parts,false)!
  parts.forEach(part=>part.dispose())
  geometry.computeBoundingSphere()
  return geometry
}
