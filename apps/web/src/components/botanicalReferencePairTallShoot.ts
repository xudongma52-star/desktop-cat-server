import * as THREE from 'three'
import mesh from '../assets/shoot-step-03.traced.json'

/** 本轮双笋中左侧较高的一株：独立轮廓、笋壳层片与参考 UV，逐株验收。 */
export function makeReferencePairTallShoot() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  geometry.setIndex(mesh.index);geometry.computeBoundingSphere();return geometry
}
