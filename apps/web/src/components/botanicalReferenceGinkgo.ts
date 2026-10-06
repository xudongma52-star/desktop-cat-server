import * as THREE from 'three'
import mesh from '../assets/ginkgo.traced.json'

/** 已确认银杏三叶与木质弯茎的描边网格，细叶脉沿原图 UV 投影。 */
export function makeReferenceGinkgo() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  geometry.setIndex(mesh.index);geometry.computeBoundingSphere();return geometry
}
