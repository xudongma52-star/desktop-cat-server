import * as THREE from 'three'
import mesh from '../assets/bottom-left-additions-v2.traced.json'

/** 三组配植薄壳；细花枝独立加宽叶面和侧柄，原色 UV、连续根茎和离墙锚点保持一致。 */
export function makeReferenceBottomLeftAdditions() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  geometry.setIndex(mesh.index);geometry.computeBoundingSphere();return geometry
}
