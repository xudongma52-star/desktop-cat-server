<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { fetchBotanicalBytes, wallAssetBytes, botanicalTextureUrl, botanicalTextureBytes, loadReferenceGeometry, referenceGeometryBytes, referenceGeometryKey } from './botanicalAssetLoader'
import { makeBotanicalRelief } from './botanicalRelief'
import { makeReferenceMainFlower } from './botanicalReferenceFlower'
import { makeReferenceFlowerStems } from './botanicalReferenceFlowerStems'
import { makeReferenceGinkgo } from './botanicalReferenceGinkgo'
import { makeReferenceBroadleaf } from './botanicalReferenceBroadleaf'
import { makeReferenceIvy } from './botanicalReferenceIvy'
import { makeReferenceLeftSprig } from './botanicalReferenceLeftSprig'
import { makeReferenceUmbel } from './botanicalReferenceUmbel'
import { makeReferenceRightPair } from './botanicalReferenceRightPair'
import { makeReferenceBottomLayer } from './botanicalReferenceBottomLayer'
import { makeReferenceGapSprig } from './botanicalReferenceGapSprig'
import { makeReferenceFern } from './botanicalReferenceFern'
import { makeReferenceBottomLeftAdditions } from './botanicalReferenceBottomLeftAdditions'
import { makeReferenceBamboo } from './botanicalReferenceBamboo'
import { makeReferenceShoot } from './botanicalReferenceShoot'
import { makeReferenceRightShoot } from './botanicalReferenceRightShoot'
import { makeReferencePairTallShoot } from './botanicalReferencePairTallShoot'
import { makeReferencePairShortShoot } from './botanicalReferencePairShortShoot'
import { makeReferenceTallBamboo } from './botanicalReferenceTallBamboo'
import { makeReferenceSecondTallBamboo } from './botanicalReferenceSecondTallBamboo'
import { makeReferenceCenterBamboo } from './botanicalReferenceCenterBamboo'
import { makeReferenceCenterFineBamboo } from './botanicalReferenceCenterFineBamboo'
import { makeReferenceRemainingBotanicals } from './botanicalReferenceRemaining'
import { makeReferenceBirds } from './botanicalReferenceBirds'
import wallNormalUrl from '../assets/beige_wall_001_nor_gl_1k.jpg'
import wallRoughUrl from '../assets/beige_wall_001_rough_1k.jpg'

const flowerStemsReferenceUrl = botanicalTextureUrl('flower-stems-approved-reference.png')
const ginkgoReferenceUrl = botanicalTextureUrl('ginkgo-approved-reference.png')
const broadleafReferenceUrl = botanicalTextureUrl('broadleaf-approved-reference.png')
const ivyReferenceUrl = botanicalTextureUrl('ivy-approved-reference.png')
const leftSprigReferenceUrl = botanicalTextureUrl('left-sprig-v2-cutout.png')
const umbelReferenceUrl = botanicalTextureUrl('umbel-approved-reference.png')
const rightPairReferenceUrl = botanicalTextureUrl('right-pair-approved-reference.png')
const bottomLayerReferenceUrl = botanicalTextureUrl('bottom-layer-approved-reference.png')
const gapSprigReferenceUrl = botanicalTextureUrl('gap-sprig-approved-reference.png')
const fernReferenceUrl = botanicalTextureUrl('fern-v3-cutout.png')
const bottomLeftAdditionsUrl = botanicalTextureUrl('bottom-left-additions-cutout.png')
const bambooReferenceUrl = botanicalTextureUrl('bamboo-cutout.png')
const shootReferenceUrl = botanicalTextureUrl('shoot-step-01-approved.png')
const rightShootReferenceUrl = botanicalTextureUrl('shoot-step-02-approved.png')
const pairTallShootReferenceUrl = botanicalTextureUrl('shoot-step-03-approved.png')
const pairShortShootReferenceUrl = botanicalTextureUrl('shoot-step-04-approved.png')
const tallBambooReferenceUrl = botanicalTextureUrl('bamboo-tall-01-approved.png')
const secondTallBambooReferenceUrl = botanicalTextureUrl('bamboo-tall-02-approved.png')
const centerBambooReferenceUrl = botanicalTextureUrl('center-bamboo-01-approved.png')
const centerFineBambooReferenceUrl = botanicalTextureUrl('center-bamboo-02-approved.png')
const wideBirdReferenceUrl = botanicalTextureUrl('birds-wide-approved.png')
const gatheredBirdReferenceUrl = botanicalTextureUrl('birds-gathered-approved.png')
const flowerReferenceUrl = botanicalTextureUrl('flower-approved-reference.png')

