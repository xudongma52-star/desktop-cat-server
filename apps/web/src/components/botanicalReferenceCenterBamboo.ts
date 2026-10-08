import * as THREE from 'three'
import mesh from '../assets/center-bamboo-01.traced.json'

/** 中下部第一株短竹：逐叶追踪与连续竹秆，保护已验收植物，单独等待验收。 */
export function makeReferenceCenterBamboo() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  geometry.setIndex(mesh.index);geometry.computeBoundingSphere();return geometry
}
