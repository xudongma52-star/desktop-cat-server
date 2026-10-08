import * as THREE from 'three'
import mesh from '../assets/shoot-step-02.traced.json'

/** 长竹右侧的一株竹笋：独立笋壳轮廓、嫩芽与确认图 UV，不改已有植株。 */
export function makeReferenceRightShoot() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  geometry.setIndex(mesh.index);geometry.computeBoundingSphere();return geometry
}
