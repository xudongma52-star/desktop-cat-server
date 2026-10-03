<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import catModelUrl from '../assets/relief-cat.glb?url'
import wallColorUrl from '../assets/beige_wall_001_diff_1k.jpg'
import wallNormalUrl from '../assets/beige_wall_001_nor_gl_1k.jpg'
import wallRoughUrl from '../assets/beige_wall_001_rough_1k.jpg'
const props = defineProps<{ menu: number; content: number; scroll: number; hover: number; reveal: number; openingTone: number; openingColor: string; reducedMotion: boolean; skipOpening: boolean }>()
const emit = defineEmits<{ progress: [value: number]; failure: []; ready: [result: { failed: boolean }] }>()
const host = ref<HTMLDivElement | null>(null)
const status = ref('loading')
let renderer: THREE.WebGLRenderer | undefined
let resizeObserver: ResizeObserver | undefined
let frame = 0, disposed = false
let requestDraw = () => {}
let cleanupEvents = () => {}
let scene: THREE.Scene | undefined
const textures: THREE.Texture[] = []
function disposeObjects(root: THREE.Object3D) {
  root.traverse(object => {
    if (!(object instanceof THREE.Mesh)) return
    object.geometry.dispose()
    ;(Array.isArray(object.material) ? object.material : [object.material]).forEach(material => {
      Object.values(material).forEach(value => { if (value instanceof THREE.Texture) value.dispose() })
      material.dispose()
    })
  })
}
onMounted(() => {
  if (!host.value) return
  try { renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, powerPreference: 'low-power' }) }
  catch {
    // 三维能力不可用时仍呈现静态轮廓，业务入口不依赖模型加载。
    status.value = 'fallback'; emit('failure'); emit('ready', { failed: true }); return
  }
  const canvasRenderer = renderer, container = host.value
  canvasRenderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.5))
  canvasRenderer.shadowMap.enabled = true
  canvasRenderer.shadowMap.type = THREE.VSMShadowMap
  canvasRenderer.toneMapping = THREE.ACESFilmicToneMapping
  canvasRenderer.toneMappingExposure = 1.25
  container.appendChild(canvasRenderer.domElement)
  scene = new THREE.Scene()
  const world = scene, sculpture = new THREE.Group()
  world.add(sculpture)
  const camera = new THREE.OrthographicCamera(-7,7,4,-4,.1,60)
  camera.position.set(0,1,12)
  camera.lookAt(0,0,0)
  let aspect = 1.8, pointerX = 0, pointerY = 0, smoothX = 0, smoothY = 0
  let resourcesSettled = false, readyEmitted = false, resourceFailures = 0
  const reveal = { value: 0 }, nightMix = { value: 0 }, resolution = { value: new THREE.Vector2(1,1) }
  const openingColor = { value: new THREE.Color() }, openingMix = { value: 0 }
  const plaster = new THREE.MeshStandardMaterial({ color: '#dfdcd3', roughness: .98 })
  const wallMaterial = plaster.clone()
  const wall = new THREE.Mesh(new THREE.PlaneGeometry(60,40),wallMaterial)
  wall.position.z = -.32; wall.receiveShadow = true; world.add(wall)
  const ledge = new THREE.Mesh(new THREE.BoxGeometry(4.15,.04,.24),plaster)
  ledge.position.set(0,-.12,-.20); ledge.castShadow = ledge.receiveShadow = true
  sculpture.add(ledge)
  // 开场改变实时材质的显露与侧光，不播放截图、图片帧或拼接视频。
  function revealMaterial(material: THREE.MeshStandardMaterial) {
    material.onBeforeCompile = shader => {
      shader.uniforms.cornerReveal = reveal; shader.uniforms.cornerResolution = resolution; shader.uniforms.cornerNight = nightMix
      shader.uniforms.cornerOpeningColor = openingColor; shader.uniforms.cornerOpeningMix = openingMix
      shader.fragmentShader = 'uniform float cornerReveal; uniform float cornerNight; uniform vec2 cornerResolution; uniform vec3 cornerOpeningColor; uniform float cornerOpeningMix;\n'+shader.fragmentShader
      if (material === wallMaterial) shader.fragmentShader = shader.fragmentShader.replace('#include <color_fragment>', `
        #include <color_fragment>
        float wallGrey = dot(diffuseColor.rgb,vec3(.2126,.7152,.0722));
        diffuseColor.rgb = (vec3(clamp(.85+(wallGrey-.30)*.60,.60,.96))+vec3(.005,.003,0.))*mix(1.,.12,cornerNight);
      `)
      if (material === wallMaterial) shader.fragmentShader = shader.fragmentShader.replace('#include <colorspace_fragment>', `
        #include <colorspace_fragment>
        // 在输出色域中承接网页同一纸色，保留纹理起伏，落定后完整恢复原墙面着色。
        float paperDetail = dot(gl_FragColor.rgb - vec3(.90), vec3(.2126,.7152,.0722));
        vec3 tintedPaper = cornerOpeningColor + vec3(paperDetail * .36);
        gl_FragColor.rgb = mix(gl_FragColor.rgb, tintedPaper, cornerOpeningMix * (1. - cornerNight));
      `)
      // 墙面保持接近最终纸色，避免加载层退去时整屏变暗；猫与枝叶仍保留明确的显露层次。
      shader.fragmentShader = shader.fragmentShader.replace('#include <opaque_fragment>',`
        float sweep = gl_FragCoord.x/cornerResolution.x + .12*gl_FragCoord.y/cornerResolution.y;
        float lit = smoothstep(sweep-.42,sweep+.12,cornerReveal*1.7);
        outgoingLight *= ${material === wallMaterial ? '.96+.04*lit' : '.42+.58*lit'};
        #include <opaque_fragment>
      `)
    }
    material.customProgramCacheKey = () => material === wallMaterial ? 'corner-opening-wall-2' : 'corner-opening-object-2'
  }
  revealMaterial(plaster); revealMaterial(wallMaterial)
  // 真实曲面叶片与叶脉共用侧光和投影，避免平面图案贴在背景上。
  const leaves: { group: THREE.Group; angle: number; phase: number }[] = []
  const leafMaterial = plaster.clone(); leafMaterial.side = THREE.DoubleSide; revealMaterial(leafMaterial)
  function leafGeometry(length: number, width: number) {
    const positions: number[] = [], uvs: number[] = [], indices: number[] = []
    for (let i=0;i<=16;i++) for (let j=0;j<3;j++) {
      const t = i/16, side = j-1
      positions.push(side*Math.pow(Math.sin(Math.PI*t),.8)*width,t*length,.07*Math.sin(Math.PI*t)+(1-Math.abs(side))*.035)
      uvs.push(j/2,t)
    }
    for (let i=0;i<16;i++) for (let j=0;j<2;j++) {
      const a=i*3+j; indices.push(a,a+1,a+3,a+1,a+4,a+3)
    }
    const geometry = new THREE.BufferGeometry()
    geometry.setAttribute('position',new THREE.Float32BufferAttribute(positions,3))
    geometry.setAttribute('uv',new THREE.Float32BufferAttribute(uvs,2)); geometry.setIndex(indices); geometry.computeVertexNormals()
    return geometry
  }
  const stem = new THREE.CatmullRomCurve3([new THREE.Vector3(1.6,-.2,-.265),new THREE.Vector3(1.88,.5,-.265),new THREE.Vector3(1.55,1.25,-.265),new THREE.Vector3(1.35,2.15,-.265),new THREE.Vector3(.94,2.70,-.265)])
  const branch = new THREE.Mesh(new THREE.TubeGeometry(stem,64,.018,6,false),plaster)
  branch.castShadow = true; sculpture.add(branch)
  const leafSlots = [.11,.23,.36,.44,.58,.69,.81,.91]
  const leafAngles = [.96,-.72,1.14,-.84,.75,-1.07,.86,-.62]
  const leafLengths = [.50,.70,.62,.58,.76,.65,.55,.48]
  for (let i=0;i<leafSlots.length;i++) {
    const group = new THREE.Group(), angle = leafAngles[i]!, length = leafLengths[i]!
    group.position.copy(stem.getPoint(leafSlots[i]!)); group.rotation.z = angle; group.rotation.y = Math.sin(i*1.9)*.2
    const leaf = new THREE.Mesh(leafGeometry(length,.14+i%3*.015),leafMaterial)
    leaf.castShadow = leaf.receiveShadow = true; group.add(leaf)
    const vein = new THREE.CatmullRomCurve3([new THREE.Vector3(0,0,.035),new THREE.Vector3(0,length*.5,.105),new THREE.Vector3(0,length,.035)])
    group.add(new THREE.Mesh(new THREE.TubeGeometry(vein,12,.008,4,false),plaster))
    sculpture.add(group); leaves.push({group,angle,phase:i*.83})
  }
  const ambient = new THREE.HemisphereLight('#ffffff','#bdc0bb',2.1)
  const sunlight = new THREE.DirectionalLight('#fffefa',2)
  sunlight.position.set(-3,5,11); sunlight.castShadow = true; sunlight.shadow.mapSize.set(2048,2048)
  sunlight.shadow.camera.left = sunlight.shadow.camera.bottom = -10
  sunlight.shadow.camera.right = sunlight.shadow.camera.top = 10
  sunlight.shadow.normalBias = .005; sunlight.shadow.bias = -.00005; sunlight.shadow.radius = 5; sunlight.shadow.blurSamples = 8
  const edge = new THREE.DirectionalLight('#e4e8e3',.3); edge.position.set(5,2,3)
  world.add(ambient,sunlight,edge)
  let cat: THREE.Group | undefined
  const breathing: { mesh: THREE.Mesh; base: Float32Array; amount: number[] }[] = []
  const day = new THREE.Color('#b8b7b0'), night = new THREE.Color('#999b94')
  const wallDay = new THREE.Color('#eeeae1'), wallNight = new THREE.Color('#353b35')
  // 统计首屏实际依赖的四份资源；失败也结束等待并单独告知，不表示全部加载成功，菜单不受加载层阻挡。
  const completed = new Set<string>()
  function resourceDone(key: string, failed = false) {
    if (disposed || completed.has(key)) return
    completed.add(key)
    if (failed) { resourceFailures++; emit('failure') }
    emit('progress',completed.size/4)
    if (completed.size === 4) {
      // 统一细颗粒的尺度和粗糙度，模型仍用原有 UV；不是给物体贴一张场景截图。
      cat?.traverse(object => {
        if (!(object instanceof THREE.Mesh)) return
        ;(Array.isArray(object.material) ? object.material : [object.material]).forEach(material => {
          if (material instanceof THREE.MeshStandardMaterial) {
            material.normalMap = wallMaterial.normalMap; material.normalScale.set(.035,.035)
            material.roughnessMap = wallMaterial.roughnessMap; material.needsUpdate = true
          }
        })
      })
      status.value = cat ? 'ready' : 'fallback'
      resourcesSettled = true; requestDraw()
    }
  }
  const textureLoader = new THREE.TextureLoader()
  function texture(url: string, key: string, apply: (value: THREE.Texture) => void) {
    textureLoader.load(url,value => {
      if (disposed) { value.dispose(); return }
      value.wrapS = value.wrapT = THREE.RepeatWrapping; value.repeat.set(5,3.3)
      textures.push(value); apply(value); wallMaterial.needsUpdate = true; resourceDone(key); requestDraw()
    },undefined,() => resourceDone(key, true))
  }
  texture(wallColorUrl,'color',value => { value.colorSpace = THREE.SRGBColorSpace; wallMaterial.map = value })
  texture(wallNormalUrl,'normal',value => { wallMaterial.normalMap = value; wallMaterial.normalScale.set(.18,.18) })
  texture(wallRoughUrl,'rough',value => { wallMaterial.roughnessMap = value })
  function draw(now: number) {
    frame = 0
    if (disposed || document.hidden) return
    const mobile = aspect < .85, menu = props.menu, content = props.content, scroll = props.scroll*(1-menu), time = now*.001
    smoothX += (pointerX-smoothX)*.08; smoothY += (pointerY-smoothY)*.08; nightMix.value = menu
    reveal.value = props.reducedMotion || props.skipOpening ? 1 : props.reveal
    openingColor.value.set(props.openingColor).convertLinearToSRGB()
    openingMix.value = props.reducedMotion || props.skipOpening ? 0 : Math.min(1, 2-props.openingTone)
    const compositionScale = (mobile ? .62 : 1.1)*(1-content*.6)*(mobile ? 1-menu*.65 : 1+menu*.08)
    // 取景只缩放墙面的平面方向，保持离墙深度，避免菜单放大后把枝干压进墙内。
    sculpture.scale.set(compositionScale,compositionScale,1)
    sculpture.position.set(mobile ? .2+content*2+menu*.85 : aspect*1.83+content*.8,mobile ? -1.8+content*3.4-menu*.45 : -.92+content*1.6-scroll*.4,.025)
    // 浅浮雕随墙面保持小角度，避免转动把猫脸/尾巴推入墙内；主要转场由光线承担。
    sculpture.rotation.y = .005+menu*.015+smoothX*.008; sculpture.rotation.x = smoothY*.012
    camera.position.x = menu*.2+smoothX*.03; camera.lookAt(menu*.2,0,0)
    ambient.intensity = 2.1-menu*1.15; sunlight.intensity = 2-menu*.9
    sunlight.position.x = -3-(1-reveal.value)*1.5+menu*.7+(props.reducedMotion ? 0 : Math.sin(time*.12)*.15)
    plaster.color.copy(day).lerp(night,menu); leafMaterial.color.copy(day).lerp(night,menu); wallMaterial.color.copy(wallDay).lerp(wallNight,menu)
    cat?.traverse(object => {
      if (object instanceof THREE.Mesh) (Array.isArray(object.material) ? object.material : [object.material]).forEach(material => {
        if (material instanceof THREE.MeshStandardMaterial) material.color.copy(day).lerp(night,menu)
      })
    })
    for (const leaf of leaves) leaf.group.rotation.z = leaf.angle+(props.reducedMotion ? 0 : Math.sin(time*.45+leaf.phase)*.009)
    // 仅腹部有限顶点进行毫米级呼吸；脚、脸和整体比例保持稳定。
    for (const item of breathing) {
      const attribute = item.mesh.geometry.attributes.position as THREE.BufferAttribute, positions = attribute.array as Float32Array
      const breath = props.reducedMotion ? 0 : Math.sin(time*.9)*.006
      for (let i=0;i<item.amount.length;i++) positions[i*3+1] = item.base[i*3+1]!+item.amount[i]!*breath
      attribute.needsUpdate = true
    }
    container.style.opacity = String(1-content*.80-Math.min(scroll,1)*.97*(1-content))
    canvasRenderer.render(world,camera)
    // 资源已处理且首帧提交后才交给首页开场；回调完成并不等于场景已可呈现。
    if (resourcesSettled && !readyEmitted) { readyEmitted = true; emit('ready', { failed: resourceFailures > 0 }) }
    if ((!props.reducedMotion && ((props.content < .99 && scroll < .99) || menu > .01)) || Math.abs(pointerX-smoothX)+Math.abs(pointerY-smoothY) > .002) requestDraw()
  }
  requestDraw = () => { if (!frame && !disposed && !document.hidden) frame = requestAnimationFrame(draw) }
  function resize() {
    const width=container.clientWidth,height=container.clientHeight; aspect=width/Math.max(height,1)
    camera.left=-4*aspect; camera.right=4*aspect; camera.updateProjectionMatrix()
    canvasRenderer.setSize(width,height); canvasRenderer.getDrawingBufferSize(resolution.value); requestDraw()
  }
  function pointer(event: PointerEvent) {
    if (props.reducedMotion || event.pointerType === 'touch') return
    pointerX=event.clientX/window.innerWidth-.5; pointerY=event.clientY/window.innerHeight-.5; requestDraw()
  }
  function visibility() { requestDraw() }
  window.addEventListener('pointermove',pointer,{passive:true}); document.addEventListener('visibilitychange',visibility)
  resizeObserver = new ResizeObserver(resize); resizeObserver.observe(container); resize()
  new GLTFLoader().load(catModelUrl,gltf => {
    if (disposed) { disposeObjects(gltf.scene); return }
    cat=gltf.scene; cat.position.set(-.24,-.09,-.14); cat.scale.set(.92,.92,.35)
    cat.traverse(object => {
      if (!(object instanceof THREE.Mesh)) return
      object.castShadow=object.receiveShadow=true
      ;(Array.isArray(object.material) ? object.material : [object.material]).forEach(material => {
        if (material instanceof THREE.MeshStandardMaterial) { material.roughness=.96; revealMaterial(material) }
      })
      const attribute=object.geometry.attributes.position as THREE.BufferAttribute, base=new Float32Array(attribute.array)
      const amount=Array.from({length:attribute.count},(_,i)=>Math.exp(-Math.pow((base[i*3]!-.35)/.7,2))*Math.exp(-Math.pow((base[i*3+1]!-.65)/.45,2)))
      breathing.push({mesh:object,base,amount})
    })
    sculpture.add(cat); resourceDone('cat')
  },undefined,() => resourceDone('cat', true))
  // 上述监听必须与画布一起释放，切换到登录页后不保留渲染工作。
  cleanupEvents = () => { window.removeEventListener('pointermove',pointer); document.removeEventListener('visibilitychange',visibility) }
})
watch(() => [props.menu,props.content,props.scroll,props.hover,props.reveal,props.openingTone,props.openingColor,props.reducedMotion,props.skipOpening],() => requestDraw())
onBeforeUnmount(() => {
  disposed=true; cancelAnimationFrame(frame); cleanupEvents(); resizeObserver?.disconnect()
  if (scene) disposeObjects(scene)
  textures.forEach(value => value.dispose()); renderer?.dispose()
})
</script>
<template>
  <div ref="host" class="corner-scene" :data-scene-status="status" aria-hidden="true">
    <svg v-if="status === 'fallback'" class="corner-scene-fallback" viewBox="0 0 300 330"><path d="M52 265c-7-41 15-85 71-85 47 0 86 22 93 50 12 41-59 51-118 28m-10-8-17-47-3-39 28 21 30-2 24-22 4 49c9 31-14 51-48 45m-14-26 9 3 8-4m13 0 9 3 8-4" /></svg>
  </div>
</template>
