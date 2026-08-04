export function geometryBasisLabel(basis: string): string {
  switch (basis) {
    case 'ALPHA_SPHERES':
      return 'Alpha spheres'
    case 'RESIDUE_ATOMS':
    case 'RESOLVED_RESIDUE_HEAVY_ATOMS':
      return 'Residue heavy atoms'
    default:
      return basis
  }
}
