import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'

/** 上中偏左的三叶银杏枝：楔形窄叶根、宽扇面与中央浅缺口，均为自制薄曲面。 */
export function makeGinkgoSprig() {
  const parts:THREE.BufferGeometry[]=[],point=(x:number,y:number,z:number)=>new THREE.Vector3(x,y,z)
  function stem(points:THREE.Vector3[],radius:number,segments:number) {
    const curve=new THREE.CatmullRomCurve3(points,false,'centripetal')
    parts.push(new THREE.TubeGeometry(curve,segments,radius,6,false))
    return curve
  }
  // 沿用先前新增单元连接左侧植物带的弯茎，末端作为三片叶柄的共同分枝区。
  const branch=point(-1.63,1.34,.030)
  const mainStem=stem([point(-4.45,-4.55,.045),point(-4.03,-1.70,.055),point(-2.93,-.12,.065),point(-1.92,1.00,.075),branch],.010,72)
  // 叶柄从主茎中心线内起接，保留管壁交叠；独立近似坐标会留下亚像素净空隙。
  const leaves=[
    {root:mainStem.getPoint(.93),base:point(-2.08,1.48,.004),angle:1.02,width:1.20,length:.82,fold:.018},
    {root:mainStem.getPoint(.989),base:point(-1.43,1.85,.004),angle:-.12,width:.88,length:.65,fold:-.014},
    {root:mainStem.getPoint(.987),base:point(-1.11,1.43,.004),angle:-.94,width:.64,length:.49,fold:.012},
  ]
  for(const [index,leaf]of leaves.entries()) {
    // 三柄名义直径在720px高视口下约为1.7/1.5/1.3px，均细于主茎；只调管径，真实接点及端部曲线保持。
    const petioleRadius=[.009444444444444445,.008333333333333333,.007222222222222222][index]!
    stem([leaf.root,leaf.root.clone().lerp(leaf.base,.55).add(point(.018,0,.006)),leaf.base],petioleRadius,18)
    const positions:number[]=[],uv:number[]=[],indices:number[]=[],rows=44,columns=80
    const c=Math.cos(leaf.angle),s=Math.sin(leaf.angle)
    // 银杏叶腹只有宽缓浅起伏，避免几道高褶把叶片挤成花瓣或折纸扇。
    const folds=index===0 ? [[-.65,.38,.014],[-.15,.42,.021],[.43,.34,.016]]
      : index===1 ? [[-.51,.41,.016],[.27,.46,.020]] : [[-.34,.46,.012],[.49,.38,.015]]
    // 银杏的细脉从叶基向扇缘展开，并在叶腹二叉分支，不使用花瓣状主褶。
    const veinDirections=Array.from({length:index===0 ? 15 : index===1 ? 13 : 11},(_,k)=>-.90+1.80*k/(index===0 ? 14 : index===1 ? 12 : 10))
    const reliefScale=Math.sqrt(leaf.width/1.20),gaussian=(v:number,width:number)=>Math.exp(-Math.pow(v/width,2))
    for(let i=0;i<=rows;i++)for(let j=0;j<=columns;j++) {
      const t=i/rows,u=j/columns*2-1,angle=u*1.02
      // 极坐标扇面从窄楔根展开；弧长缓慢起伏，中央只作浅凹，不切成两片心形叶。
      const notch=.16*gaussian(u-.018*index,.18)
      const edge=1-notch+.012*Math.sin(u*Math.PI*3+index*.65)+.009*Math.sin(u*19+index)*Math.pow(Math.abs(u),.4)+.018*u
      // 侧缘由窄根缓慢展开再接外弧，不用两条直切半径拼出三角扇片。
      const bow=Math.sin(Math.PI*t)
      const x=Math.sin(angle)*leaf.width/(2*Math.sin(1.02))*Math.pow(t,1.12)+leaf.width*.018*bow*(.35+.65*u)*(1-.35*t)
      const y=Math.cos(angle)*leaf.length*t*edge+leaf.length*.035*bow*(u*u-.2)*(index===1 ? -.65 : 1)
      const pleats=folds.reduce((sum,[center,width,height],k)=>sum+height!*gaussian(u-center!-.065*Math.sin(t*2.4+index*.7+k*.8),width!),0)
      // 起伏主要留在叶腹：根与侧缘归于薄边，外弧仅一小段轻翻，不将整片轮廓等高顶出墙面。
      const interior=Math.pow(bow,1.5)*Math.pow(1-u*u,1.5)
      const curl=.009*Math.pow(t,4)*gaussian(u-(index===1 ? -.72 : .73),.24)*(1-u*u)
      // 细脉直接长在薄叶面上，分叉强度由叶腹渐增，根部和外缘收薄，不叠独立粗管。
      const veinT=Math.max(0,Math.min(1,(t-.14)/.80)),veinEnvelope=Math.pow(Math.sin(Math.PI*veinT),2)
      const veins=veinDirections.reduce((sum,v,k)=>{
        const route=v+.012*Math.sin(t*2.5+index+k*.6)
        const fork=Math.max(0,Math.min(1,(t-.36)/.48)),secondary=route+(k%2 ? -.048 : .048)*fork
        return sum+.0032*gaussian(u-route,.020)+.0020*fork*gaussian(u-secondary,.015)
      },0)*veinEnvelope*(1-u*u)
      const grain=.00045*Math.sin(t*130+u*17)*Math.sin(u*83+index)*interior
      const z=leaf.base.z+reliefScale*((.026+leaf.fold*.32*u+pleats)*interior+curl+veins+grain)
      positions.push(leaf.base.x+x*c-y*s,leaf.base.y+x*s+y*c,z);uv.push(j/columns,t)
    }
    for(let i=0;i<rows;i++)for(let j=0;j<columns;j++){const a=i*(columns+1)+j,b=a+columns+1;indices.push(a,a+1,b,a+1,b+1,b)}
    const geometry=new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3))
    geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2));geometry.setIndex(indices);geometry.computeVertexNormals();parts.push(geometry)
  }
  const geometry=mergeGeometries(parts,false)!
  parts.forEach(part=>part.dispose());geometry.computeBoundingSphere();return geometry
}
