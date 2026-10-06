<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as THREE from 'three'
import { makeBotanicalRelief } from './botanicalRelief'
import wallNormalUrl from '../assets/beige_wall_001_nor_gl_1k.jpg'
import wallRoughUrl from '../assets/beige_wall_001_rough_1k.jpg'

const props = defineProps<{ menu: number; content: number; scroll: number; hover: number; reveal: number; openingTone: number; openingColor: string; reducedMotion: boolean; skipOpening: boolean }>()
const emit = defineEmits<{ progress: [value: number]; failure: []; ready: [result: { failed: boolean }] }>()
const host = ref<HTMLDivElement | null>(null)
const status = ref('loading')
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
  const scene=new THREE.Scene(), paper=new THREE.Color('#d8d6d0'), backdrop=paper.clone(), night=new THREE.Color('#555952'), nightBackground=new THREE.Color('#252b26')
  scene.background=backdrop
  const camera=new THREE.OrthographicCamera(-7.11,7.11,4,-4,.1,40)
  camera.position.set(0,0,15);camera.lookAt(0,0,0)
  const stone=new THREE.MeshStandardMaterial({color:'#d5d3cc',roughness:.97,side:THREE.DoubleSide})
  const wallMaterial=stone.clone(), wall=new THREE.Mesh(new THREE.PlaneGeometry(60,40),wallMaterial)
  wall.position.z=-.012;wall.receiveShadow=true;scene.add(wall)
  const relief=new THREE.Mesh(makeBotanicalRelief(mobile),stone)
  relief.castShadow=relief.receiveShadow=true;scene.add(relief)
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
  const flowUniforms={previous:{value:targets[0]!.texture},pointer:{value:new THREE.Vector2(.5,.5)},velocity:{value:new THREE.Vector2()},aspect:{value:1},radius:{value:.15},strength:{value:0},motion:{value:0},clock:{value:0},decay:{value:.95},response:{value:1}}
  const flowMaterial=new THREE.ShaderMaterial({uniforms:flowUniforms,depthTest:false,depthWrite:false,
    vertexShader:'varying vec2 flowUv;void main(){flowUv=uv;gl_Position=vec4(position.xy,0.,1.);}',
    fragmentShader:`
      uniform sampler2D previous;uniform vec2 pointer;uniform vec2 velocity;
      uniform float aspect,radius,strength,motion,clock,decay,response;varying vec2 flowUv;
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
        vec2 field=min(vec2(1.),history+stamp*response*vec2(2.2,3.6*motion));
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
    float botanicalDepth(vec2 p){vec2 st=p*botanicalScale/(botanicalExtent*2.)+.5;vec2 field=texture2D(botanicalFlow,st).rg;return botanicalBaseDepth+botanicalLiftDepth*${mobile ? 'field.r' : '((field.r+field.g)*.5)'}*botanicalActive;}
  `
  const shadowUniforms={botanicalFlow:uniforms.botanicalFlow,botanicalExtent:uniforms.botanicalExtent,botanicalActive:uniforms.botanicalActive,botanicalShadowMenu:{value:0}}
  function fieldShadow(shader:Parameters<THREE.MeshStandardMaterial['onBeforeCompile']>[0]){
    Object.assign(shader.uniforms,shadowUniforms)
    // 墙面和植物都按世界坐标读取同一场，只调已有定向光投影，不改变底色、光向或几何。
    shader.vertexShader='varying vec2 botanicalShadowPosition;\n'+shader.vertexShader
    shader.vertexShader=shader.vertexShader.replace('#include <project_vertex>','botanicalShadowPosition=(modelMatrix*vec4(transformed,1.)).xy;\n#include <project_vertex>')
    shader.fragmentShader=`
      uniform sampler2D botanicalFlow;uniform vec2 botanicalExtent;uniform float botanicalActive,botanicalShadowMenu;
      varying vec2 botanicalShadowPosition;
      float botanicalShadowWeight(){
        vec2 field=texture2D(botanicalFlow,botanicalShadowPosition/(botanicalExtent*2.)+.5).rg;
        float reveal=smoothstep(0.,1.,(field.r+field.g)*botanicalActive);
        // 无输入只留浅痕；Menu 平滑接回原夜色投影，避免交互场禁用时改变既有菜单背景。
        return mix(.06+.94*reveal,1.,botanicalShadowMenu);
      }
    `+shader.fragmentShader
    shader.fragmentShader=shader.fragmentShader.replace('#include <lights_fragment_begin>',THREE.ShaderChunk.lights_fragment_begin.replace('directionalLightShadow.shadowIntensity,','directionalLightShadow.shadowIntensity*botanicalShadowWeight(),'))
  }
  if(!mobile){wallMaterial.onBeforeCompile=fieldShadow;wallMaterial.customProgramCacheKey=()=> 'botanical-wall-field-shadow-1'}
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
  const textures: THREE.Texture[]=[];let completed=1,failed=false,ready=false
  emit('progress',1/3)
  const loader=new THREE.TextureLoader()
  function settled(error=false){if(disposed)return;completed++;failed ||= error;if(error)emit('failure');emit('progress',completed/3);requestDraw()}
  loader.load(wallNormalUrl,texture=>{
    if(disposed){texture.dispose();return}textures.push(texture);texture.wrapS=texture.wrapT=THREE.RepeatWrapping;texture.repeat.set(5,3)
    wallMaterial.normalMap=stone.normalMap=texture;wallMaterial.normalScale.set(.04,.04);stone.normalScale.set(.012,.012);stone.needsUpdate=wallMaterial.needsUpdate=true;settled()
  },undefined,()=>settled(true))
  loader.load(wallRoughUrl,texture=>{
    if(disposed){texture.dispose();return}textures.push(texture);texture.wrapS=texture.wrapT=THREE.RepeatWrapping;texture.repeat.set(5,3)
    wallMaterial.roughnessMap=stone.roughnessMap=texture;stone.needsUpdate=wallMaterial.needsUpdate=true;settled()
  },undefined,()=>settled(true))
  let index=0,lastTime=0,lastMove=-10,inside=false,energy=0,aspect=1
  const targetPointer=new THREE.Vector2(.5,.5),easedPointer=targetPointer.clone(),previousPointer=targetPointer.clone()
  let pressure=0,radius=.15,motion=0
  function pointer(event:PointerEvent){
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
    energy=Math.max(pressure,energy*Math.exp(-dt*2.3))
    const menu=props.menu
    shadowUniforms.botanicalShadowMenu.value=menu
    ambient.intensity=mobile ? 1.25-menu*.85 : ambientBase+(.40-ambientBase)*menu
    sun.intensity=mobile ? 2.9-menu*2.1 : sunBase+(.80-sunBase)*menu
    stone.color.copy(paper).lerp(night,menu);wallMaterial.color.copy(paper).lerp(night,menu)
    backdrop.copy(paper).lerp(nightBackground,menu)
    container.style.opacity=String(1-Math.min(props.scroll,1)*.97*(1-menu))
    gl.render(scene,camera)
    if(completed===3&&!ready){ready=true;status.value=failed ? 'simplified' : 'ready';emit('ready',{failed})}
    // 桌面场内驻留持续接续影响场；移出回浅后停止逐帧工作，场景可见、指针或状态变化时再唤醒。
    if(energy>.002||pressure>.002)requestDraw()
  }
  requestDraw=()=>{if(!frame&&!disposed&&!document.hidden)frame=requestAnimationFrame(draw)}
  const visibility=()=>{lastTime=0;if(!mobile&&document.hidden)inside=false;requestDraw()}
  window.addEventListener('pointermove',pointer,{passive:true});window.addEventListener('pointerdown',pointer,{passive:true})
  document.documentElement.addEventListener('pointerleave',leave);document.addEventListener('visibilitychange',visibility)
  observer=new ResizeObserver(resize);observer.observe(container);resize()
  cleanup=()=>{
    window.removeEventListener('pointermove',pointer);window.removeEventListener('pointerdown',pointer)
    document.documentElement.removeEventListener('pointerleave',leave);document.removeEventListener('visibilitychange',visibility)
    textures.forEach(texture=>texture.dispose());targets.forEach(target=>target.dispose())
    sun.shadow.dispose()
    relief.geometry.dispose();wall.geometry.dispose();stone.dispose();wallMaterial.dispose();depthMaterial.dispose();flowPlane.geometry.dispose();flowMaterial.dispose()
  }
})
// 黑色开屏自己控制显露；草木在纹理就绪时提交首帧，不因未使用的开屏参数重绘底层画布。
watch(()=>[props.menu,props.content,props.scroll,props.reducedMotion],()=>requestDraw())
onBeforeUnmount(()=>{disposed=true;cancelAnimationFrame(frame);observer?.disconnect();cleanup();renderer?.dispose()})
</script>

<template>
  <div ref="host" class="corner-scene botanical-scene" :data-scene-status="status" aria-hidden="true">
    <div v-if="status==='fallback'" class="botanical-fallback"></div>
  </div>
</template>
