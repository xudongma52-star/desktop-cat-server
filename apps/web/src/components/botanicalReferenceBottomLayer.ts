import * as THREE from 'three'
import mesh from '../assets/bottom-layer-without-fern.traced.json'

/** 底部圆卵叶与细草，旧蕨叶索引已移除，确认图 UV、连续叶柄与浅浮雕统一导出。 */
export function makeReferenceBottomLayer() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  geometry.setIndex(mesh.index);geometry.computeBoundingSphere();return geometry
}
