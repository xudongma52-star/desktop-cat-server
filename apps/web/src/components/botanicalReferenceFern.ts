import * as THREE from 'three'
import mesh from '../assets/fern-v3.traced.json'

/** 高清 alpha 轮廓的蕨羽薄壳，含背面及封闭侧缘，确认图 UV、连续叶柄与浅浮雕统一导出。 */
export function makeReferenceFern() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  // 根部细柄延伸到页面底边之外；上方已确认的蕨羽轮廓、颜色和 UV 保持不变。
  const positions=geometry.getAttribute('position')
  for(let i=0;i<positions.count;i++){
    const y=positions.getY(i)
    if(y< -3.7)positions.setY(i,-3.7+(y+3.7)*2.2)
  }
  geometry.setIndex(mesh.index);geometry.computeVertexNormals();geometry.computeBoundingSphere();return geometry
}
