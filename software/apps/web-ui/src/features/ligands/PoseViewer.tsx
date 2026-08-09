import { useEffect, useRef, useState } from 'react'
import { inferBonds, type PdbqtAtom } from './pdbqt'

interface Props {
  protein: PdbqtAtom[]
  backbone: PdbqtAtom[]
  ligand: PdbqtAtom[]
  resetKey: string
}

interface GlState {
  gl: WebGLRenderingContext
  program: WebGLProgram
  positionLocation: number
  mvpLocation: WebGLUniformLocation | null
  colorLocation: WebGLUniformLocation | null
  sizeLocation: WebGLUniformLocation | null
  proteinBuffer: WebGLBuffer | null
  backboneBuffer: WebGLBuffer | null
  ligandBondBuffer: WebGLBuffer | null
  ligandAtomBuffer: WebGLBuffer | null
  ligandColorBuffer: WebGLBuffer | null
  proteinCount: number
  backboneCount: number
  ligandBondCount: number
  ligandAtomCount: number
  center: { x: number; y: number; z: number }
  radius: number
}

// Element colors (rough CPK, tuned to the page palette).
const LIGAND_COLORS: Record<string, [number, number, number]> = {
  C: [0.16, 0.29, 0.23],
  N: [0.16, 0.45, 0.9],
  O: [0.86, 0.24, 0.18],
  S: [0.85, 0.65, 0.1],
  CL: [0.13, 0.55, 0.3],
}
const PROTEIN_COLOR: [number, number, number] = [0.72, 0.76, 0.71]
const BACKBONE_COLOR: [number, number, number] = [0.45, 0.52, 0.47]

const VERTEX_SHADER = `
attribute vec3 aPosition;
attribute vec3 aColor;
uniform mat4 uMvp;
uniform float uSize;
uniform float uUseColor;
varying vec3 vColor;
uniform vec3 uColor;
varying float vPointSize;
void main() {
  gl_Position = uMvp * vec4(aPosition, 1.0);
  gl_PointSize = uSize;
  vColor = uUseColor > 0.5 ? aColor : uColor;
  vPointSize = uSize;
}
`

const FRAGMENT_SHADER = `
precision mediump float;
varying vec3 vColor;
varying float vPointSize;
void main() {
  vec2 offset = gl_PointCoord - vec2(0.5);
  if (length(offset) > 0.5 && vPointSize > 3.0) {
    discard;
  }
  gl_FragColor = vec4(vColor, 1.0);
}
`

/**
 * WebGL pose viewer in the style of the comparison page's
 * PointCloudViewer: receptor as atoms plus C-alpha trace, docked
 * ligand as element-colored sticks. The camera frames the ligand so
 * the binding site stays visible inside the receptor.
 */
