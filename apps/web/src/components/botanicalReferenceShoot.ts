import * as THREE from 'three'
import mesh from '../assets/shoot-step-01.traced.json'

/** 第一株左侧竹笋：确认图轮廓与 UV、连续笋体和笋壳起伏，单独等待验收。 */
export function makeReferenceShoot() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  geometry.setIndex(mesh.index);geometry.computeBoundingSphere();return geometry
}
