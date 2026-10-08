import * as THREE from 'three'
import mesh from '../assets/shoot-step-04.traced.json'

/** 双笋右侧小笋：独立直立嫩芽和三层笋壳，保留左侧高笋的模型与位置。 */
export function makeReferencePairShortShoot() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  geometry.setIndex(mesh.index);geometry.computeBoundingSphere();return geometry
}
