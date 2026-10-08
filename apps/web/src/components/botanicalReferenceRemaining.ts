import { botanicalTextureUrl, makeDeferredReferenceGeometry } from './botanicalAssetLoader'

const centerFrontUrl = botanicalTextureUrl('center-bamboo-03-approved.png')
const rightBambooUrl = botanicalTextureUrl('right-bamboo-01-approved.png')
const centerShootUrl = botanicalTextureUrl('shoot-step-05-approved.png')
const rightTallShootUrl = botanicalTextureUrl('shoot-step-06-approved.png')
const rightSmallShootUrl = botanicalTextureUrl('shoot-step-07-approved.png')
const rightTinyShootUrl = botanicalTextureUrl('shoot-step-08-approved.png')

const centerFront = 'center-bamboo-03'
const rightBamboo = 'right-bamboo-01'
const centerShoot = 'shoot-step-05'
const rightTallShoot = 'shoot-step-06'
const rightSmallShoot = 'shoot-step-07'
const rightTinyShoot = 'shoot-step-08'

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
    const geometry=makeDeferredReferenceGeometry(source)
    return { key, geometry, textureUrl }
  })
}
