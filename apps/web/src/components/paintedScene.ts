// 环境动效只影响植物与蒸气；人物、头发、手和笔必须保持原画不动。
// 坐标对应 dusk-writer-round.webp；更换构图时需同步校准杯口与植物区域。
export function createPaintedScene(canvas: HTMLCanvasElement, image: HTMLImageElement) {
  const gl = canvas.getContext('webgl', { alpha: false, antialias: false, powerPreference: 'low-power' })
  if (!gl) return null
  const vertexSource = `
    attribute vec2 point;
    varying vec2 uv;
    void main() { uv = point; gl_Position = vec4(point.x * 2.0 - 1.0, 1.0 - point.y * 2.0, 0.0, 1.0); }
  `
  const fragmentSource = `
    precision mediump float;
    varying vec2 uv;
    uniform sampler2D painting;
    uniform float time;
    float area(vec2 p, vec2 center, vec2 radius) {
      vec2 d = (p - center) / radius;
      return exp(-dot(d, d) * 2.0);
    }
    void main() {
      vec2 p = uv;
      float wind = sin(time * .67) * .65 + sin(time * 1.13 + 1.4) * .35;
      float rightLeaves = area(uv, vec2(.939, .18), vec2(.055, .24));
      float garden = area(uv, vec2(.70, .42), vec2(.13, .09));
      float foreground = area(uv, vec2(.969, .86), vec2(.047, .15));
      // 将人物所在左侧及执笔区域完全排除，避免高斯范围的尾部使人物轻微漂移。
      float environment = smoothstep(.70, .73, uv.x);
      p.x -= wind * (.0038 * rightLeaves + .0018 * garden + .005 * foreground) * environment;
      p.y -= sin(time * .8 + uv.y * 10.0) * .0015 * rightLeaves * environment;
      vec3 color = texture2D(painting, clamp(p, .001, .999)).rgb;
      // 蒸气从杯口出发，向上逐渐扩散；各缕有相位差，不形成同步的白色线条。
      float height = (.599 - uv.y) / .19;
      float steam = 0.0;
      if (height > 0.0 && height < 1.0) {
        float envelope = smoothstep(0.0, .12, height) * (1.0 - smoothstep(.5, 1.0, height));
        for (int i = 0; i < 3; i++) {
          float n = float(i);
          float center = .756 + (n - 1.0) * .009 + sin(height * 8.0 - time * .72 + n * 2.0) * (.003 + height * .006) + wind * height * .008;
          float width = .0025 + height * .005;
          float strand = exp(-pow((uv.x - center) / width, 2.0));
          float flow = .55 + .45 * sin(height * 12.0 - time * 1.4 + n * 2.0);
          steam += strand * envelope * flow * .095;
        }
      }
      color = mix(color, vec3(.91, .85, .76), min(steam, .24));
      gl_FragColor = vec4(color, 1.0);
    }
  `
  const shaders: WebGLShader[] = []
  function compile(type: number, source: string) {
    const shader = gl!.createShader(type)
    if (!shader) throw new Error('Scene shader unavailable')
    shaders.push(shader)
    gl!.shaderSource(shader, source)
    gl!.compileShader(shader)
    if (!gl!.getShaderParameter(shader, gl!.COMPILE_STATUS)) throw new Error('Scene shader compilation failed')
    return shader
  }
  let program: WebGLProgram | null = null
  let buffer: WebGLBuffer | null = null
  let texture: WebGLTexture | null = null
  const dispose = () => {
    if (texture) gl.deleteTexture(texture)
    if (buffer) gl.deleteBuffer(buffer)
    if (program) gl.deleteProgram(program)
    shaders.forEach(shader => gl.deleteShader(shader))
  }
  try {
    program = gl.createProgram()
    if (!program) throw new Error('Scene program unavailable')
    gl.attachShader(program, compile(gl.VERTEX_SHADER, vertexSource))
    gl.attachShader(program, compile(gl.FRAGMENT_SHADER, fragmentSource))
    gl.linkProgram(program)
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error('Scene link failed')
    gl.useProgram(program)
    buffer = gl.createBuffer()
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 1]), gl.STATIC_DRAW)
    const point = gl.getAttribLocation(program, 'point')
    gl.enableVertexAttribArray(point)
    gl.vertexAttribPointer(point, 2, gl.FLOAT, false, 0, 0)
    texture = gl.createTexture()
    gl.bindTexture(gl.TEXTURE_2D, texture)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image)
    gl.uniform1i(gl.getUniformLocation(program, 'painting'), 0)
    const time = gl.getUniformLocation(program, 'time')
    canvas.width = 1152
    canvas.height = 768
    gl.viewport(0, 0, canvas.width, canvas.height)
    return {
      draw(seconds: number) { gl.uniform1f(time, seconds); gl.drawArrays(gl.TRIANGLES, 0, 6) },
      dispose,
    }
  } catch {
    dispose()
    return null
  }
}
