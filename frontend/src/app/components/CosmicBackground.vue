<template>
  <canvas ref="canvasRef" class="cosmic-webgl-canvas" aria-hidden="true"></canvas>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

const canvasRef = ref<HTMLCanvasElement | null>(null)

// WebGL 背景由两个 shader 组成：
// vertex shader 负责铺满整个屏幕，fragment shader 负责给每个像素计算星云和星点颜色。
const vertexShaderSource = `
attribute vec2 aPosition;
varying vec2 vUv;

void main() {
  vUv = aPosition * 0.5 + 0.5;
  gl_Position = vec4(aPosition, 0.0, 1.0);
}
`

const fragmentShaderSource = `
precision highp float;

uniform vec2 uResolution;
uniform float uTime;
varying vec2 vUv;

float hash(vec2 p) {
  p = fract(p * vec2(123.34, 456.21));
  p += dot(p, p + 45.32);
  return fract(p.x * p.y);
}

vec2 hash2(vec2 p) {
  return vec2(hash(p), hash(p + 17.31));
}

float noise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);

  float a = hash(i);
  float b = hash(i + vec2(1.0, 0.0));
  float c = hash(i + vec2(0.0, 1.0));
  float d = hash(i + vec2(1.0, 1.0));

  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
  float value = 0.0;
  float amplitude = 0.5;
  mat2 rotation = mat2(1.58, 1.16, -1.16, 1.58);

  for (int i = 0; i < 6; i++) {
    value += amplitude * noise(p);
    p = rotation * p + 0.19;
    amplitude *= 0.52;
  }

  return value;
}

float starLayer(vec2 uv, float scale, float threshold, float size, float speed, float depth) {
  vec2 p = uv * scale;
  p += vec2(uTime * speed, sin(uTime * 0.08 + depth) * speed * 7.0);

  vec2 cell = floor(p);
  vec2 local = fract(p) - 0.5;
  float randomValue = hash(cell + depth * 41.0);
  local -= (hash2(cell + depth * 11.0) - 0.5) * 0.54;

  float distanceToStar = length(local);
  float core = 1.0 - smoothstep(0.0, size, distanceToStar);
  float halo = 1.0 - smoothstep(size * 0.4, size * 5.5, distanceToStar);
  float twinkle = 0.58 + 0.42 * sin(uTime * (1.0 + randomValue * 2.8) + randomValue * 6.28318);
  float mask = step(threshold, randomValue);

  return (core * 1.2 + halo * 0.24) * mask * twinkle;
}

void main() {
  vec2 uv = (gl_FragCoord.xy * 2.0 - uResolution.xy) / uResolution.y;
  vec2 centeredUv = vUv - 0.5;
  float time = uTime;

  vec2 slowFlow = vec2(time * 0.018, -time * 0.011);
  vec2 silkWarpA = vec2(
    fbm(uv * 1.08 + slowFlow),
    fbm(uv * 1.12 - slowFlow.yx + 4.17)
  );
  vec2 silkWarpB = vec2(
    fbm(uv * 2.05 + silkWarpA * 1.7 - slowFlow * 0.7),
    fbm(uv * 1.74 - silkWarpA * 1.4 + slowFlow.yx * 0.6)
  );

  float broadNebula = fbm(uv * 1.24 + silkWarpA * 2.25 + vec2(time * 0.012, time * 0.004));
  float fineNebula = fbm(uv * 3.05 + silkWarpB * 2.8 - vec2(time * 0.028, time * 0.016));
  float ribbonPath = uv.y + uv.x * 0.18 + sin(uv.x * 1.25 + time * 0.026) * 0.2;
  float ribbon = exp(-abs(ribbonPath) * 1.34);
  float silk = smoothstep(0.2, 0.86, broadNebula) * smoothstep(0.14, 0.78, fineNebula);

  float lightSource = 1.0 - length(uv - vec2(-0.36, 0.24)) * 0.62;
  float volumeLight = pow(max(lightSource, 0.0), 3.0);
  volumeLight *= 0.62 + 0.38 * fbm(uv * 2.2 + silkWarpB + time * 0.01);

  vec3 nearBlack = vec3(0.016, 0.010, 0.011);
  vec3 deepBrown = vec3(0.102, 0.043, 0.033);
  vec3 copper = vec3(0.471, 0.243, 0.176);
  vec3 ember = vec3(0.86, 0.50, 0.27);
  vec3 smoke = vec3(0.16, 0.12, 0.16);

  vec3 color = nearBlack;
  float dustVeil = smoothstep(0.32, 0.9, fineNebula) * ribbon;

  color = mix(color, deepBrown, broadNebula * 0.78);
  color += smoke * fineNebula * 0.2;
  color += copper * silk * ribbon * 1.02;
  color += ember * volumeLight * 0.44;
  color += vec3(0.3, 0.13, 0.08) * dustVeil * 0.3;
  color += copper * pow(max(1.0 - length(centeredUv * vec2(0.8, 1.15)), 0.0), 2.3) * 0.16;

  float farStars = starLayer(uv + silkWarpA * 0.025, 70.0, 0.963, 0.024, 0.004, 1.0);
  float midStars = starLayer(uv + silkWarpB * 0.035, 126.0, 0.974, 0.021, 0.014, 3.0);
  float nearStars = starLayer(uv, 210.0, 0.984, 0.016, 0.038, 8.0);
  float dustStars = starLayer(uv - silkWarpB * 0.02, 310.0, 0.992, 0.01, 0.052, 11.0);
  float largeStars = starLayer(uv + vec2(0.14, -0.07), 18.0, 0.97, 0.058, 0.008, 13.0);
  float starValue = farStars * 0.5 + midStars * 0.9 + nearStars * 1.28 + dustStars * 0.62 + largeStars * 0.92;

  vec3 starColor = mix(vec3(0.72, 0.75, 0.82), vec3(1.0, 0.82, 0.56), smoothstep(0.2, 1.0, starValue));
  color += starColor * starValue;

  float vignette = smoothstep(1.35, 0.34, length(uv * vec2(0.74, 1.0)));
  color *= 0.34 + vignette * 0.84;
  color = pow(color, vec3(0.88));

  gl_FragColor = vec4(color, 1.0);
}
`

