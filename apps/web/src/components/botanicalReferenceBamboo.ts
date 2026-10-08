import * as THREE from 'three'
import mesh from '../assets/bamboo.traced.json'

/** 双竹薄壳：独立叶脉起伏、连续竹秆与竹枝、原色 UV、根部离墙锚点。 */
export function makeReferenceBamboo() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  geometry.setIndex(mesh.index);geometry.computeBoundingSphere();return geometry
}