export function PoseViewer(props: Props) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null)
  const stateRef = useRef<GlState | null>(null)
  const cameraRef = useRef({ yaw: 0.7, pitch: 0.45, zoom: 2.6 })
  const propsRef = useRef(props)

  const [supported, setSupported] = useState(true)

  useEffect(() => {
    propsRef.current = props
  }, [props])

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return

    let gl: WebGLRenderingContext | null
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
      render(stateRef.current, cameraRef.current)
    }
    const onMouseUp = () => {
      dragging = false
    }
    const onWheel = (event: WheelEvent) => {
      event.preventDefault()
      const camera = cameraRef.current
      camera.zoom = Math.max(
        0.8,
        Math.min(10, camera.zoom * (event.deltaY > 0 ? 1.1 : 0.9)),
      )
      render(stateRef.current, cameraRef.current)
    }

    canvas.addEventListener('mousedown', onMouseDown)
    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
    canvas.addEventListener('wheel', onWheel, { passive: false })

    uploadGeometry(state, propsRef.current)
    render(state, cameraRef.current)

    return () => {
      canvas.removeEventListener('mousedown', onMouseDown)
      window.removeEventListener('mousemove', onMouseMove)
      window.removeEventListener('mouseup', onMouseUp)
      canvas.removeEventListener('wheel', onWheel)
      destroyGlState(state)
      stateRef.current = null
    }
  }, [])

  useEffect(() => {
    cameraRef.current = { yaw: 0.7, pitch: 0.45, zoom: 2.6 }
    uploadGeometry(stateRef.current, propsRef.current)
    render(stateRef.current, cameraRef.current)
  }, [props.resetKey, props.protein, props.backbone, props.ligand])

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
  gl.deleteShader(vertex)
  gl.deleteShader(fragment)

  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    gl.deleteProgram(program)
    return null
  }

  gl.enable(gl.DEPTH_TEST)

  return {
    gl,
    program,
    positionLocation: gl.getAttribLocation(program, 'aPosition'),
    mvpLocation: gl.getUniformLocation(program, 'uMvp'),
    colorLocation: gl.getUniformLocation(program, 'uColor'),
    sizeLocation: gl.getUniformLocation(program, 'uSize'),
    proteinBuffer: null,
    backboneBuffer: null,
    ligandBondBuffer: null,
    ligandAtomBuffer: null,
    ligandColorBuffer: null,
    proteinCount: 0,
    backboneCount: 0,
    ligandBondCount: 0,
    ligandAtomCount: 0,
    center: { x: 0, y: 0, z: 0 },
    radius: 10,
  }
}

function destroyGlState(state: GlState) {
  const { gl } = state
  for (const buffer of [
    state.proteinBuffer,
    state.backboneBuffer,
    state.ligandBondBuffer,
    state.ligandAtomBuffer,
    state.ligandColorBuffer,
  ]) {
    if (buffer) gl.deleteBuffer(buffer)
  }
  gl.deleteProgram(state.program)
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
    gl.deleteShader(shader)
    return null
  }
  return shader
}

function uploadGeometry(state: GlState | null, props: Props) {
  if (!state) return
  const { gl } = state

  state.proteinBuffer = uploadPoints(gl, state.proteinBuffer, props.protein)
  state.backboneBuffer = uploadPoints(gl, state.backboneBuffer, props.backbone)

  const bonds = inferBonds(props.ligand)
  const bondVertices: number[] = []
  for (const [first, second] of bonds) {
    bondVertices.push(
      props.ligand[first].x, props.ligand[first].y, props.ligand[first].z,
      props.ligand[second].x, props.ligand[second].y, props.ligand[second].z,
    )
  }
  state.ligandBondBuffer = uploadFlat(gl, state.ligandBondBuffer, bondVertices)
  state.ligandAtomBuffer = uploadPoints(gl, state.ligandAtomBuffer,
    props.ligand)
  state.ligandColorBuffer = uploadFlat(
    gl,
    state.ligandColorBuffer,
    props.ligand.flatMap((atom) =>
      LIGAND_COLORS[atom.element] ?? LIGAND_COLORS.C),
  )

  state.proteinCount = props.protein.length
  // LINE_STRIP over the consecutive C-alpha atoms forms the trace.
  state.backboneCount = props.backbone.length
  state.ligandBondCount = bonds.length * 2
  state.ligandAtomCount = props.ligand.length

  // Frame the ligand (or the protein when no ligand is loaded) so the
  // binding site is centered instead of the whole receptor.
  const focus = props.ligand.length > 0 ? props.ligand : props.backbone
  state.center = centroidOf(focus)
  let radius = 8
  for (const atom of focus) {
    const dx = atom.x - state.center.x
    const dy = atom.y - state.center.y
    const dz = atom.z - state.center.z
    radius = Math.max(radius, Math.sqrt(dx * dx + dy * dy + dz * dz))
  }
  state.radius = props.ligand.length > 0 ? radius * 3 + 4 : radius
}

function uploadPoints(
  gl: WebGLRenderingContext,
  buffer: WebGLBuffer | null,
  atoms: PdbqtAtom[],
): WebGLBuffer | null {
  return uploadFlat(
    gl,
    buffer,
    atoms.flatMap((atom) => [atom.x, atom.y, atom.z]),
  )
}

