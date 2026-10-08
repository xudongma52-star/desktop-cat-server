import * as THREE from 'three'
import mesh from '../assets/center-bamboo-02.traced.json'

/** 中右部后侧细竹：独立描出倾斜竹秆与三簇叶，沿用确认图 UV 和共享动态阴影。 */
export function makeReferenceCenterFineBamboo() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  geometry.setIndex(mesh.index);geometry.computeBoundingSphere();return geometry
}
