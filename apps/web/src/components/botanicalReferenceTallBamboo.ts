import * as THREE from 'three'
import mesh from '../assets/bamboo-tall-01.traced.json'

/** 双长竹中靠左的一株：逐叶轮廓、连续弯秆和确认图 UV，独立进行验收。 */
export function makeReferenceTallBamboo() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  geometry.setIndex(mesh.index);geometry.computeBoundingSphere();return geometry
}
