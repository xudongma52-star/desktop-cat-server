import * as THREE from 'three'
import mesh from '../assets/ivy.traced.json'

/** 确认图逐片描边的四叶浅裂弯枝，原图 UV 与连续主茎、叶柄共用墙面锚点。 */
export function makeReferenceIvy() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  geometry.setIndex(mesh.index);geometry.computeBoundingSphere();return geometry
}
