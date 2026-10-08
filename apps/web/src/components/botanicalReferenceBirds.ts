import * as THREE from 'three'
import wide from '../assets/birds-wide.traced.json'
import gathered from '../assets/birds-gathered.traced.json'

/** 确认图的两种翼姿逐部位描边；原图 UV、真实厚度和墙面锚点来自 Blender。 */
export function makeReferenceBirds() {
  return [wide,gathered].map(mesh=>{
    const geometry=new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(mesh.position,3))
    geometry.setAttribute('normal',new THREE.Float32BufferAttribute(mesh.normal,3))
    geometry.setAttribute('uv',new THREE.Float32BufferAttribute(mesh.uv,2))
    geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(mesh.anchor,1))
    geometry.setIndex(mesh.index);geometry.computeBoundingSphere();return geometry
  })
}