const props = defineProps<{ menu: number; content: number; scroll: number; hover: number; reveal: number; openingTone: number; openingColor: string; reducedMotion: boolean; skipOpening: boolean }>()
const emit = defineEmits<{ progress: [value: number]; failure: []; ready: [result: { failed: boolean }] }>()
const host = ref<HTMLDivElement | null>(null)
const status = ref('loading')
// 页面优化期间临时全量显现，恢复为 false 即回到原鼠标迷雾、驻留及回落效果。
const hideMistForPageReview = false
let renderer: THREE.WebGLRenderer | undefined, observer: ResizeObserver | undefined
let frame = 0, disposed = false
let requestDraw = () => {}, cleanup = () => {}
onMounted(() => {
  if (!host.value) return
  const container=host.value, mobile=window.innerWidth<700
  try { renderer=new THREE.WebGLRenderer({antialias:true,alpha:false,powerPreference:'low-power'}) }
  catch { status.value='fallback';emit('failure');emit('progress',1);emit('ready',{failed:true});return }
  const gl=renderer
  gl.setPixelRatio(Math.min(window.devicePixelRatio,mobile ? 1.25 : 1.5))
  gl.shadowMap.enabled=true;gl.shadowMap.type=THREE.PCFShadowMap
  gl.toneMapping=THREE.ACESFilmicToneMapping;gl.toneMappingExposure=1.04
  container.appendChild(gl.domElement)
  const scene=new THREE.Scene(), paper=new THREE.Color(mobile ? '#d8d6d0' : '#c7c5bf'), backdrop=paper.clone(), night=new THREE.Color('#555952'), nightBackground=new THREE.Color('#252b26')
  scene.background=backdrop
  const camera=new THREE.OrthographicCamera(-7.11,7.11,4,-4,.1,40)
  camera.position.set(0,0,15);camera.lookAt(0,0,0)
  const stone=new THREE.MeshStandardMaterial({color:'#d5d3cc',roughness:.97,side:THREE.DoubleSide})
  const wallMaterial=stone.clone(), wall=new THREE.Mesh(new THREE.PlaneGeometry(60,40),wallMaterial)
  wall.position.z=-.012;wall.receiveShadow=true;scene.add(wall)
  // 桌面左右旧密集植株已全部由独立参考网格替代；原生成器只用于手机，不再生成或叠加桌面旧枝。
  const relief=new THREE.Mesh(mobile ? makeBotanicalRelief(true) : new THREE.BufferGeometry(),stone)
  relief.castShadow=relief.receiveShadow=true;if(mobile)scene.add(relief)
  // 桌面首页用侧光主导、补光托底；手机与 Menu 最终夜色沿用原强度，内页使用独立 CornerScene。
  const ambientBase=mobile ? 1.25 : .50,sunBase=mobile ? 2.9 : 3.55
  const ambient=new THREE.HemisphereLight('#f9f8f4','#aca9a3',ambientBase)
  const sun=new THREE.DirectionalLight('#fffaf1',sunBase)
  sun.position.set(-5,7,5.3);sun.castShadow=true
  sun.shadow.mapSize.set(mobile ? 1024 : 2048,mobile ? 1024 : 2048)
  sun.shadow.camera.left=-10;sun.shadow.camera.right=10;sun.shadow.camera.top=7;sun.shadow.camera.bottom=-7
  sun.shadow.camera.near=.1;sun.shadow.camera.far=30;sun.shadow.bias=-.0001;sun.shadow.normalBias=.008;sun.shadow.radius=3
  scene.add(ambient,sun)

  // 低分辨率双缓冲影响场只存交互强度；桌面 RG 分别保留驻留与运动，植物深度与阴影共用同一场。
  const flowSize=mobile ? 96 : 160
  const targets=[0,1].map(()=>new THREE.WebGLRenderTarget(flowSize,flowSize,{depthBuffer:false,type:mobile ? THREE.UnsignedByteType : THREE.HalfFloatType,minFilter:THREE.LinearFilter,magFilter:THREE.LinearFilter}))
  const flowUniforms={previous:{value:targets[0]!.texture},pointer:{value:new THREE.Vector2(.5,.5)},velocity:{value:new THREE.Vector2()},autoPointer:{value:new THREE.Vector2(-1,-1)},autoStrength:{value:0},aspect:{value:1},radius:{value:.15},strength:{value:0},motion:{value:0},clock:{value:0},decay:{value:.95},response:{value:1}}
  const flowMaterial=new THREE.ShaderMaterial({uniforms:flowUniforms,depthTest:false,depthWrite:false,
    vertexShader:'varying vec2 flowUv;void main(){flowUv=uv;gl_Position=vec4(position.xy,0.,1.);}',
    fragmentShader:`
      uniform sampler2D previous;uniform vec2 pointer;uniform vec2 velocity;uniform vec2 autoPointer;
      uniform float aspect,radius,strength,motion,clock,decay,response,autoStrength;varying vec2 flowUv;
      float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
      float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+vec2(1.,0.)),f.x),mix(hash(i+vec2(0.,1.)),hash(i+vec2(1.,1.)),f.x),f.y);}
      void main(){
        vec2 d=flowUv-pointer;d.x*=aspect;
        ${mobile ? `float irregular=.75+.32*noise(flowUv*vec2(aspect,1.)*17.+clock*.18)+.14*noise(flowUv*vec2(aspect,1.)*37.-clock*.12);
        float stamp=(1.-smoothstep(radius*.22,radius*irregular,length(d)))*strength;
        // 速度携带旧场形成短拖尾；边缘是平滑噪声权重，没有圆圈或描边。
        float history=texture2D(previous,flowUv-velocity*.014).r*decay;
        float value=max(history,stamp);
        gl_FragColor=vec4(value,value,value,1.);` : `
        // 固定软笔触由连续噪声调制；收束来自运动贡献退去，不动画缩小半径。
        vec2 p=flowUv*vec2(aspect,1.);
        float organic=.30+.50*noise(p*7.+clock*.09)+.20*noise(p*17.-clock*.07);
        float stamp=(1.-smoothstep(0.,radius,length(d)))*organic*strength;
        // 驻留持续写入 R，实际速度只写 G；时间校正的积累/衰减保留短暂历史，半浮点避免尾部量化残留。
        vec2 history=texture2D(previous,flowUv).rg*decay;
        // 自动轨迹独立写入同一 RG 场；显露、离墙深度与投影因此一起变化，不另叠一层遮罩。
        vec2 autoDistance=flowUv-autoPointer;autoDistance.x*=aspect;
        float autoOrganic=smoothstep(.2,.8,noise(p*3.5+clock*.025))*organic;
        float autoStamp=(1.-smoothstep(0.,.19,length(autoDistance)))*autoOrganic*autoStrength;
        vec2 field=min(vec2(1.),history+stamp*response*vec2(2.2,3.6*motion)+autoStamp*response*vec2(3.3,5.4));
        gl_FragColor=vec4(field,0.,1.);`}
      }`})
  const flowScene=new THREE.Scene(),flowCamera=new THREE.Camera(),flowPlane=new THREE.Mesh(new THREE.PlaneGeometry(2,2),flowMaterial)
  flowScene.add(flowPlane)
  for (const target of targets) {gl.setRenderTarget(target);gl.setClearColor(0);gl.clear()}
  gl.setRenderTarget(null)
  // 桌面 .06 / .76 区间与最高 .82 保留；手机基础深度恢复原 .24 以保证无 hover 静态可读性，触控增量仍为 1.36。
  const uniforms={botanicalFlow:{value:targets[0]!.texture},botanicalExtent:{value:new THREE.Vector2(7.11,4)},botanicalScale:{value:new THREE.Vector2(1,1)},botanicalActive:{value:1},botanicalBaseDepth:{value:mobile ? .24 : .06},botanicalLiftDepth:{value:mobile ? 1.36 : .76}}
  const deformation=`
    uniform sampler2D botanicalFlow;uniform vec2 botanicalExtent;uniform vec2 botanicalScale;uniform float botanicalActive,botanicalBaseDepth,botanicalLiftDepth;
    float botanicalDepth(vec2 p){vec2 st=p*botanicalScale/(botanicalExtent*2.)+.5;vec2 field=${hideMistForPageReview ? 'vec2(1.)' : 'texture2D(botanicalFlow,st).rg'};return botanicalBaseDepth+botanicalLiftDepth*${mobile ? 'field.r' : '((field.r+field.g)*.5)'}*botanicalActive;}
  `
  const shadowUniforms={botanicalFlow:uniforms.botanicalFlow,botanicalExtent:uniforms.botanicalExtent,botanicalActive:uniforms.botanicalActive,botanicalShadowMenu:{value:0},botanicalMistStatic:{value:props.reducedMotion ? 1 : 0}}
  function fieldShadow(shader:Parameters<THREE.MeshStandardMaterial['onBeforeCompile']>[0]){
    Object.assign(shader.uniforms,shadowUniforms)
    // 墙面和植物都按世界坐标读取同一场，只调已有定向光投影，不改变底色、光向或几何。
    shader.vertexShader='varying vec2 botanicalShadowPosition;\n'+shader.vertexShader
    shader.vertexShader=shader.vertexShader.replace('#include <project_vertex>','botanicalShadowPosition=(modelMatrix*vec4(transformed,1.)).xy;\n#include <project_vertex>')
    shader.fragmentShader=`
      uniform sampler2D botanicalFlow;uniform vec2 botanicalExtent;uniform float botanicalActive,botanicalShadowMenu,botanicalMistStatic;
      varying vec2 botanicalShadowPosition;
      float botanicalMistWeight(){
        vec2 field=${hideMistForPageReview ? 'vec2(1.)' : 'texture2D(botanicalFlow,botanicalShadowPosition/(botanicalExtent*2.)+.5).rg'};
        return mix(smoothstep(.01,.70,(field.r+field.g)*botanicalActive),1.,max(botanicalShadowMenu,botanicalMistStatic));
      }
      float botanicalShadowWeight(){
        vec2 field=${hideMistForPageReview ? 'vec2(1.)' : 'texture2D(botanicalFlow,botanicalShadowPosition/(botanicalExtent*2.)+.5).rg'};
        float reveal=smoothstep(0.,1.,(field.r+field.g)*botanicalActive);
        // 桌面无输入完全遮住投影；Menu 接回原夜色，减弱动画模式保留原静态浅痕。
        // 主花与周围植物共用相同的交互投影强度；薄瓣外观由原图纹理保留。
        return mix(mix(reveal,.06+.94*reveal,botanicalMistStatic),1.,botanicalShadowMenu);
      }
    `+shader.fragmentShader
    shader.fragmentShader=shader.fragmentShader.replace('#include <lights_fragment_begin>',THREE.ShaderChunk.lights_fragment_begin.replace('directionalLightShadow.shadowIntensity,','directionalLightShadow.shadowIntensity*botanicalShadowWeight(),'))
  }
  if(!mobile){wallMaterial.onBeforeCompile=fieldShadow;wallMaterial.customProgramCacheKey=()=> 'botanical-wall-field-shadow-mist-2'}
  stone.onBeforeCompile=shader=>{
    Object.assign(shader.uniforms,uniforms);shader.vertexShader=deformation+shader.vertexShader
    shader.vertexShader=shader.vertexShader.replace('#include <beginnormal_vertex>',`
      #include <beginnormal_vertex>
      float depth=botanicalDepth(position.xy);
      vec2 grad=vec2(botanicalDepth(position.xy+vec2(.018,0.))-botanicalDepth(position.xy-vec2(.018,0.)),botanicalDepth(position.xy+vec2(0.,.018))-botanicalDepth(position.xy-vec2(0.,.018)))/.036;
      // z'=.016+z*depth(x,y) 的逆转置同时修正梯度项；xy 梯度项和 z 分量都除以 depth，最后对完整向量归一化。静止 grad=0，法线仍按基础深度修正。
      objectNormal.xy-=grad*position.z*objectNormal.z/depth;objectNormal.z/=depth;objectNormal=normalize(objectNormal);
    `)
    shader.vertexShader=shader.vertexShader.replace('#include <begin_vertex>','#include <begin_vertex>\ntransformed.z=.016+position.z*botanicalDepth(position.xy);')
    if(!mobile)fieldShadow(shader)
  }
  stone.customProgramCacheKey=()=> mobile ? 'botanical-flow-relief-1' : 'botanical-flow-relief-field-shadow-1'
  const depthMaterial=new THREE.MeshDepthMaterial({depthPacking:THREE.RGBADepthPacking,side:THREE.DoubleSide})
  depthMaterial.onBeforeCompile=shader=>{
    Object.assign(shader.uniforms,uniforms);shader.vertexShader=deformation+shader.vertexShader
    shader.vertexShader=shader.vertexShader.replace('#include <begin_vertex>','#include <begin_vertex>\ntransformed.z=.016+position.z*botanicalDepth(position.xy);')
  }
  depthMaterial.customProgramCacheKey=()=> 'botanical-shadow-depth-1';relief.customDepthMaterial=depthMaterial
  // 两朵花与连续主副茎使用原图投影，共用深度场；花苞、细叶沿同曲线附着并同步抬升。
  const flowerMaterial=mobile ? undefined : stone.clone()
  const referenceFlower=flowerMaterial ? new THREE.Mesh(makeReferenceMainFlower(),flowerMaterial) : undefined
  const flowerTint={value:new THREE.Vector3(1,1,1)}
  const flowerDepthMaterial=referenceFlower ? depthMaterial.clone() : undefined
  if(referenceFlower&&flowerMaterial){
    referenceFlower.castShadow=referenceFlower.receiveShadow=true
    // 静止保留原图贴墙基线，显现时增加整体抬升，让薄瓣与茎秆产生可见侧向投影。
    const referenceLift='referenceAnchor+position.z*botanicalDepth(position.xy)+.12*clamp((botanicalDepth(position.xy)-botanicalBaseDepth)/botanicalLiftDepth,0.,1.)'
    if(flowerDepthMaterial){
      flowerDepthMaterial.onBeforeCompile=shader=>{
        depthMaterial.onBeforeCompile(shader,gl)
        shader.vertexShader='attribute float referenceAnchor;\n'+shader.vertexShader
        shader.vertexShader=shader.vertexShader.replace('transformed.z=.016+position.z*botanicalDepth(position.xy)',`transformed.z=${referenceLift}`)
      }
      flowerDepthMaterial.customProgramCacheKey=()=> 'botanical-traced-main-flower-depth-2'
      referenceFlower.customDepthMaterial=flowerDepthMaterial
    }
    scene.add(referenceFlower)
    flowerMaterial.onBeforeCompile=shader=>{
      stone.onBeforeCompile(shader,gl)
      shader.vertexShader='attribute float referenceAnchor;\n'+shader.vertexShader
      shader.vertexShader=shader.vertexShader.replace('transformed.z=.016+position.z*botanicalDepth(position.xy)',`transformed.z=${referenceLift}`)
      // 整体抬升也随影响场变化，法线梯度和阴影网格保持同一变形。
      shader.vertexShader=shader.vertexShader.replace('grad*position.z*objectNormal.z/depth','grad*(position.z+.12/botanicalLiftDepth)*objectNormal.z/depth')
      shader.uniforms.flowerTint=flowerTint
      shader.fragmentShader='uniform vec3 flowerTint;\n'+shader.fragmentShader
      // 原图含光照，局部均值归一化只保留中高频外观；它是近似去光照，不是测得的真实反照率。
      shader.fragmentShader=shader.fragmentShader.replace('#include <map_fragment>',`
        #ifdef USE_MAP
          vec3 detail=texture2D(map,vMapUv).rgb;
          vec2 flowerField=${hideMistForPageReview ? 'vec2(1.)' : 'texture2D(botanicalFlow,botanicalShadowPosition/(botanicalExtent*2.)+.5).rg'};
          float referenceReveal=smoothstep(0.,1.,(flowerField.r+flowerField.g)*botanicalActive);
          vec2 spread=vec2(.017,.021);
          vec3 localMean=(texture2D(map,vMapUv+vec2(spread.x,0.)).rgb+texture2D(map,vMapUv-vec2(spread.x,0.)).rgb+
            texture2D(map,vMapUv+vec2(0.,spread.y)).rgb+texture2D(map,vMapUv-vec2(0.,spread.y)).rgb+detail*4.)/8.;
          diffuseColor.rgb*=mix(vec3(1.),clamp(detail/max(localMean,vec3(.08)),vec3(.55),vec3(1.35)),referenceReveal);
        #endif
      `)
      // 固定正面保留原图外观时，用混合替代重复乘光照；真实几何仍参与抬起和接触投影。
      // 这属于带原图光照的投影外观，不能宣称已恢复真实反照率或任意角度一致的材质。
      shader.fragmentShader=shader.fragmentShader.replace('#include <opaque_fragment>',`
        #ifdef USE_MAP
          outgoingLight=mix(outgoingLight,texture2D(map,vMapUv).rgb*flowerTint,.88*referenceReveal);
        #endif
        #include <opaque_fragment>
      `)
    }
    flowerMaterial.customProgramCacheKey=()=> 'botanical-traced-main-flower-detail-2'
  }
  // 保存未染色投影回调；每株颜色独立，银杏不会继承花瓣的暖色。
  const referenceProjectionCompile=flowerMaterial?.onBeforeCompile
  // 鸟保留确认图的淡暖羽色与两种翼姿，只沿已有影响场抬升；同一深度公式产生真实动态投影。
  const referenceBirds=flowerMaterial ? makeReferenceBirds().map((geometry,index)=>{
    const material=flowerMaterial.clone()
    material.toneMapped=false
    material.onBeforeCompile=referenceProjectionCompile!
    material.customProgramCacheKey=()=> 'botanical-approved-bird-projection-1-'+index
    const bird=new THREE.Mesh(geometry,material)
    bird.customDepthMaterial=flowerDepthMaterial
    bird.castShadow=bird.receiveShadow=true;scene.add(bird);return bird
  }) : []
  // 确认图中的木质主副茎、细叶与花苞独立描边及纹理投影，接点沿连续曲线定位。
  const flowerStemMaterial=flowerMaterial?.clone()
  const referenceFlowerStems=flowerStemMaterial ? new THREE.Mesh(makeReferenceFlowerStems(),flowerStemMaterial) : undefined
  if(referenceFlowerStems&&flowerStemMaterial&&flowerMaterial){
    flowerStemMaterial.onBeforeCompile=referenceProjectionCompile!
    flowerStemMaterial.customProgramCacheKey=()=> 'botanical-confirmed-flower-stems-detail-1'
    referenceFlowerStems.customDepthMaterial=flowerDepthMaterial
    referenceFlowerStems.castShadow=referenceFlowerStems.receiveShadow=true;scene.add(referenceFlowerStems)
  }
  // 银杏沿用已验收投影材质与深度变形，独立纹理避免改变两朵花的资产。
  const ginkgoMaterial=flowerMaterial?.clone()
  const referenceGinkgo=ginkgoMaterial ? new THREE.Mesh(makeReferenceGinkgo(),ginkgoMaterial) : undefined
  if(referenceGinkgo&&ginkgoMaterial&&flowerMaterial){
    // 只在显现的原图投影中补偿细叶脉对比；静止材质仍共用墙面的色调映射。
    ginkgoMaterial.onBeforeCompile=(shader,render)=>{
      referenceProjectionCompile!(shader,render)
      shader.fragmentShader=shader.fragmentShader.replace('texture2D(map,vMapUv).rgb*flowerTint','clamp((texture2D(map,vMapUv).rgb-vec3(.55))*1.35+vec3(.55),vec3(.03),vec3(1.))*flowerTint')
    }
    ginkgoMaterial.customProgramCacheKey=()=> 'botanical-traced-ginkgo-detail-2'
    referenceGinkgo.customDepthMaterial=flowerDepthMaterial
    referenceGinkgo.castShadow=referenceGinkgo.receiveShadow=true;scene.add(referenceGinkgo)
  }
  // 四叶弯枝独立替换原三出叶；沿用已验收的投影、法线梯度和同公式阴影深度。
  const broadleafMaterial=flowerMaterial?.clone()
  const referenceBroadleaf=broadleafMaterial ? new THREE.Mesh(makeReferenceBroadleaf(),broadleafMaterial) : undefined
  const broadleafDepthMaterial=flowerDepthMaterial?.clone()
  if(referenceBroadleaf&&broadleafMaterial&&flowerMaterial){
    // 小尺寸叶枝已确认的 .12 抬升现在由银杏和两朵花共用；整条茎柄、法线梯度与投影保持同公式。
    broadleafMaterial.onBeforeCompile=referenceProjectionCompile!
    broadleafMaterial.customProgramCacheKey=()=> 'botanical-traced-broadleaf-detail-2'
    if(broadleafDepthMaterial&&flowerDepthMaterial){
      broadleafDepthMaterial.onBeforeCompile=flowerDepthMaterial.onBeforeCompile
      broadleafDepthMaterial.customProgramCacheKey=()=> 'botanical-traced-broadleaf-depth-2'
      referenceBroadleaf.customDepthMaterial=broadleafDepthMaterial
    }
    referenceBroadleaf.castShadow=referenceBroadleaf.receiveShadow=true;scene.add(referenceBroadleaf)
  }
  // 底中区域按确认图整组重建；轮廓、茎柄和纹理独立，抬升及真实投影复用已确认公式。
  const ivyMaterial=flowerMaterial?.clone()
  const referenceIvy=ivyMaterial ? new THREE.Mesh(makeReferenceIvy(),ivyMaterial) : undefined
  if(referenceIvy&&ivyMaterial&&flowerMaterial){
    ivyMaterial.onBeforeCompile=referenceProjectionCompile!
    ivyMaterial.customProgramCacheKey=()=> 'botanical-traced-ivy-detail-1'
    referenceIvy.customDepthMaterial=flowerDepthMaterial
    referenceIvy.castShadow=referenceIvy.receiveShadow=true;scene.add(referenceIvy)
  }
  // 左侧密集旧植株整组替换，五片叶与小花簇共用一条连续弯茎和确认图纹理。
  // 根部逐渐贴回墙面：可见材质和专用阴影材质使用同一高度，法线包含根部过渡梯度。
  function anchorRoot(shader: {vertexShader:string}) {
    const fullLift='referenceAnchor+position.z*botanicalDepth(position.xy)+.12*clamp((botanicalDepth(position.xy)-botanicalBaseDepth)/botanicalLiftDepth,0.,1.)'
    const rootLift='referenceAnchor+(position.z*botanicalDepth(position.xy)+.12*clamp((botanicalDepth(position.xy)-botanicalBaseDepth)/botanicalLiftDepth,0.,1.))*smoothstep(-4.08,-3.55,position.y)'
    shader.vertexShader=shader.vertexShader.replace(fullLift,rootLift)
    shader.vertexShader=shader.vertexShader.replace('float depth=botanicalDepth(position.xy);',`
      float rootT=clamp((position.y+4.08)/.53,0.,1.);
      float rootWeight=rootT*rootT*(3.-2.*rootT);
      float rootSlope=6.*rootT*(1.-rootT)/.53;
      float rootHeight=position.z*botanicalDepth(position.xy)+.12*clamp((botanicalDepth(position.xy)-botanicalBaseDepth)/botanicalLiftDepth,0.,1.);
      float depth=max(.001,botanicalDepth(position.xy)*rootWeight);
    `)
    shader.vertexShader=shader.vertexShader.replace('grad*(position.z+.12/botanicalLiftDepth)*objectNormal.z/depth','(grad*(position.z+.12/botanicalLiftDepth)*rootWeight+vec2(0.,rootSlope)*rootHeight)*objectNormal.z/depth')
  }
  const rootedDepthMaterial=flowerDepthMaterial?.clone()
  if(rootedDepthMaterial&&flowerDepthMaterial){
    rootedDepthMaterial.onBeforeCompile=(shader,renderer)=>{
      flowerDepthMaterial.onBeforeCompile(shader,renderer);anchorRoot(shader)
    }
    rootedDepthMaterial.customProgramCacheKey=()=> 'botanical-bottom-root-depth-1'
  }
  const leftSprigMaterial=flowerMaterial?.clone()
  const referenceLeftSprig=leftSprigMaterial ? new THREE.Mesh(makeReferenceLeftSprig(),leftSprigMaterial) : undefined
  if(referenceLeftSprig&&leftSprigMaterial&&flowerMaterial){
    // 固定正面沿用确认图原色；不叠加统一染色、ACES 或二次灯光，薄壳仍产生真实投影。
    leftSprigMaterial.toneMapped=false
    leftSprigMaterial.onBeforeCompile=(shader,renderer)=>{
      referenceProjectionCompile!(shader,renderer)
      anchorRoot(shader)
      shader.fragmentShader=shader.fragmentShader.replace('.88*referenceReveal','referenceReveal')
      shader.fragmentShader=shader.fragmentShader.replace('#include <opaque_fragment>',`
        #ifdef USE_MAP
          if(texture2D(map,vMapUv).a<.90)discard;
        #endif
        #include <opaque_fragment>
      `)
    }
    leftSprigMaterial.customProgramCacheKey=()=> 'botanical-left-sprig-hd-root-3'
    referenceLeftSprig.customDepthMaterial=rootedDepthMaterial
    referenceLeftSprig.castShadow=referenceLeftSprig.receiveShadow=true;scene.add(referenceLeftSprig)
  }
  // 确认图疏花复伞冠、三片浅齿叶及连续弯茎，独立于旁边已确认花朵和裂叶。
  const umbelMaterial=flowerMaterial?.clone()
  const referenceUmbel=umbelMaterial ? new THREE.Mesh(makeReferenceUmbel(),umbelMaterial) : undefined
  if(referenceUmbel&&umbelMaterial&&flowerMaterial){
    umbelMaterial.onBeforeCompile=referenceProjectionCompile!
    umbelMaterial.customProgramCacheKey=()=> 'botanical-traced-compound-umbel-detail-1'
    referenceUmbel.customDepthMaterial=flowerDepthMaterial
    referenceUmbel.castShadow=referenceUmbel.receiveShadow=true;scene.add(referenceUmbel)
  }
  // 右侧整组重建的叶枝和单根高穗，共用原图映射和连续茎柄，不叠加旧碎叶。
  const rightPairMaterial=flowerMaterial?.clone()
  const referenceRightPair=rightPairMaterial ? new THREE.Mesh(makeReferenceRightPair(),rightPairMaterial) : undefined
  if(referenceRightPair&&rightPairMaterial&&flowerMaterial){
    rightPairMaterial.onBeforeCompile=referenceProjectionCompile!
    rightPairMaterial.customProgramCacheKey=()=> 'botanical-traced-right-pair-detail-1'
    referenceRightPair.customDepthMaterial=flowerDepthMaterial
    referenceRightPair.castShadow=referenceRightPair.receiveShadow=true;scene.add(referenceRightPair)
  }
  // 底部植物沿用主体 .12 离墙抬升，保证可见高度、法线梯度与真实阴影同步。
  const bottomLayerMaterial=flowerMaterial?.clone()
  const bottomLayerDepthMaterial=flowerDepthMaterial?.clone()
  const referenceBottomLayer=bottomLayerMaterial ? new THREE.Mesh(makeReferenceBottomLayer(),bottomLayerMaterial) : undefined
  if(referenceBottomLayer&&bottomLayerMaterial&&flowerMaterial&&bottomLayerDepthMaterial&&flowerDepthMaterial){
    bottomLayerMaterial.onBeforeCompile=(shader,renderer)=>{
      referenceProjectionCompile!(shader,renderer)
    }
    bottomLayerMaterial.customProgramCacheKey=()=> 'botanical-bottom-layer-detail-2'
    bottomLayerDepthMaterial.onBeforeCompile=(shader,renderer)=>{
      flowerDepthMaterial.onBeforeCompile(shader,renderer)
    }
    bottomLayerDepthMaterial.customProgramCacheKey=()=> 'botanical-bottom-layer-depth-2'
    referenceBottomLayer.customDepthMaterial=bottomLayerDepthMaterial
    referenceBottomLayer.castShadow=referenceBottomLayer.receiveShadow=true;scene.add(referenceBottomLayer)
  }
  // 只增补左下空隙的五叶小枝；沿用主体高度、法线与阴影深度，周围植物保持原状。
  const gapSprigMaterial=flowerMaterial?.clone()
  const referenceGapSprig=gapSprigMaterial ? new THREE.Mesh(makeReferenceGapSprig(),gapSprigMaterial) : undefined
  if(referenceGapSprig&&gapSprigMaterial&&flowerMaterial){
    gapSprigMaterial.onBeforeCompile=referenceProjectionCompile!
    gapSprigMaterial.customProgramCacheKey=()=> 'botanical-gap-sprig-detail-1'
    referenceGapSprig.customDepthMaterial=flowerDepthMaterial
    referenceGapSprig.castShadow=referenceGapSprig.receiveShadow=true;scene.add(referenceGapSprig)
  }
  // 蕨叶独立使用本轮确认纹理，避免对灰绿与浅褐细节重复染色；原抬升/投影共享。
  const fernMaterial=flowerMaterial?.clone()
  const referenceFern=fernMaterial ? new THREE.Mesh(makeReferenceFern(),fernMaterial) : undefined
  if(referenceFern&&fernMaterial&&referenceProjectionCompile){
    // 正面颜色以高清参考为基准，不再叠加 ACES 或二次灯光混合；真实薄壳继续投影。
    fernMaterial.toneMapped=false
    fernMaterial.onBeforeCompile=(shader,renderer)=>{
      referenceProjectionCompile(shader,renderer)
      anchorRoot(shader)
      shader.fragmentShader=shader.fragmentShader.replace('.88*referenceReveal','referenceReveal')
      shader.fragmentShader=shader.fragmentShader.replace('#include <opaque_fragment>',`
        #ifdef USE_MAP
          if(texture2D(map,vMapUv).a<.90)discard;
        #endif
        #include <opaque_fragment>
      `)
    }
    fernMaterial.customProgramCacheKey=()=> 'botanical-traced-fern-root-reference-5'
    referenceFern.customDepthMaterial=rootedDepthMaterial
    referenceFern.castShadow=referenceFern.receiveShadow=true;scene.add(referenceFern)
  }
  // 左下新增三组配植独立保留原色；连续根茎、贴墙过渡和深度投影共享现有方式。
  const bottomLeftMaterial=flowerMaterial?.clone()
  const referenceBottomLeft=bottomLeftMaterial ? new THREE.Mesh(makeReferenceBottomLeftAdditions(),bottomLeftMaterial) : undefined
  if(referenceBottomLeft&&bottomLeftMaterial&&referenceProjectionCompile){
    bottomLeftMaterial.toneMapped=false
    bottomLeftMaterial.onBeforeCompile=(shader,renderer)=>{
      referenceProjectionCompile(shader,renderer);anchorRoot(shader)
      shader.fragmentShader=shader.fragmentShader.replace('.88*referenceReveal','referenceReveal')
      shader.fragmentShader=shader.fragmentShader.replace('#include <opaque_fragment>',`
        #ifdef USE_MAP
          if(texture2D(map,vMapUv).a<.90)discard;
        #endif
        #include <opaque_fragment>
      `)
    }
    bottomLeftMaterial.customProgramCacheKey=()=> 'botanical-bottom-left-additions-root-1'
    referenceBottomLeft.customDepthMaterial=rootedDepthMaterial
    referenceBottomLeft.castShadow=referenceBottomLeft.receiveShadow=true;scene.add(referenceBottomLeft)
  }
  // 左侧确认双竹独立叠入；保留灰绿原色，根部与竹叶共享现有抬升和投影。
  const bambooMaterial=flowerMaterial?.clone()
  const referenceBamboo=bambooMaterial ? new THREE.Mesh(makeReferenceBamboo(),bambooMaterial) : undefined
  if(referenceBamboo&&bambooMaterial&&referenceProjectionCompile){
    bambooMaterial.toneMapped=false
    bambooMaterial.onBeforeCompile=(shader,renderer)=>{
      referenceProjectionCompile(shader,renderer);anchorRoot(shader)
      shader.fragmentShader=shader.fragmentShader.replace('.88*referenceReveal','referenceReveal')
      shader.fragmentShader=shader.fragmentShader.replace('#include <opaque_fragment>',`
        #ifdef USE_MAP
          if(texture2D(map,vMapUv).a<.90)discard;
        #endif
        #include <opaque_fragment>
      `)
    }
    bambooMaterial.customProgramCacheKey=()=> 'botanical-bamboo-reference-root-1'
    referenceBamboo.customDepthMaterial=rootedDepthMaterial
    referenceBamboo.castShadow=referenceBamboo.receiveShadow=true;scene.add(referenceBamboo)
  }
  // 逐株验收的第一株竹笋独立接入；沿用竹子的根部、法线和同公式阴影，不改已有植物。
  const shootMaterial=flowerMaterial?.clone()
  const referenceShoot=shootMaterial ? new THREE.Mesh(makeReferenceShoot(),shootMaterial) : undefined
  if(referenceShoot&&shootMaterial&&bambooMaterial){
    shootMaterial.toneMapped=false
    shootMaterial.onBeforeCompile=bambooMaterial.onBeforeCompile
    shootMaterial.customProgramCacheKey=()=> 'botanical-first-shoot-reference-root-1'
    referenceShoot.customDepthMaterial=rootedDepthMaterial
    referenceShoot.castShadow=referenceShoot.receiveShadow=true;scene.add(referenceShoot)
  }
  // 双长竹逐株补齐，第一根独立使用确认图轮廓；根秆、叶柄与阴影同场连续变形。
  const tallBambooMaterial=flowerMaterial?.clone()
  const referenceTallBamboo=tallBambooMaterial ? new THREE.Mesh(makeReferenceTallBamboo(),tallBambooMaterial) : undefined
  if(referenceTallBamboo&&tallBambooMaterial&&bambooMaterial){
    tallBambooMaterial.toneMapped=false
    tallBambooMaterial.onBeforeCompile=bambooMaterial.onBeforeCompile
    tallBambooMaterial.customProgramCacheKey=()=> 'botanical-first-tall-bamboo-reference-root-1'
    referenceTallBamboo.customDepthMaterial=rootedDepthMaterial
    referenceTallBamboo.castShadow=referenceTallBamboo.receiveShadow=true;scene.add(referenceTallBamboo)
  }
  // 第二株长竹的右向叶簇与第一株错开，单独纹理及几何保留原有模型；显现和阴影同场。
  const secondTallBambooMaterial=flowerMaterial?.clone()
  const referenceSecondTallBamboo=secondTallBambooMaterial ? new THREE.Mesh(makeReferenceSecondTallBamboo(),secondTallBambooMaterial) : undefined
  if(referenceSecondTallBamboo&&secondTallBambooMaterial&&bambooMaterial){
    secondTallBambooMaterial.toneMapped=false
    secondTallBambooMaterial.onBeforeCompile=bambooMaterial.onBeforeCompile
    secondTallBambooMaterial.customProgramCacheKey=()=> 'botanical-second-tall-bamboo-reference-root-1'
    referenceSecondTallBamboo.customDepthMaterial=rootedDepthMaterial
    referenceSecondTallBamboo.castShadow=referenceSecondTallBamboo.receiveShadow=true;scene.add(referenceSecondTallBamboo)
  }
  // 右侧竹笋独立补入双长竹与旧细枝之间，笋壳、嫩芽和同公式阴影沿现有影响场显现。
  const rightShootMaterial=flowerMaterial?.clone()
  const referenceRightShoot=rightShootMaterial ? new THREE.Mesh(makeReferenceRightShoot(),rightShootMaterial) : undefined
  if(referenceRightShoot&&rightShootMaterial&&bambooMaterial){
    rightShootMaterial.toneMapped=false
    rightShootMaterial.onBeforeCompile=bambooMaterial.onBeforeCompile
    rightShootMaterial.customProgramCacheKey=()=> 'botanical-right-shoot-reference-root-1'
    referenceRightShoot.customDepthMaterial=rootedDepthMaterial
    referenceRightShoot.castShadow=referenceRightShoot.receiveShadow=true;scene.add(referenceRightShoot)
  }
  // 下一组双笋中的左侧高笋独立保留叠壳和嫩芽，已完成的形态与位置保持原状。
  const pairTallShootMaterial=flowerMaterial?.clone()
  const referencePairTallShoot=pairTallShootMaterial ? new THREE.Mesh(makeReferencePairTallShoot(),pairTallShootMaterial) : undefined
  if(referencePairTallShoot&&pairTallShootMaterial&&bambooMaterial){
    pairTallShootMaterial.toneMapped=false
    pairTallShootMaterial.onBeforeCompile=bambooMaterial.onBeforeCompile
    pairTallShootMaterial.customProgramCacheKey=()=> 'botanical-pair-tall-shoot-reference-root-1'
    referencePairTallShoot.customDepthMaterial=rootedDepthMaterial
    referencePairTallShoot.castShadow=referencePairTallShoot.receiveShadow=true;scene.add(referencePairTallShoot)
  }
  // 双笋右侧小笋独立描出较细直立形态，与高笋留出原参考的间距；阴影和显现共享。
  const pairShortShootMaterial=flowerMaterial?.clone()
  const referencePairShortShoot=pairShortShootMaterial ? new THREE.Mesh(makeReferencePairShortShoot(),pairShortShootMaterial) : undefined
  if(referencePairShortShoot&&pairShortShootMaterial&&bambooMaterial){
    pairShortShootMaterial.toneMapped=false
    pairShortShootMaterial.onBeforeCompile=bambooMaterial.onBeforeCompile
    pairShortShootMaterial.customProgramCacheKey=()=> 'botanical-pair-short-shoot-reference-root-1'
    referencePairShortShoot.customDepthMaterial=rootedDepthMaterial
    referencePairShortShoot.castShadow=referencePairShortShoot.receiveShadow=true;scene.add(referencePairShortShoot)
  }
  // 中下部新增短竹逐株验收；连续根秆、细叶柄及投影沿用同一鼠标场，已有资产保持不变。
  const centerBambooMaterial=flowerMaterial?.clone()
  const referenceCenterBamboo=centerBambooMaterial ? new THREE.Mesh(makeReferenceCenterBamboo(),centerBambooMaterial) : undefined
  if(referenceCenterBamboo&&centerBambooMaterial&&bambooMaterial){
    centerBambooMaterial.toneMapped=false
    centerBambooMaterial.onBeforeCompile=bambooMaterial.onBeforeCompile
    centerBambooMaterial.customProgramCacheKey=()=> 'botanical-centre-short-bamboo-reference-root-1'
    referenceCenterBamboo.customDepthMaterial=rootedDepthMaterial
    referenceCenterBamboo.castShadow=referenceCenterBamboo.receiveShadow=true;scene.add(referenceCenterBamboo)
  }
  // 中右部后侧细竹单独补齐；枝根与叶柄连续，和原有裂叶保留前后关系，共享显现及投影。
  const centerFineBambooMaterial=flowerMaterial?.clone()
  const referenceCenterFineBamboo=centerFineBambooMaterial ? new THREE.Mesh(makeReferenceCenterFineBamboo(),centerFineBambooMaterial) : undefined
  if(referenceCenterFineBamboo&&centerFineBambooMaterial&&bambooMaterial){
    centerFineBambooMaterial.toneMapped=false
    centerFineBambooMaterial.onBeforeCompile=bambooMaterial.onBeforeCompile
    centerFineBambooMaterial.customProgramCacheKey=()=> 'botanical-centre-fine-bamboo-reference-root-1'
    referenceCenterFineBamboo.customDepthMaterial=rootedDepthMaterial
    referenceCenterFineBamboo.castShadow=referenceCenterFineBamboo.receiveShadow=true;scene.add(referenceCenterFineBamboo)
  }
  // 整张确认图剩余植株统一补齐；独立材质沿用竹子的原色、根部过渡和共享阴影深度。
  // 克隆的是尚未叠加其他植物染色的材质，不改变已验收模型、全站灯光或鼠标场。
  const remainingBotanicals=flowerMaterial&&bambooMaterial ? makeReferenceRemainingBotanicals().map(({key,geometry,textureUrl})=>{
    const material=flowerMaterial.clone()
    material.toneMapped=false;material.onBeforeCompile=bambooMaterial.onBeforeCompile
    material.customProgramCacheKey=()=> 'botanical-remaining-reference-root-1-'+key
    const mesh=new THREE.Mesh(geometry,material)
    mesh.customDepthMaterial=rootedDepthMaterial
    mesh.castShadow=mesh.receiveShadow=true;scene.add(mesh)
    return { mesh,textureUrl }
  }) : []
  // 亮部暖白、叶面自然色和卷褶浅褐色分层；依据原图局部纹理调色，夜色继续由 flowerTint 统一控制。
  function naturalPigment(material: THREE.MeshStandardMaterial | undefined, tint: [number,number,number], key: string) {
    if(!material)return
    const compile=material.onBeforeCompile
    const pigment=new THREE.Vector3(...tint)
    material.onBeforeCompile=(shader,renderer)=>{
      compile(shader,renderer)
      shader.uniforms.botanicalPigment={value:pigment}
      shader.fragmentShader='uniform vec3 botanicalPigment;\n'+shader.fragmentShader
      shader.fragmentShader=shader.fragmentShader.replace('#include <opaque_fragment>',`
        #ifdef USE_MAP
          float pigmentLight=dot(texture2D(map,vMapUv).rgb,vec3(.2126,.7152,.0722));
          float pigmentAmount=smoothstep(.12,.65,pigmentLight)*referenceReveal;
          // 原图与邻域均值之比保留叶脉/纤维高频变化，避免整片涂同一种颜色。
          float pigmentRelief=dot(detail/max(localMean,vec3(.08)),vec3(.2126,.7152,.0722));
          float pigmentGrain=clamp((pigmentRelief-1.)*3.,-.65,.65);
          float pigmentVariation=1.-smoothstep(.15,.85,dot(localMean,vec3(.2126,.7152,.0722)));
          vec3 pigmentBody=mix(vec3(.978,.977,.968),botanicalPigment,.70+.30*pigmentVariation);
          vec3 pigmentLayer=mix(pigmentBody,vec3(.973,.968,.953),smoothstep(.0,.48,pigmentGrain));
          pigmentLayer=mix(pigmentLayer,vec3(.988,.977,.954),smoothstep(.10,.60,-pigmentGrain)*.24);
          outgoingLight*=mix(vec3(1.),pigmentLayer,pigmentAmount);
          // 仅轻微增强原有细纹对比，真正投影仍由场景阴影负责。
          outgoingLight*=1.+pigmentGrain*.055*pigmentAmount;
        #endif
        #include <opaque_fragment>
      `)
    }
    material.customProgramCacheKey=()=> key+'-natural-pigment-layered-4'
  }
  naturalPigment(flowerMaterial,[1.008,1.001,.994],'flower-warm-ivory')
  naturalPigment(flowerStemMaterial,[.948,.953,.940],'flower-stem-sage')
  naturalPigment(broadleafMaterial,[.735,.775,.725],'broadleaf-grey-green')
  naturalPigment(ivyMaterial,[.755,.790,.744],'ivy-grey-green')
  naturalPigment(umbelMaterial,[.996,.993,.975],'umbel-pale-straw')
  naturalPigment(rightPairMaterial,[.800,.826,.776],'right-pair-olive-straw')
  naturalPigment(bottomLayerMaterial,[.760,.800,.745],'bottom-muted-green')
  naturalPigment(gapSprigMaterial,[.748,.786,.732],'gap-sprig-sage')
  // 加重桌面迷雾：未触及的浮雕片元完全隐藏，边缘沿原影响场柔和混合到实际墙面。
  // 包装已有材质回调，保留原图裁切、染色与几何；Menu/减弱动画模式仍可静态看见植物，手机不变。
  if(!mobile)scene.traverse(object=>{
    if(!(object instanceof THREE.Mesh)||!(object.material instanceof THREE.MeshStandardMaterial)||object===wall||object===relief)return
    const material=object.material,compile=material.onBeforeCompile,cacheKey=material.customProgramCacheKey()
    material.transparent=true
    material.onBeforeCompile=(shader,render)=>{
      compile(shader,render)
      shader.fragmentShader=shader.fragmentShader.replace('#include <opaque_fragment>',`
        float mistVisibility=botanicalMistWeight();
        if(mistVisibility<.001)discard;
        diffuseColor.a*=mistVisibility;
        #include <opaque_fragment>
      `)
    }
    material.customProgramCacheKey=()=> cacheKey+'-opaque-mist-1'
  })
  const textures: THREE.Texture[]=[];let failed=false,ready=false,firstPending=0,backgroundComplete=false
  const controller=new AbortController(),loader=new THREE.TextureLoader()
  type ReferenceMesh = THREE.Mesh<THREE.BufferGeometry,THREE.MeshStandardMaterial>
  type AssetJob = { mesh?: ReferenceMesh; url:string; first:boolean; bytes:number; apply?: (texture:THREE.Texture)=>void; received:number }
  const jobs:AssetJob[]=[]
  function addModel(mesh:ReferenceMesh|undefined,url:string){
    if(!mesh)return
    mesh.visible=false
    const key=referenceGeometryKey(mesh.geometry)
    jobs.push({mesh,url,first:['birds-wide','birds-gathered','ginkgo'].includes(key??''),bytes:referenceGeometryBytes(mesh.geometry)+botanicalTextureBytes(url),received:0})
  }
  referenceBirds.forEach((bird,index)=>addModel(bird,[wideBirdReferenceUrl,gatheredBirdReferenceUrl][index]!))
  addModel(referenceGinkgo,ginkgoReferenceUrl)
  addModel(referenceFlower,flowerReferenceUrl)
  addModel(referenceFlowerStems,flowerStemsReferenceUrl)
  addModel(referenceBamboo,bambooReferenceUrl)
  addModel(referenceTallBamboo,tallBambooReferenceUrl)
  addModel(referenceSecondTallBamboo,secondTallBambooReferenceUrl)
  addModel(referenceBroadleaf,broadleafReferenceUrl)
  addModel(referenceIvy,ivyReferenceUrl)
  addModel(referenceUmbel,umbelReferenceUrl)
  addModel(referenceLeftSprig,leftSprigReferenceUrl)
  addModel(referenceRightPair,rightPairReferenceUrl)
  addModel(referenceBottomLayer,bottomLayerReferenceUrl)
  addModel(referenceGapSprig,gapSprigReferenceUrl)
  addModel(referenceFern,fernReferenceUrl)
  addModel(referenceBottomLeft,bottomLeftAdditionsUrl)
  addModel(referenceShoot,shootReferenceUrl)
  addModel(referenceRightShoot,rightShootReferenceUrl)
  addModel(referencePairTallShoot,pairTallShootReferenceUrl)
  addModel(referencePairShortShoot,pairShortShootReferenceUrl)
  addModel(referenceCenterBamboo,centerBambooReferenceUrl)
  addModel(referenceCenterFineBamboo,centerFineBambooReferenceUrl)
  remainingBotanicals.forEach(({mesh,textureUrl})=>addModel(mesh,textureUrl))
  jobs.push({url:wallNormalUrl,first:true,bytes:wallAssetBytes.normal,received:0,apply:texture=>{
    texture.wrapS=texture.wrapT=THREE.RepeatWrapping;texture.repeat.set(5,3)
    wallMaterial.normalMap=stone.normalMap=texture;wallMaterial.normalScale.set(mobile ? .04 : .06,mobile ? .04 : .06);stone.normalScale.set(.012,.012);stone.needsUpdate=wallMaterial.needsUpdate=true
  }},{url:wallRoughUrl,first:true,bytes:wallAssetBytes.rough,received:0,apply:texture=>{
    texture.wrapS=texture.wrapT=THREE.RepeatWrapping;texture.repeat.set(5,3)
    wallMaterial.roughnessMap=stone.roughnessMap=texture;stone.needsUpdate=wallMaterial.needsUpdate=true
  }})
  const firstJobs=jobs.filter(job=>job.first),backgroundJobs=jobs.filter(job=>!job.first)
  firstPending=firstJobs.length
  const firstBytes=firstJobs.reduce((sum,job)=>sum+job.bytes,0)
  function reportProgress(){
    if(disposed||ready)return
    const received=firstJobs.reduce((sum,job)=>sum+Math.min(job.bytes,job.received),0)
    emit('progress',.05+.90*Math.min(1,received/firstBytes))
  }
  async function loadTexture(job:AssetJob,offset:number){
    const bytes=await fetchBotanicalBytes(job.url,controller.signal,n=>{job.received=offset+n;reportProgress()})
    const objectUrl=URL.createObjectURL(new Blob([bytes]))
    try{return await loader.loadAsync(objectUrl)}finally{URL.revokeObjectURL(objectUrl)}
  }
  async function runJob(job:AssetJob){
    try{
      if(job.mesh)await loadReferenceGeometry(job.mesh.geometry,controller.signal,n=>{job.received=n;reportProgress()})
      const texture=await loadTexture(job,job.mesh ? referenceGeometryBytes(job.mesh.geometry) : 0)
      if(disposed){texture.dispose();return}
      textures.push(texture)
      if(job.mesh){
        texture.colorSpace=THREE.SRGBColorSpace;job.mesh.material.map=texture;job.mesh.material.needsUpdate=true
        job.mesh.visible=true;job.mesh.userData.loadedAt=performance.now()
        if(!job.first&&!props.reducedMotion)job.mesh.material.opacity=0
      }
      else job.apply?.(texture)
    }catch(error){
      if(disposed||controller.signal.aborted)return
      failed=true;emit('failure');console.warn('Botanical asset failed',job.mesh ? referenceGeometryKey(job.mesh.geometry) : job.url,error)
    }finally{
      if(!disposed){job.received=job.bytes;if(job.first)firstPending--;reportProgress();requestDraw()}
    }
  }
  async function runQueue(queue:AssetJob[]){
    // 两个并发槽优先服务首屏；后台队列只在揭幕完成后启动，避免占用首屏带宽。
    const pending=[...queue]
    await Promise.all([0,1].map(async()=>{while(pending.length&&!disposed)await runJob(pending.shift()!)}))
    if(!disposed&&queue===backgroundJobs){backgroundComplete=true;status.value=failed ? 'simplified' : 'complete';requestDraw()}
  }
  let backgroundStarted=false
  function startBackground(){
    if(backgroundStarted||!ready||(!props.skipOpening&&props.reveal<.99))return
    backgroundStarted=true;void runQueue(backgroundJobs)
  }
  const stopBackgroundWatch=watch(()=>[props.reveal,props.skipOpening],startBackground)
  emit('progress',.05)
  void runQueue(firstJobs)
  let index=0,lastTime=0,lastMove=-10,inside=false,energy=0,aspect=1
  const targetPointer=new THREE.Vector2(.5,.5),easedPointer=targetPointer.clone(),previousPointer=targetPointer.clone()
  let pressure=0,radius=.15,motion=0
  const autoPointer=flowUniforms.autoPointer.value,autoStart=new THREE.Vector2(),autoEnd=new THREE.Vector2()
  let autoRunning=false,autoPaused=false,autoSegments=0,autoAxis=0,autoElapsed=0,autoDuration=0,autoSeed=0,autoTimer=0
  function loadedAutomaticPoint(){
    const loaded=jobs.filter(job=>job.mesh?.visible)
    const mesh=loaded[Math.floor(Math.random()*loaded.length)]?.mesh
    const center=mesh?.geometry.boundingSphere?.center
    if(!mesh||!center)return new THREE.Vector2(.5,.5)
    const x=.5+center.x*mesh.scale.x/(uniforms.botanicalExtent.value.x*2)
    const y=.5+center.y/(uniforms.botanicalExtent.value.y*2)
    return new THREE.Vector2(THREE.MathUtils.clamp(x+(Math.random()-.5)*.12,.05,.95),THREE.MathUtils.clamp(y+(Math.random()-.5)*.12,.05,.95))
  }
  function stopAutomaticReveal(){
    window.clearTimeout(autoTimer);autoTimer=0;autoRunning=false;autoPaused=false
    autoPointer.set(-1,-1);flowUniforms.autoStrength.value=0
  }
  function nextAutomaticSegment(){
    autoStart.copy(autoPointer)
    const angle=Math.random()*Math.PI*2,distance=.35+Math.random()*.1
    autoEnd.set(.5+Math.cos(angle)*distance,.5+Math.sin(angle)*distance)
    // 分批加载期间只扫过已就绪对象；全部补齐后恢复原有全画面随机轨迹。
    if(!backgroundComplete)autoEnd.copy(loadedAutomaticPoint())
    autoAxis=0;autoElapsed=0;autoDuration=.7+Math.random()*.3;autoSeed=Math.random()*Math.PI*2
  }
  function updateAutomaticReveal(dt:number,allowed:boolean){
    if(!allowed){stopAutomaticReveal();return}
    if(autoPaused)return
    if(!autoRunning){
      autoRunning=true;autoSegments=1+Math.floor(Math.random()*3)
      if(backgroundComplete)autoPointer.set(.05+Math.random()*.9,.05+Math.random()*.9)
      else autoPointer.copy(loadedAutomaticPoint())
      nextAutomaticSegment()
    }
    autoElapsed+=dt
    const progress=Math.min(1,autoElapsed/autoDuration)
    // 参考站逐轴扫过、随机变速的虚拟指针；直接复用场景帧循环，隐藏页不追赶过期轨迹。
    const eased=THREE.MathUtils.clamp(progress+Math.sin(progress*Math.PI)*Math.sin(progress*Math.PI*6+autoSeed)*.06,0,1)
    if(autoAxis===0)autoPointer.x=THREE.MathUtils.lerp(autoStart.x,autoEnd.x,eased)
    else autoPointer.y=THREE.MathUtils.lerp(autoStart.y,autoEnd.y,eased)
    flowUniforms.autoStrength.value=1
    if(progress<1)return
    if(autoAxis===0){autoAxis=1;autoElapsed=0;return}
    if(--autoSegments>0){nextAutomaticSegment();return}
    autoPointer.set(-1,-1);flowUniforms.autoStrength.value=0;autoRunning=false;autoPaused=true
    // 间歇期间只让旧场自然消退；消退后停止渲染，用定时唤醒下一轮，卸载时取消。
    autoTimer=window.setTimeout(()=>{autoTimer=0;autoPaused=false;requestDraw()},(1+Math.random()*2)*1000)
  }
  function pointer(event:PointerEvent){
    if(hideMistForPageReview)return
    if(props.reducedMotion || props.menu>.01 || props.scroll>.15 || props.content>.01){if(!mobile)leave();return}
    if(!mobile){
      const bounds=container.getBoundingClientRect()
      // 驻留只属于实际可见场景内的指针；移出或进入已有排除控件时沿原插值回浅。
      if(event.clientX<bounds.left || event.clientX>=bounds.right || event.clientY<bounds.top || event.clientY>=bounds.bottom){leave();return}
    }
    if(event.target instanceof Element && event.target.closest('button,a,input,select,textarea')){if(!mobile)leave();return}
    inside=true;lastMove=performance.now()*.001
    targetPointer.set(event.clientX/window.innerWidth,1-event.clientY/window.innerHeight);requestDraw()
  }
  function leave(){inside=false;requestDraw()}
  // 仅创建/resize 时求界，动态抬起仍在同一包络内；不在逐帧 draw 中遍历几何。
  const shadowPoint=new THREE.Vector3()
  function fitMobileShadow(){
    sun.updateMatrixWorld(true);sun.target.updateMatrixWorld(true);sun.shadow.updateMatrices(sun);relief.updateMatrixWorld(true)
    const shadowCamera=sun.shadow.camera,view=shadowCamera.matrixWorldInverse,positions=relief.geometry.getAttribute('position')
    let left=Infinity,right=-Infinity,bottom=Infinity,top=-Infinity
    function include(){
      shadowPoint.applyMatrix4(view)
      left=Math.min(left,shadowPoint.x);right=Math.max(right,shadowPoint.x);bottom=Math.min(bottom,shadowPoint.y);top=Math.max(top,shadowPoint.y)
    }
    // 与可见/customDepthMaterial 的 .016+z*depth 位置方程一致；保留全部屏幕外花梗/叶片的投影。
    const baseDepth=uniforms.botanicalBaseDepth.value,depths=[baseDepth,baseDepth+uniforms.botanicalLiftDepth.value]
    for(let i=0;i<positions.count;i++)for(const depth of depths){
      shadowPoint.set(positions.getX(i),positions.getY(i),.016+positions.getZ(i)*depth).applyMatrix4(relief.matrixWorld);include()
    }
    // 实际可见墙面由当前正交相机裁面限定；墙面屏幕外完整60×40平面无需占满贴图。
    for(const x of [camera.left,camera.right])for(const y of [camera.bottom,camera.top]){
      shadowPoint.set(x,y,wall.position.z);include()
    }
    // radius 的归一化邻域随新视锥跨度变化，解出含自身padding的保守覆盖；.15保留已验证的接触/边缘余量。
    const filter=(sun.shadow.radius+.5)/sun.shadow.mapSize.x
    const marginX=Math.max(.15,(sun.shadow.normalBias+filter*(right-left))/(1-2*filter))
    const marginY=Math.max(.15,(sun.shadow.normalBias+filter*(top-bottom))/(1-2*filter))
    shadowCamera.left=left-marginX;shadowCamera.right=right+marginX;shadowCamera.bottom=bottom-marginY;shadowCamera.top=top+marginY
    shadowCamera.updateProjectionMatrix()
  }
  function resize(){
    const w=container.clientWidth,h=container.clientHeight;aspect=w/Math.max(h,1)
    camera.left=-4*aspect;camera.right=4*aspect;camera.updateProjectionMatrix()
    relief.scale.x=aspect/(mobile ? 390/844 : 16/9);uniforms.botanicalScale.value.set(relief.scale.x,1);uniforms.botanicalExtent.value.set(4*aspect,4)
    if(referenceFlower)referenceFlower.scale.x=relief.scale.x
    if(referenceFlowerStems)referenceFlowerStems.scale.x=relief.scale.x
    if(referenceGinkgo)referenceGinkgo.scale.x=relief.scale.x
    if(referenceBroadleaf)referenceBroadleaf.scale.x=relief.scale.x
    if(referenceIvy)referenceIvy.scale.x=relief.scale.x
    if(referenceLeftSprig)referenceLeftSprig.scale.x=relief.scale.x
    if(referenceUmbel)referenceUmbel.scale.x=relief.scale.x
    if(referenceRightPair)referenceRightPair.scale.x=relief.scale.x
    if(referenceBottomLayer)referenceBottomLayer.scale.x=relief.scale.x
    if(referenceGapSprig)referenceGapSprig.scale.x=relief.scale.x
    if(referenceFern)referenceFern.scale.x=relief.scale.x
    if(referenceBottomLeft)referenceBottomLeft.scale.x=relief.scale.x
    if(referenceBamboo)referenceBamboo.scale.x=relief.scale.x
    if(referenceShoot)referenceShoot.scale.x=relief.scale.x
    if(referenceTallBamboo)referenceTallBamboo.scale.x=relief.scale.x
    if(referenceSecondTallBamboo)referenceSecondTallBamboo.scale.x=relief.scale.x
    if(referenceRightShoot)referenceRightShoot.scale.x=relief.scale.x
    if(referencePairTallShoot)referencePairTallShoot.scale.x=relief.scale.x
    if(referencePairShortShoot)referencePairShortShoot.scale.x=relief.scale.x
    if(referenceCenterBamboo)referenceCenterBamboo.scale.x=relief.scale.x
    if(referenceCenterFineBamboo)referenceCenterFineBamboo.scale.x=relief.scale.x
    remainingBotanicals.forEach(({mesh})=>{mesh.scale.x=relief.scale.x})
    referenceBirds.forEach(bird=>{bird.scale.x=relief.scale.x})
    if(mobile)fitMobileShadow()
    flowUniforms.aspect.value=aspect;gl.setSize(w,h);requestDraw()
  }
  function draw(now:number){
    frame=0;if(disposed||document.hidden)return
    const time=now*.001,dt=Math.min(.05,lastTime ? time-lastTime : 1/60);lastTime=time
    previousPointer.copy(easedPointer);easedPointer.lerp(targetPointer,1-Math.exp(-dt*10))
    // 页面状态可能在没有新pointer事件时改变，桌面驻留也必须随已有交互禁用条件结束。
    if(!mobile&&(props.reducedMotion || props.menu>.01 || props.scroll>.15 || props.content>.01))inside=false
    const idle=Math.max(0,time-lastMove),moving=inside&&idle<.10
    // 手机保留原衰减；桌面驻留持续写入，离场沿原强度插值回零。
    const desiredPressure=inside ? (mobile ? Math.max(0,1-idle/2.15) : 1) : 0
    pressure+=(desiredPressure-pressure)*(1-Math.exp(-dt*8))
    const desiredRadius=moving ? .17 : .035+.135*Math.max(0,1-idle/2.15)
    if(mobile)radius+=(Math.min(1,aspect)*desiredRadius-radius)*(1-Math.exp(-dt*5))
    else radius=.19
    const enabled=!props.reducedMotion&&props.menu<.01&&props.scroll<.15
    updateAutomaticReveal(dt,!mobile&&!hideMistForPageReview&&ready&&enabled&&props.content<.01&&props.reveal>=.99)
    flowUniforms.pointer.value.copy(easedPointer);flowUniforms.velocity.value.subVectors(easedPointer,previousPointer)
    // 速度按内容区高度/秒计量，正常慢移也能写入运动场；停止后只平滑释放速度，不设置收缩时间线。
    const speed=inside ? Math.min(1,Math.hypot(flowUniforms.velocity.value.x*aspect,flowUniforms.velocity.value.y)/dt*8) : 0
    motion+=(speed-motion)*(1-Math.exp(-dt*(speed>motion ? 12 : 4)))
    flowUniforms.motion.value=motion
    flowUniforms.radius.value=radius;flowUniforms.strength.value=enabled ? pressure : 0;flowUniforms.clock.value=time
    flowUniforms.decay.value=Math.exp(-dt*(mobile ? 2.3 : 3));flowUniforms.response.value=1-flowUniforms.decay.value;flowUniforms.previous.value=targets[index]!.texture
    const next=1-index;gl.setRenderTarget(targets[next]!);gl.render(flowScene,flowCamera);gl.setRenderTarget(null);index=next
    uniforms.botanicalFlow.value=targets[index]!.texture
    uniforms.botanicalActive.value+=((enabled ? 1 : 0)-uniforms.botanicalActive.value)*(1-Math.exp(-dt*8))
    energy=Math.max(pressure,flowUniforms.autoStrength.value,energy*Math.exp(-dt*2.3))
    const menu=props.menu
    shadowUniforms.botanicalShadowMenu.value=menu
    shadowUniforms.botanicalMistStatic.value=props.reducedMotion ? 1 : 0
    ambient.intensity=mobile ? 1.25-menu*.85 : ambientBase+(.40-ambientBase)*menu
    sun.intensity=mobile ? 2.9-menu*2.1 : sunBase+(.80-sunBase)*menu
    stone.color.copy(paper).lerp(night,menu);wallMaterial.color.copy(paper).lerp(night,menu)
    flowerMaterial?.color.copy(stone.color)
    flowerStemMaterial?.color.copy(stone.color)
    ginkgoMaterial?.color.copy(stone.color)
    broadleafMaterial?.color.copy(stone.color)
    ivyMaterial?.color.copy(stone.color)
    leftSprigMaterial?.color.copy(stone.color)
    umbelMaterial?.color.copy(stone.color)
    rightPairMaterial?.color.copy(stone.color)
    bottomLayerMaterial?.color.copy(stone.color)
    gapSprigMaterial?.color.copy(stone.color)
    fernMaterial?.color.copy(stone.color)
    bottomLeftMaterial?.color.copy(stone.color)
    bambooMaterial?.color.copy(stone.color)
    shootMaterial?.color.copy(stone.color)
    tallBambooMaterial?.color.copy(stone.color)
    secondTallBambooMaterial?.color.copy(stone.color)
    rightShootMaterial?.color.copy(stone.color)
    pairTallShootMaterial?.color.copy(stone.color)
    pairShortShootMaterial?.color.copy(stone.color)
    centerBambooMaterial?.color.copy(stone.color)
    centerFineBambooMaterial?.color.copy(stone.color)
    remainingBotanicals.forEach(({mesh})=>mesh.material.color.copy(stone.color))
    referenceBirds.forEach(bird=>bird.material.color.copy(stone.color))
    flowerTint.value.set(stone.color.r/paper.r,stone.color.g/paper.g,stone.color.b/paper.b)
    backdrop.copy(paper).lerp(nightBackground,menu)
    container.style.opacity=String(1-Math.min(props.scroll,1)*.97*(1-menu))
    let assetsFading=false
    for(const job of jobs){
      if(!job.mesh?.visible||job.mesh.material.opacity===1)continue
      // 晚到的网格沿现有迷雾柔和接入；鼠标场、抬升和阴影公式不变。
      job.mesh.material.opacity=props.reducedMotion ? 1 : Math.min(1,(now-job.mesh.userData.loadedAt)/600)
      assetsFading ||= job.mesh.material.opacity<1
    }
    gl.render(scene,camera)
    if(firstPending===0&&!ready){ready=true;status.value=failed ? 'simplified' : 'ready';emit('progress',1);emit('ready',{failed});startBackground()}
    // 桌面驻留与自动轨迹共同接续影响场；间歇回浅后停止逐帧工作，指针、页面状态或下一轮轨迹再唤醒。
    if(energy>.002||pressure>.002||autoRunning||assetsFading)requestDraw()
  }
  requestDraw=()=>{if(!frame&&!disposed&&!document.hidden)frame=requestAnimationFrame(draw)}
  const visibility=()=>{lastTime=0;if(!mobile&&document.hidden){inside=false;stopAutomaticReveal()}requestDraw()}
  window.addEventListener('pointermove',pointer,{passive:true});window.addEventListener('pointerdown',pointer,{passive:true})
  document.documentElement.addEventListener('pointerleave',leave);document.addEventListener('visibilitychange',visibility)
  observer=new ResizeObserver(resize);observer.observe(container);resize()
  cleanup=()=>{
    controller.abort();stopBackgroundWatch()
    stopAutomaticReveal()
    window.removeEventListener('pointermove',pointer);window.removeEventListener('pointerdown',pointer)
    document.documentElement.removeEventListener('pointerleave',leave);document.removeEventListener('visibilitychange',visibility)
    textures.forEach(texture=>texture.dispose());targets.forEach(target=>target.dispose())
    sun.shadow.dispose()
    relief.geometry.dispose();wall.geometry.dispose();stone.dispose();wallMaterial.dispose();depthMaterial.dispose();flowPlane.geometry.dispose();flowMaterial.dispose()
    referenceFlower?.geometry.dispose();flowerMaterial?.dispose();flowerDepthMaterial?.dispose()
    referenceFlowerStems?.geometry.dispose();flowerStemMaterial?.dispose()
    referenceGinkgo?.geometry.dispose();ginkgoMaterial?.dispose()
    referenceBroadleaf?.geometry.dispose();broadleafMaterial?.dispose();broadleafDepthMaterial?.dispose()
    referenceIvy?.geometry.dispose();ivyMaterial?.dispose()
    referenceLeftSprig?.geometry.dispose();leftSprigMaterial?.dispose();rootedDepthMaterial?.dispose()
    referenceUmbel?.geometry.dispose();umbelMaterial?.dispose()
    referenceRightPair?.geometry.dispose();rightPairMaterial?.dispose()
    referenceBottomLayer?.geometry.dispose();bottomLayerMaterial?.dispose();bottomLayerDepthMaterial?.dispose()
    referenceGapSprig?.geometry.dispose();gapSprigMaterial?.dispose()
    referenceFern?.geometry.dispose();fernMaterial?.dispose()
    referenceBottomLeft?.geometry.dispose();bottomLeftMaterial?.dispose()
    referenceBamboo?.geometry.dispose();bambooMaterial?.dispose()
    referenceShoot?.geometry.dispose();shootMaterial?.dispose()
    referenceTallBamboo?.geometry.dispose();tallBambooMaterial?.dispose()
    referenceSecondTallBamboo?.geometry.dispose();secondTallBambooMaterial?.dispose()
    referenceRightShoot?.geometry.dispose();rightShootMaterial?.dispose()
    referencePairTallShoot?.geometry.dispose();pairTallShootMaterial?.dispose()
    referencePairShortShoot?.geometry.dispose();pairShortShootMaterial?.dispose()
    referenceCenterBamboo?.geometry.dispose();centerBambooMaterial?.dispose()
    referenceCenterFineBamboo?.geometry.dispose();centerFineBambooMaterial?.dispose()
    remainingBotanicals.forEach(({mesh})=>{mesh.geometry.dispose();mesh.material.dispose()})
    referenceBirds.forEach(bird=>{bird.geometry.dispose();bird.material.dispose()})
  }
})
// 黑色开屏自己控制显露；纹理就绪先提交首帧，揭幕后唤醒自动轨迹，不因未使用的色彩参数重绘。
watch(()=>[props.menu,props.content,props.scroll,props.reducedMotion,props.reveal],()=>requestDraw())
onBeforeUnmount(()=>{disposed=true;cancelAnimationFrame(frame);observer?.disconnect();cleanup();renderer?.dispose()})
</script>

<template>
  <div ref="host" class="corner-scene botanical-scene" :data-scene-status="status" aria-hidden="true">
    <div v-if="status==='fallback'" class="botanical-fallback"></div>
  </div>
</template>
