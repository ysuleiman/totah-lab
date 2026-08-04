import { useEffect, useRef, useState } from 'react'
import type { Point3D } from '../../api/types'

export type ViewerMode = 'overlay' | 'query' | 'candidate'

interface Props {
  queryPoints: Point3D[]
  candidatePoints: Point3D[]
  mode: ViewerMode
  pointSize: number
  opacity: number
  showCentroids: boolean
  resetKey: number
}

interface GlState {
  gl: WebGLRenderingContext
  program: WebGLProgram
  positionLocation: number
  mvpLocation: WebGLUniformLocation | null
  colorLocation: WebGLUniformLocation | null
  sizeLocation: WebGLUniformLocation | null
  opacityLocation: WebGLUniformLocation | null
  queryBuffer: WebGLBuffer | null
  candidateBuffer: WebGLBuffer | null
  centroidBuffer: WebGLBuffer | null
  queryCount: number
  candidateCount: number
  radius: number
}

const QUERY_COLOR: [number, number, number] = [0.16, 0.45, 0.9]
const CANDIDATE_COLOR: [number, number, number] = [0.9, 0.42, 0.13]
const CENTROID_COLOR: [number, number, number] = [0.75, 0.1, 0.35]

const VERTEX_SHADER = `
attribute vec3 aPosition;
uniform mat4 uMvp;
uniform float uSize;
void main() {
  gl_Position = uMvp * vec4(aPosition, 1.0);
  gl_PointSize = uSize;
}
`

const FRAGMENT_SHADER = `
precision mediump float;
uniform vec3 uColor;
uniform float uOpacity;
void main() {
  vec2 offset = gl_PointCoord - vec2(0.5);
  if (dot(offset, offset) > 0.25) {
    discard;
  }
  gl_FragColor = vec4(uColor, uOpacity);
}
`

export function PointCloudViewer(props: Props) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const stateRef = useRef<GlState | null>(null)
  const cameraRef = useRef({ yaw: 0.7, pitch: 0.45, zoom: 3.2 })
  const propsRef = useRef(props)
  propsRef.current = props

  const [supported, setSupported] = useState(true)

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return

    let gl: WebGLRenderingContext | null = null
    try {
      gl = canvas.getContext('webgl', { antialias: true })
    } catch {
      gl = null
    }
    if (!gl) {
      setSupported(false)
      return
    }

    const state = createGlState(gl)
    if (!state) {
      setSupported(false)
      return
    }
    stateRef.current = state

    let dragging = false
    let lastX = 0
    let lastY = 0

    const onMouseDown = (event: MouseEvent) => {
      dragging = true
      lastX = event.clientX
      lastY = event.clientY
    }
    const onMouseMove = (event: MouseEvent) => {
      if (!dragging) return
      const camera = cameraRef.current
      camera.yaw += (event.clientX - lastX) * 0.01
      camera.pitch += (event.clientY - lastY) * 0.01
      camera.pitch = Math.max(-1.5, Math.min(1.5, camera.pitch))
      lastX = event.clientX
      lastY = event.clientY
      render(stateRef.current, cameraRef.current, propsRef.current)
    }
    const onMouseUp = () => {
      dragging = false
    }
    const onWheel = (event: WheelEvent) => {
      event.preventDefault()
      const camera = cameraRef.current
      camera.zoom = Math.max(
        1.2,
        Math.min(12, camera.zoom * (event.deltaY > 0 ? 1.1 : 0.9)),
      )
      render(stateRef.current, cameraRef.current, propsRef.current)
    }

    canvas.addEventListener('mousedown', onMouseDown)
    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
    canvas.addEventListener('wheel', onWheel, { passive: false })

    uploadGeometry(state, propsRef.current)
    render(state, cameraRef.current, propsRef.current)

    return () => {
      canvas.removeEventListener('mousedown', onMouseDown)
      window.removeEventListener('mousemove', onMouseMove)
      window.removeEventListener('mouseup', onMouseUp)
      canvas.removeEventListener('wheel', onWheel)
      stateRef.current = null
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    cameraRef.current = { yaw: 0.7, pitch: 0.45, zoom: 3.2 }
    render(stateRef.current, cameraRef.current, propsRef.current)
  }, [props.resetKey])

  useEffect(() => {
    uploadGeometry(stateRef.current, propsRef.current)
    render(stateRef.current, cameraRef.current, propsRef.current)
  }, [props.queryPoints, props.candidatePoints])

  useEffect(() => {
    render(stateRef.current, cameraRef.current, propsRef.current)
  })

  if (!supported) {
    return (
      <div className="viewer-fallback" role="img">
        3D viewer unavailable in this browser.
      </div>
    )
  }

  return <canvas ref={canvasRef} className="viewer-canvas" />
}

