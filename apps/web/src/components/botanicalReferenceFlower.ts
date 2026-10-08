import * as THREE from 'three'
import mesh from '../assets/main-flower.traced.json'

/** 原图逐瓣描边的浅浮雕，UV 保留原图坐标；不以图像明暗直接生成几何深度。 */
export function makeReferenceMainFlower() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  // 花头保持已确认锚点；独立重建的主副茎、叶柄与花苞共享这一锚点和动态抬升，避免接点跳层。
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  geometry.setIndex(mesh.index);geometry.computeBoundingSphere()
  return geometry
}
