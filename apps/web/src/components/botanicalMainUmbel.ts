import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'

/** 已验收右上主花：沿原茎和花冠构造分组复伞形花；手机仅变换整个实例复用，不改变生成曲面或全局光影。 */
export function makeMainUmbel() {
  const parts:THREE.BufferGeometry[]=[]
  const point=(x:number,y:number,z:number)=>new THREE.Vector3(x,y,z)
  const crown=point(2.05,1.55,.105)
  function mesh(positions:number[],uv:number[],indices:number[]) {
    const geometry=new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3))
    geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2));geometry.setIndex(indices);geometry.computeVertexNormals();parts.push(geometry)
  }
  function stalk(points:THREE.Vector3[],startWidth:number,endWidth:number,segments=30) {
    const curve=new THREE.CatmullRomCurve3(points,false,'centripetal'),frames=curve.computeFrenetFrames(segments,false)
    const positions:number[]=[],uv:number[]=[],indices:number[]=[],sides=7
    for(let i=0;i<=segments;i++) {
      const t=i/segments,p=curve.getPoint(t),radius=endWidth+(startWidth-endWidth)*Math.pow(1-t,.8)
      for(let j=0;j<=sides;j++) {
        const a=j/sides*Math.PI*2,v=p.clone().addScaledVector(frames.normals[i]!,Math.cos(a)*radius).addScaledVector(frames.binormals[i]!,Math.sin(a)*radius)
        positions.push(...v.toArray());uv.push(j/sides,t)
      }
    }
    for(let i=0;i<segments;i++)for(let j=0;j<sides;j++){const a=i*(sides+1)+j,b=a+sides+1;indices.push(a,b,a+1,a+1,b,b+1)}
    mesh(positions,uv,indices);return curve
  }
  function floret(base:THREE.Vector3,size:number,turn:number) {
    // 少量花瓣各自从花托长出，薄杯状曲面形成可读体量，不用球粒或噪声铺满花冠。
    for(let petal=0;petal<5;petal++) {
      const a=turn+petal*Math.PI*2/5,c=Math.cos(a),s=Math.sin(a),positions:number[]=[],uv:number[]=[],indices:number[]=[]
      const length=size*(.92+(petal%3)*.09),rows=5,columns=4
      for(let i=0;i<=rows;i++)for(let j=0;j<=columns;j++) {
        const t=i/rows,u=j/columns*2-1,radial=size*.12+t*length,across=Math.sin(Math.PI*t)*size*.43*u
        positions.push(base.x+radial*c-across*s,base.y+radial*s+across*c,base.z+.007+Math.sin(Math.PI*t)*size*.65*(1-u*u)+t*size*.55)
        uv.push(j/columns,t)
      }
      for(let i=0;i<rows;i++)for(let j=0;j<columns;j++){const a=i*(columns+1)+j,b=a+columns+1;indices.push(a,a+1,b,a+1,b+1,b)}
      mesh(positions,uv,indices)
    }
    const receptacle=new THREE.SphereGeometry(size*.30,6,4)
    receptacle.scale(1,.85,.40);receptacle.translate(base.x,base.y,base.z+.006);parts.push(receptacle)
    for(const angle of [turn+.5,turn+2.8]) {
      const c=Math.cos(angle),s=Math.sin(angle),length=size*.72,width=size*.15
      mesh([base.x,base.y,base.z,base.x+c*length-s*width,base.y+s*length+c*width,base.z+.003,base.x+c*length+s*width,base.y+s*length-c*width,base.z+.003],[.5,0,0,1,1,1],[0,1,2])
    }
  }
  const stem=stalk([point(1.5,-4.55,.05),point(1.6,-2.25,.085),point(2.40,.25,.11),crown],.012,.0085,70)
  // 手工分组确定长短、偏斜和前后层次；端点不共圆弧，也不以等角度的直线从一点放射。
  const groups=[
    {dx:-.57,dy:.38,z:.074,bow:-.07,size:.080,heads:3},
    {dx:-.50,dy:.62,z:.110,bow:-.09,size:.095,heads:4},
    {dx:-.36,dy:.70,z:.084,bow:-.07,size:.090,heads:3},
    {dx:-.18,dy:.93,z:.125,bow:-.05,size:.108,heads:4},
    {dx:.00,dy:1.03,z:.093,bow:-.02,size:.087,heads:3},
    {dx:.20,dy:1.00,z:.133,bow:.03,size:.103,heads:4},
    {dx:.36,dy:.80,z:.078,bow:.05,size:.089,heads:3},
    {dx:.30,dy:.62,z:.116,bow:.07,size:.093,heads:3},
  ]
  // 花冠收紧并偏斜；每个末端小伞冠保留独立展开角、扁率和偏斜，不能只是同一针尖花簇的复制。
  const crowns=[
    {angles:[2.91,1.62,.36],spread:1.08,flat:.58,lean:-.035},
    {angles:[2.78,2.00,1.12,.20],spread:1.17,flat:.62,lean:-.025},
    {angles:[2.84,1.48,.26],spread:1.12,flat:.72,lean:-.015},
    {angles:[2.92,2.18,1.16,.31],spread:1.22,flat:.58,lean:-.022},
    {angles:[2.63,1.35,.18],spread:1.08,flat:.78,lean:.012},
    {angles:[2.80,1.89,.92,.13],spread:1.19,flat:.68,lean:.020},
    {angles:[2.92,1.70,.42],spread:1.10,flat:.60,lean:.025},
    {angles:[2.73,1.33,.16],spread:1.18,flat:.72,lean:.035},
  ]
  for(const [index,group]of groups.entries()) {
    // 梗在原主茎末端一小段上错开汇入；共享实际接点，避免用粗结点遮掩几何断接。
    const origin=stem.getPoint(.980-(index%3)*.012),tip=crown.clone().add(point(group.dx,group.dy,group.z-crown.z))
    const primary=stalk([origin,origin.clone().add(point(group.dx*.16,.14,.005)),crown.clone().add(point(group.dx*.48+group.bow,group.dy*.43+.06,group.z-crown.z+.016)),crown.clone().add(point(group.dx*.82+group.bow*.5,group.dy*.81+.02,group.z-crown.z+.011)),tip],.0085-(index%3)*.0006,.0033,44)
    const smallCrown=crowns[index]!
    for(let head=0;head<group.heads;head++) {
      // 二级梗提前从主梗实际节点长出，让分梗承担花簇轮廓；花朵数量、花瓣和局部Z层次保持。
      const a=smallCrown.angles[head]!,start=primary.getPoint(.58+head*.105)
      const extent=group.size*smallCrown.spread*(.93+(head%3)*.11)
      const flower=tip.clone().add(point(Math.cos(a)*extent+smallCrown.lean,Math.sin(a)*extent*smallCrown.flat+.043+(head%2)*.014,.047+((head+index)%3-1)*.016))
      // 静态深度被统一压到24%，原二级梗直径不足一像素、花瓣曲率也被压平；只增本朵末梢的实体宽度和杯形高度。
      const secondary=stalk([start,start.clone().lerp(flower,.48).add(point(-group.bow*.10,.023,.006)),flower],.0055,.0028,19)
      // 大部分短梗只托一朵，少数较重花组托两朵；留出能读到细分梗的空隙，避免叠成绒球。
      const blooms=index%3===1&&head%2===0 ? 2 : 1
      for(let bloom=0;bloom<blooms;bloom++) {
        const attachment=secondary.getPoint(.91+bloom*.04),turn=index*.61+head*.8+bloom*2.13
        const offset=blooms===1 ? .006 : .056
        const flowerBase=flower.clone().add(point(Math.cos(turn)*offset,Math.sin(turn)*offset,.007+(bloom%2)*.005))
        stalk([attachment,attachment.clone().lerp(flowerBase,.55),flowerBase],.0030,.0022,9)
        floret(flowerBase,.043+(head%2)*.004+(index%3)*.002,turn)
      }
    }
  }
  const geometry=mergeGeometries(parts,false)!
  parts.forEach(part=>part.dispose());geometry.computeBoundingSphere();return geometry
}
