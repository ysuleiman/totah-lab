import type { AlphaSphereView, Point3D } from '../../api/types'

// Rigid alignment never changes sphere radii, only centers. Interpolating
// between the original and aligned candidate therefore lerps the centers
// while keeping each sphere's persisted radius untouched.
export function interpolateSpheres(
  original: AlphaSphereView[],
  alignedCenters: Point3D[],
  progress: number,
): AlphaSphereView[] {
  if (original.length !== alignedCenters.length) {
    return original
  }

  const clampedProgress = Math.max(0, Math.min(1, progress))

  return original.map((sphere, index) => {
    const target = alignedCenters[index]
    return {
      ...sphere,
      center: {
        x: sphere.center.x + (target.x - sphere.center.x) * clampedProgress,
        y: sphere.center.y + (target.y - sphere.center.y) * clampedProgress,
        z: sphere.center.z + (target.z - sphere.center.z) * clampedProgress,
      },
    }
  })
}
