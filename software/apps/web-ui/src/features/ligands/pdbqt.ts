// Minimal PDBQT reading for the ligand pose viewers: fixed-column atom
// records, vina MODEL/ENDMDL splitting, and distance-based bond
// inference (vina PDBQT ligands carry no CONECT records).

export interface PdbqtAtom {
  x: number
  y: number
  z: number
  element: string
  name: string
  residueName: string
  residueNumber: number
  hetero: boolean
}

const HYDROGEN_TYPES = new Set(['H', 'HD', 'HS'])

function parseAtomLine(line: string): PdbqtAtom | null {
  const x = Number(line.substring(30, 38))
  const y = Number(line.substring(38, 46))
  const z = Number(line.substring(46, 54))
  if (Number.isNaN(x) || Number.isNaN(y) || Number.isNaN(z)) return null
  const tokens = line.trim().split(/\s+/)
  const element = (tokens[tokens.length - 1] ?? '').toUpperCase()
  if (HYDROGEN_TYPES.has(element)) return null
  return {
    x,
    y,
    z,
    element,
    name: line.substring(12, 16).trim(),
    residueName: line.substring(17, 20).trim(),
    residueNumber: Number(line.substring(22, 26)),
    hetero: line.startsWith('HETATM'),
  }
}

/**
 * Atoms of one PDBQT document split into models. Files without MODEL
 * records (receptors, single-pose DiffDock outputs) yield one model.
 */
export function parsePdbqtModels(text: string): PdbqtAtom[][] {
  const models: PdbqtAtom[][] = []
  let current: PdbqtAtom[] = []
  let inModel = false
  for (const line of text.split('\n')) {
    if (line.startsWith('MODEL')) {
      if (inModel && current.length > 0) models.push(current)
      current = []
      inModel = true
    } else if (line.startsWith('ENDMDL')) {
      if (current.length > 0) models.push(current)
      current = []
      inModel = false
    } else if (line.startsWith('ATOM') || line.startsWith('HETATM')) {
      const atom = parseAtomLine(line)
      if (atom) current.push(atom)
    }
  }
  if (current.length > 0) models.push(current)
  return models
}

/** Protein (non-hetero) atoms of a receptor PDBQT. */
export function proteinAtoms(text: string): PdbqtAtom[] {
  const models = parsePdbqtModels(text)
  return (models[0] ?? []).filter((atom) => !atom.hetero)
}

/** C-alpha trace of a receptor, in residue order. */
export function backboneTrace(atoms: PdbqtAtom[]): PdbqtAtom[] {
  return atoms.filter((atom) => atom.name === 'CA')
}

/**
 * Bonds inferred from interatomic distance (1.9 Å covers all common
 * covalent pairs including C–Cl at 1.77 Å; no bond is shorter than
 * ~0.95 Å among heavy atoms).
 */
export function inferBonds(atoms: PdbqtAtom[]): Array<[number, number]> {
  const bonds: Array<[number, number]> = []
  const cutoffSquared = 1.9 * 1.9
  for (let first = 0; first < atoms.length; first++) {
    for (let second = first + 1; second < atoms.length; second++) {
      const dx = atoms[first].x - atoms[second].x
      const dy = atoms[first].y - atoms[second].y
      const dz = atoms[first].z - atoms[second].z
      const distanceSquared = dx * dx + dy * dy + dz * dz
      if (distanceSquared < cutoffSquared && distanceSquared > 0.2) {
        bonds.push([first, second])
      }
    }
  }
  return bonds
}
