import * as THREE from 'three'
import mesh from '../assets/flower-stems.traced.json'

/** 确认图逐段描边的连续木质茎秆与附属细叶、花苞，UV 使用独立原图。 */
export function makeReferenceFlowerStems() {
  const geometry=new THREE.BufferGeometry()
  geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
  geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
  geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
  // 与花托共用墙面锚点和影响场，显现抬升时接点不跳层。
  geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
  geometry.setIndex(mesh.index);geometry.computeBoundingSphere()
  return geometry
}
