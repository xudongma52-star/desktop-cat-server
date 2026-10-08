import * as THREE from 'three'
import mesh from '../assets/left-sprig-v2.traced.json'

/** 高清确认图轮廓薄壳；五叶、小花序与连续茎柄保留原色 UV，并共用墙面锚点。 */
export function makeReferenceLeftSprig() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  // 茎尾继续长到画面底边之外，避免完整资产的切口露在画面内；叶片和花序坐标不变。
  const positions=geometry.getAttribute('position')
  for(let i=0;i<positions.count;i++){
    const y=positions.getY(i)
    if(y< -3.2)positions.setY(i,-3.2+(y+3.2)*1.8)
  }
  geometry.setIndex(mesh.index);geometry.computeVertexNormals();geometry.computeBoundingSphere();return geometry
}
