import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'

/** 已验收左侧次花：较小的偏侧复伞冠与主花协调，轮廓独立；手机仅缩放平移整个实例复用。 */
export function makeSecondaryUmbel() {
  const parts:THREE.BufferGeometry[]=[]
  const p=(x:number,y:number,z:number)=>new THREE.Vector3(x,y,z)
  function mesh(positions:number[],uv:number[],indices:number[]) {
    const geometry=new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uv,2));geometry.setIndex(indices);geometry.computeVertexNormals();parts.push(geometry)
  }
  function stalk(points:THREE.Vector3[],width:number,endWidth:number,segments=26) {
    const curve=new THREE.CatmullRomCurve3(points,false,'centripetal'),frames=curve.computeFrenetFrames(segments,false),sides=6
    const positions:number[]=[],uv:number[]=[],indices:number[]=[]
    for(let i=0;i<=segments;i++)for(let j=0;j<=sides;j++) {
      const t=i/segments,a=j/sides*Math.PI*2,r=endWidth+(width-endWidth)*Math.pow(1-t,.8)
      const v=curve.getPoint(t).addScaledVector(frames.normals[i]!,Math.cos(a)*r).addScaledVector(frames.binormals[i]!,Math.sin(a)*r)
      positions.push(...v.toArray());uv.push(j/sides,t)
    }
    for(let i=0;i<segments;i++)for(let j=0;j<sides;j++){const a=i*(sides+1)+j,b=a+sides+1;indices.push(a,b,a+1,a+1,b,b+1)}
    mesh(positions,uv,indices);return curve
  }
  function flower(base:THREE.Vector3,size:number,turn:number) {
    // 薄杯形五瓣和小花托构成末端轮廓，二级梗直接连到花托，不用粒状球簇填盘。
    for(let petal=0;petal<5;petal++) {
      const a=turn+petal*Math.PI*2/5,c=Math.cos(a),s=Math.sin(a),rows=5,columns=4,positions:number[]=[],uv:number[]=[],indices:number[]=[]
      for(let i=0;i<=rows;i++)for(let j=0;j<=columns;j++) {
        const t=i/rows,u=j/columns*2-1,r=size*(.10+t*(.93+(petal%2)*.09)),across=Math.sin(Math.PI*t)*size*.40*u
        positions.push(base.x+r*c-across*s,base.y+r*s+across*c,base.z+.005+Math.sin(Math.PI*t)*size*.58*(1-u*u)+t*size*.48);uv.push(j/columns,t)
      }
      for(let i=0;i<rows;i++)for(let j=0;j<columns;j++){const a=i*(columns+1)+j,b=a+columns+1;indices.push(a,a+1,b,a+1,b+1,b)}
      mesh(positions,uv,indices)
    }
    const cup=new THREE.SphereGeometry(size*.27,6,4);cup.scale(1,.85,.38);cup.translate(base.x,base.y,base.z+.003);parts.push(cup)
  }
  const crown=p(-5.25,1.15,.105)
  const stem=stalk([p(-5.7,-4.55,.05),p(-5.6,-2.25,.085),p(-4.90,-.15,.11),crown],.010,.0065,56)
  const groups=[
    {dx:-.72,dy:.06,z:.111,angles:[2.76,1.45,.29],extent:.103},
    {dx:-.65,dy:.32,z:.135,angles:[2.93,1.81,.40],extent:.112},
    {dx:-.44,dy:.56,z:.123,angles:[2.71,2.00,1.13,.20],extent:.116},
    {dx:-.14,dy:.70,z:.149,angles:[2.85,1.39,.32],extent:.104},
    {dx:.18,dy:.58,z:.116,angles:[2.39,.48],extent:.094},
  ]
  for(const [i,g]of groups.entries()) {
    const start=stem.getPoint(.980+(i%3)*.006),tip=crown.clone().add(p(g.dx,g.dy,g.z-crown.z))
    const primary=stalk([start,start.clone().add(p(g.dx*.30-.033,g.dy*.16+.07,.008)),crown.clone().add(p(g.dx*.73-.020,g.dy*.62+.035,g.z-crown.z+.006)),tip],.0070,.0031,32)
    for(const [j,a]of g.angles.entries()) {
      const root=primary.getPoint(.84+j*.035),extent=g.extent*(.92+(j%2)*.16)
      const base=tip.clone().add(p(Math.cos(a)*extent-.009,Math.sin(a)*extent*(i%2 ? .67 : .55)+.035,.030+(j%3)*.011))
      stalk([root,root.clone().lerp(base,.47).add(p(-.012,.013,.004)),base],.0046,.0023,18)
      flower(base,.033+(j%2)*.003+(i%3)*.001,i*.61+j*.73)
    }
  }
  const geometry=mergeGeometries(parts,false)!
  parts.forEach(part=>part.dispose());geometry.computeBoundingSphere();return geometry
}