function uploadFlat(
  gl: WebGLRenderingContext,
  buffer: WebGLBuffer | null,
  values: number[],
): WebGLBuffer | null {
  const target = buffer ?? gl.createBuffer()
  gl.bindBuffer(gl.ARRAY_BUFFER, target)
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(values), gl.DYNAMIC_DRAW)
  return target
}

function centroidOf(atoms: PdbqtAtom[]) {
  if (atoms.length === 0) return { x: 0, y: 0, z: 0 }
  const sum = atoms.reduce(
    (total, atom) => ({
      x: total.x + atom.x,
      y: total.y + atom.y,
      z: total.z + atom.z,
    }),
    { x: 0, y: 0, z: 0 },
  )
  return {
    x: sum.x / atoms.length,
    y: sum.y / atoms.length,
    z: sum.z / atoms.length,
  }
}

function render(
  state: GlState | null,
  camera: { yaw: number; pitch: number; zoom: number },
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

  gl.useProgram(state.program)

  const distance = Math.max(1, state.radius) * camera.zoom
  const aspect = width / height
  const projection = perspective(
    Math.PI / 4,
    aspect,
    Math.max(0.01, distance / 100),
    distance * 40,
  )
  const model = translation(-state.center.x, -state.center.y, -state.center.z)
  const view = multiply(
    translation(0, 0, -distance),
    multiply(rotationX(camera.pitch), rotationY(camera.yaw)),
  )
  const mvp = multiply(projection, multiply(view, model))

  gl.uniformMatrix4fv(state.mvpLocation, false, new Float32Array(mvp))
  gl.enableVertexAttribArray(state.positionLocation)

  const colorLocation = gl.getAttribLocation(state.program, 'aColor')

  // Receptor atoms and C-alpha trace.
  gl.uniform1f(state.sizeLocation, 2.2 * ratio)
  drawArray(state, state.proteinBuffer, state.proteinCount, PROTEIN_COLOR,
    gl.POINTS, colorLocation)
  gl.uniform1f(state.sizeLocation, 3 * ratio)
  drawArray(state, state.backboneBuffer, state.backboneCount, BACKBONE_COLOR,
    gl.LINE_STRIP, colorLocation)

  // Ligand sticks and element-colored atoms.
  gl.uniform1f(state.sizeLocation, 5 * ratio)
  drawArray(state, state.ligandBondBuffer, state.ligandBondCount,
    LIGAND_COLORS.C, gl.LINES, colorLocation)
  gl.uniform1f(state.sizeLocation, 7 * ratio)
  drawArray(state, state.ligandAtomBuffer, state.ligandAtomCount,
    LIGAND_COLORS.C, gl.POINTS, colorLocation, state.ligandColorBuffer)
}

function drawArray(
  state: GlState,
  buffer: WebGLBuffer | null,
  count: number,
  color: [number, number, number],
  mode: number,
  colorLocation: number,
  colorBuffer?: WebGLBuffer | null,
) {
  if (!buffer || count <= 0) return
  const { gl } = state
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
  gl.vertexAttribPointer(state.positionLocation, 3, gl.FLOAT, false, 0, 0)
  gl.uniform3fv(state.colorLocation, color)
  const useColorLocation = gl.getUniformLocation(state.program, 'uUseColor')
  if (colorBuffer && colorLocation >= 0) {
    gl.uniform1f(useColorLocation, 1)
    gl.bindBuffer(gl.ARRAY_BUFFER, colorBuffer)
    gl.enableVertexAttribArray(colorLocation)
    gl.vertexAttribPointer(colorLocation, 3, gl.FLOAT, false, 0, 0)
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer)
    gl.vertexAttribPointer(state.positionLocation, 3, gl.FLOAT, false, 0, 0)
  } else {
    gl.uniform1f(useColorLocation, 0)
  }
  gl.drawArrays(mode, 0, count)
  if (colorBuffer && colorLocation >= 0) {
    gl.disableVertexAttribArray(colorLocation)
    gl.uniform1f(useColorLocation, 0)
  }
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
