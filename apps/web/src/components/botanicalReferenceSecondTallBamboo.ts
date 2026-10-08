import * as THREE from 'three'
import mesh from '../assets/bamboo-tall-02.traced.json'

/** 双长竹中的第二株：独立右向叶簇、连续竹秆，保持已验收第一株不变。 */
export function makeReferenceSecondTallBamboo() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  geometry.setIndex(mesh.index);geometry.computeBoundingSphere();return geometry
}