function createGlState(gl: WebGLRenderingContext): GlState | null {
  const program = gl.createProgram()
  if (!program) return null

  const vertex = compileShader(gl, gl.VERTEX_SHADER, VERTEX_SHADER)
  const fragment = compileShader(gl, gl.FRAGMENT_SHADER, FRAGMENT_SHADER)
  if (!vertex || !fragment) return null

  gl.attachShader(program, vertex)
  gl.attachShader(program, fragment)
  gl.linkProgram(program)
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    return null
  }

  return {
    gl,
    program,
    positionLocation: gl.getAttribLocation(program, 'aPosition'),
    mvpLocation: gl.getUniformLocation(program, 'uMvp'),
    colorLocation: gl.getUniformLocation(program, 'uColor'),
    sizeLocation: gl.getUniformLocation(program, 'uSize'),
    opacityLocation: gl.getUniformLocation(program, 'uOpacity'),
    queryBuffer: null,
    candidateBuffer: null,
    centroidBuffer: null,
    queryCount: 0,
    candidateCount: 0,
    radius: 10,
  }
}

function compileShader(
  gl: WebGLRenderingContext,
  type: number,
  source: string,
): WebGLShader | null {
  const shader = gl.createShader(type)
  if (!shader) return null
  gl.shaderSource(shader, source)
  gl.compileShader(shader)
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    return null
  }
  return shader
}

function uploadGeometry(state: GlState | null, props: Props) {
  if (!state) return
  const { gl } = state

  state.queryBuffer = uploadPoints(gl, state.queryBuffer, props.queryPoints)
  state.candidateBuffer = uploadPoints(
    gl,
    state.candidateBuffer,
    props.candidatePoints,
  )
  state.centroidBuffer = uploadPoints(gl, state.centroidBuffer, [
    centroidOf(props.queryPoints),
    centroidOf(props.candidatePoints),
  ])
  state.queryCount = props.queryPoints.length
  state.candidateCount = props.candidatePoints.length

  let radius = 1
  for (const point of [...props.queryPoints, ...props.candidatePoints]) {
    radius = Math.max(
      radius,
      Math.sqrt(point.x ** 2 + point.y ** 2 + point.z ** 2),
    )
  }
  state.radius = radius
}

function uploadPoints(
  gl: WebGLRenderingContext,
  buffer: WebGLBuffer | null,
  points: Point3D[],
): WebGLBuffer | null {
  const target = buffer ?? gl.createBuffer()
  gl.bindBuffer(gl.ARRAY_BUFFER, target)
  gl.bufferData(
    gl.ARRAY_BUFFER,
    new Float32Array(points.flatMap((point) => [point.x, point.y, point.z])),
    gl.STATIC_DRAW,
  )
  return target
}

function centroidOf(points: Point3D[]): Point3D {
  if (points.length === 0) return { x: 0, y: 0, z: 0 }
  const sum = points.reduce(
    (total, point) => ({
      x: total.x + point.x,
      y: total.y + point.y,
      z: total.z + point.z,
    }),
    { x: 0, y: 0, z: 0 },
  )
  return {
    x: sum.x / points.length,
    y: sum.y / points.length,
    z: sum.z / points.length,
  }
}

