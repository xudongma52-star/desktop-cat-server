import * as THREE from 'three'
import centerFront from '../assets/center-bamboo-03.traced.json'
import centerFrontUrl from '../assets/center-bamboo-03-approved.png'
import rightBamboo from '../assets/right-bamboo-01.traced.json'
import rightBambooUrl from '../assets/right-bamboo-01-approved.png'
import centerShoot from '../assets/shoot-step-05.traced.json'
import centerShootUrl from '../assets/shoot-step-05-approved.png'
import rightTallShoot from '../assets/shoot-step-06.traced.json'
import rightTallShootUrl from '../assets/shoot-step-06-approved.png'
import rightSmallShoot from '../assets/shoot-step-07.traced.json'
import rightSmallShootUrl from '../assets/shoot-step-07-approved.png'
import rightTinyShoot from '../assets/shoot-step-08.traced.json'
import rightTinyShootUrl from '../assets/shoot-step-08-approved.png'

/** 本轮补齐的独立植株；每株保留各自轮廓、UV、锚点和纹理，不复用其他植株的形状。 */
export function makeReferenceRemainingBotanicals() {
  return [
    { key:'center-front-bamboo', source:centerFront, textureUrl:centerFrontUrl },
    { key:'right-bamboo', source:rightBamboo, textureUrl:rightBambooUrl },
    { key:'center-shoot', source:centerShoot, textureUrl:centerShootUrl },
    { key:'right-tall-shoot', source:rightTallShoot, textureUrl:rightTallShootUrl },
    { key:'right-small-shoot', source:rightSmallShoot, textureUrl:rightSmallShootUrl },
    { key:'right-tiny-shoot', source:rightTinyShoot, textureUrl:rightTinyShootUrl },
  ].map(({ key, source, textureUrl })=>{
    const geometry=new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(source.position,3))
    geometry.setAttribute('normal',new THREE.Float32BufferAttribute(source.normal,3))
    geometry.setAttribute('uv',new THREE.Float32BufferAttribute(source.uv,2))
    geometry.setAttribute('referenceAnchor',new THREE.Float32BufferAttribute(source.anchor,1))
    geometry.setIndex(source.index);geometry.computeBoundingSphere()
    return { key, geometry, textureUrl }
  })
}