let cleanupWebgl: (() => void) | undefined

const createShader = (gl: WebGLRenderingContext, type: number, source: string) => {
  // 浏览器需要先把 GLSL 字符串编译成 GPU 能执行的 shader。
  const shader = gl.createShader(type)
  if (!shader) {
    return null
  }

  gl.shaderSource(shader, source)
  gl.compileShader(shader)

  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    console.warn(gl.getShaderInfoLog(shader))
    gl.deleteShader(shader)
    return null
  }

  return shader
}

onMounted(() => {
  // 组件挂载后才能拿到真实 canvas 节点，所以 WebGL 初始化放在 onMounted 里。
  const canvas = canvasRef.value
  if (!canvas) {
    return
  }

  const gl = canvas.getContext('webgl', {
    alpha: false,
    antialias: false,
    depth: false,
    powerPreference: 'high-performance',
    premultipliedAlpha: false,
    stencil: false
  })

  if (!gl) {
    return
  }

  const vertexShader = createShader(gl, gl.VERTEX_SHADER, vertexShaderSource)
  const fragmentShader = createShader(gl, gl.FRAGMENT_SHADER, fragmentShaderSource)
  const program = gl.createProgram()

  if (!vertexShader || !fragmentShader || !program) {
    return
  }

  gl.attachShader(program, vertexShader)
  gl.attachShader(program, fragmentShader)
  gl.linkProgram(program)

  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    console.warn(gl.getProgramInfoLog(program))
    gl.deleteProgram(program)
    return
  }

  const positionBuffer = gl.createBuffer()
  const positionLocation = gl.getAttribLocation(program, 'aPosition')
  const resolutionLocation = gl.getUniformLocation(program, 'uResolution')
  const timeLocation = gl.getUniformLocation(program, 'uTime')

  if (!positionBuffer || positionLocation < 0 || !resolutionLocation || !timeLocation) {
    gl.deleteProgram(program)
    return
  }

  const vertices = new Float32Array([
    -1, -1,
    1, -1,
    -1, 1,
    -1, 1,
    1, -1,
    1, 1
  ])

  gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer)
  gl.bufferData(gl.ARRAY_BUFFER, vertices, gl.STATIC_DRAW)
  gl.useProgram(program)
  gl.enableVertexAttribArray(positionLocation)
  gl.vertexAttribPointer(positionLocation, 2, gl.FLOAT, false, 0, 0)

  const reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  const startedAt = performance.now()
  let frameId = 0

  const resize = () => {
    // canvas 的像素尺寸要乘设备像素比，否则高分屏上会发糊；同时限制到 2 倍避免太耗显卡。
    const pixelRatio = Math.min(window.devicePixelRatio || 1, 2)
    const width = Math.max(1, Math.floor(canvas.clientWidth * pixelRatio))
    const height = Math.max(1, Math.floor(canvas.clientHeight * pixelRatio))

    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width
      canvas.height = height
      gl.viewport(0, 0, width, height)
    }
  }

  const render = (now: number) => {
    // 每一帧更新分辨率和时间变量，shader 根据时间生成缓慢流动的星云。
    resize()
    gl.useProgram(program)
    gl.uniform2f(resolutionLocation, canvas.width, canvas.height)
    gl.uniform1f(timeLocation, reducedMotionQuery.matches ? 18.0 : (now - startedAt) * 0.001)
    gl.drawArrays(gl.TRIANGLES, 0, 6)

    if (!reducedMotionQuery.matches) {
      frameId = window.requestAnimationFrame(render)
    }
  }

  window.addEventListener('resize', resize)
  frameId = window.requestAnimationFrame(render)

  cleanupWebgl = () => {
    // 离开页面时释放 GPU 资源，避免反复进入登录页造成内存占用。
    window.cancelAnimationFrame(frameId)
    window.removeEventListener('resize', resize)
    gl.deleteBuffer(positionBuffer)
    gl.deleteProgram(program)
    gl.deleteShader(vertexShader)
    gl.deleteShader(fragmentShader)
  }
})

onBeforeUnmount(() => {
  cleanupWebgl?.()
})
</script>
