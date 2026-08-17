import type { AlphaSphereView, Point3D } from '../../api/types'

const CORNERS = [[0,0,0],[1,0,0],[1,1,0],[0,1,0],[0,0,1],[1,0,1],[1,1,1],[0,1,1]]
const TETRAHEDRA = [[0,5,1,6],[0,1,2,6],[0,2,3,6],[0,3,7,6],[0,7,4,6],[0,4,5,6]]

/** The same 0.4 Å field and six-tetrahedra isosurface as pocket-viewer. */
export function buildPocketSurface(spheres: AlphaSphereView[], spacing = 0.4): Point3D[] {
  if (spheres.length === 0) return []
  const padding = spacing * 2
  const origin = {
    x: Math.min(...spheres.map(s => s.center.x - s.radius)) - padding,
    y: Math.min(...spheres.map(s => s.center.y - s.radius)) - padding,
    z: Math.min(...spheres.map(s => s.center.z - s.radius)) - padding,
  }
  const maximum = {
    x: Math.max(...spheres.map(s => s.center.x + s.radius)) + padding,
    y: Math.max(...spheres.map(s => s.center.y + s.radius)) + padding,
    z: Math.max(...spheres.map(s => s.center.z + s.radius)) + padding,
  }
  const [sx, sy, sz] = [maximum.x-origin.x, maximum.y-origin.y, maximum.z-origin.z]
    .map(extent => Math.max(2, Math.ceil(extent / spacing) + 1))
  const values = new Float32Array(sx * sy * sz)
  const point = (x:number,y:number,z:number):Point3D => ({x:origin.x+x*spacing,y:origin.y+y*spacing,z:origin.z+z*spacing})
  for(let z=0;z<sz;z++) for(let y=0;y<sy;y++) for(let x=0;x<sx;x++) {
    const p=point(x,y,z)
    values[(z*sy+y)*sx+x]=Math.max(...spheres.map(s=>s.radius-Math.hypot(p.x-s.center.x,p.y-s.center.y,p.z-s.center.z)))
  }
  const triangles:Point3D[]=[]
  for(let z=0;z<sz-1;z++) for(let y=0;y<sy-1;y++) for(let x=0;x<sx-1;x++) {
    const points=CORNERS.map(([dx,dy,dz])=>point(x+dx,y+dy,z+dz))
    const sample=CORNERS.map(([dx,dy,dz])=>values[((z+dz)*sy+y+dy)*sx+x+dx])
    for(const tetra of TETRAHEDRA) polygonise(tetra,points,sample,triangles)
  }
  return triangles
}

function polygonise(tetra:number[], points:Point3D[], values:number[], out:Point3D[]) {
  const inside=tetra.filter(v=>values[v]>=0), outside=tetra.filter(v=>values[v]<0)
  if(inside.length===0||inside.length===4)return
  const edge=(a:number,b:number)=>interpolate(a,b,points,values)
  if(inside.length===1) out.push(...outside.map(v=>edge(inside[0],v)))
  else if(inside.length===3){const v=inside.map(i=>edge(outside[0],i));out.push(v[0],v[2],v[1])}
  else {const a=edge(inside[0],outside[0]),b=edge(inside[0],outside[1]),c=edge(inside[1],outside[0]),d=edge(inside[1],outside[1]);out.push(a,b,c,b,d,c)}
}

function interpolate(a:number,b:number,points:Point3D[],values:number[]):Point3D{
  const denominator=values[b]-values[a]
  const t=Math.max(0,Math.min(1,Math.abs(denominator)<1e-12?0.5:-values[a]/denominator))
  return {x:points[a].x+t*(points[b].x-points[a].x),y:points[a].y+t*(points[b].y-points[a].y),z:points[a].z+t*(points[b].z-points[a].z)}
}