function render(
  state: GlState | null,
  camera: { yaw: number; pitch: number; zoom: number },
  props: Props,
) {
  if (!state) return
  const { gl } = state

  const canvas = gl.canvas as HTMLCanvasElement
  const ratio = window.devicePixelRatio || 1
  const width = Math.max(1, Math.floor(canvas.clientWidth * ratio))
  const height = Math.max(1, Math.floor(canvas.clientHeight * ratio))
  if (canvas.width !== width || canvas.height !== height) {
    canvas.width = width
    canvas.height = height
  }

  gl.viewport(0, 0, width, height)
  gl.clearColor(0.98, 0.99, 0.97, 1)
  gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT)
  gl.enable(gl.BLEND)
  gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA)

  gl.useProgram(state.program)

  const distance = state.radius * camera.zoom
  const aspect = width / height
  const projection = perspective(Math.PI / 4, aspect, distance / 100, distance * 20)
  const view = multiply(
    translation(0, 0, -distance),
    multiply(rotationX(camera.pitch), rotationY(camera.yaw)),
  )
  const mvp = multiply(projection, view)

  gl.uniformMatrix4fv(state.mvpLocation, false, new Float32Array(mvp))
  gl.uniform1f(state.sizeLocation, props.pointSize * ratio)
  gl.uniform1f(state.opacityLocation, props.opacity)

  gl.enableVertexAttribArray(state.positionLocation)

  if (props.mode !== 'candidate' && state.queryCount > 0) {
    drawPoints(state, state.queryBuffer, state.queryCount, QUERY_COLOR)
  }
  if (props.mode !== 'query' && state.candidateCount > 0) {
    drawPoints(
      state,
      state.candidateBuffer,
      state.candidateCount,
      CANDIDATE_COLOR,
    )
  }
  if (props.showCentroids) {
    gl.uniform1f(state.sizeLocation, props.pointSize * ratio * 2.5)
    drawPoints(state, state.centroidBuffer, 2, CENTROID_COLOR)
  }
}

function drawPoints(
  state: GlState,
  buffer: WebGLBuffer | null,
  count: number,
  color: [number, number, number],
) {
  const { gl } = state
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
  gl.vertexAttribPointer(
    state.positionLocation,
    3,
    gl.FLOAT,
    false,
    0,
    0,
  )
  gl.uniform3fv(state.colorLocation, color)
  gl.drawArrays(gl.POINTS, 0, count)
}

function perspective(
  fovY: number,
  aspect: number,
  near: number,
  far: number,
): number[] {
  const f = 1 / Math.tan(fovY / 2)
  const nf = 1 / (near - far)
  return [
    f / aspect, 0, 0, 0,
    0, f, 0, 0,
    0, 0, (far + near) * nf, -1,
    0, 0, 2 * far * near * nf, 0,
  ]
}

function translation(x: number, y: number, z: number): number[] {
  return [
    1, 0, 0, 0,
    0, 1, 0, 0,
    0, 0, 1, 0,
    x, y, z, 1,
  ]
}

function rotationX(angle: number): number[] {
  const c = Math.cos(angle)
  const s = Math.sin(angle)
  return [
    1, 0, 0, 0,
    0, c, s, 0,
    0, -s, c, 0,
    0, 0, 0, 1,
  ]
}

function rotationY(angle: number): number[] {
  const c = Math.cos(angle)
  const s = Math.sin(angle)
  return [
    c, 0, -s, 0,
    0, 1, 0, 0,
    s, 0, c, 0,
    0, 0, 0, 1,
  ]
}

function multiply(first: number[], second: number[]): number[] {
  const result = new Array<number>(16).fill(0)
  for (let column = 0; column < 4; column++) {
    for (let row = 0; row < 4; row++) {
      for (let index = 0; index < 4; index++) {
        result[column * 4 + row] +=
          first[index * 4 + row] * second[column * 4 + index]
      }
    }
  }
  return result
}
