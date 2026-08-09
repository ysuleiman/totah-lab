import { expect, test } from 'vitest'
import {
  backboneTrace,
  inferBonds,
  parsePdbqtModels,
  proteinAtoms,
} from './pdbqt'

function atom(
  record: string,
  name: string,
  residueName: string,
  residueNumber: number,
  x: number,
  y: number,
  z: number,
  element: string,
) {
  const paddedName = name.padStart(3).padEnd(4)
  return (
    record.padEnd(6)
    + '    1'
    + ' '
    + paddedName
    + ' '
    + residueName.padEnd(3)
    + ' A'
    + String(residueNumber).padStart(4)
    + '    '
    + x.toFixed(3).padStart(8)
    + y.toFixed(3).padStart(8)
    + z.toFixed(3).padStart(8)
    + '  0.00  0.00    +0.000 '
    + element
  )
}

test('splits vina models and skips hydrogens', () => {
  const text = [
    'MODEL 1',
    atom('ATOM', 'C1', 'UNL', 1, 0, 0, 0, 'C'),
    atom('ATOM', 'H1', 'UNL', 1, 0.5, 0, 0, 'HD'),
    'ENDMDL',
    'MODEL 2',
    atom('ATOM', 'C1', 'UNL', 1, 10, 0, 0, 'C'),
    'ENDMDL',
  ].join('\n')

  const models = parsePdbqtModels(text)
  expect(models).toHaveLength(2)
  expect(models[0]).toHaveLength(1)
  expect(models[1][0].x).toBe(10)
})

test('treats files without MODEL records as one model', () => {
  const text = [
    atom('ATOM', 'CA', 'ALA', 5, 1, 2, 3, 'C'),
    atom('HETATM', 'CL1', 'UNL', 1, 4, 5, 6, 'Cl'),
  ].join('\n')

  const models = parsePdbqtModels(text)
  expect(models).toHaveLength(1)
  expect(models[0]).toHaveLength(2)

  const protein = proteinAtoms(text)
  expect(protein).toHaveLength(1)
  expect(protein[0].residueName).toBe('ALA')
  expect(protein[0].residueNumber).toBe(5)
})

test('extracts the C-alpha trace', () => {
  const text = [
    atom('ATOM', 'CA', 'ALA', 5, 0, 0, 0, 'C'),
    atom('ATOM', 'CB', 'ALA', 5, 1, 0, 0, 'C'),
    atom('ATOM', 'CA', 'GLY', 6, 3, 0, 0, 'C'),
  ].join('\n')

  const trace = backboneTrace(proteinAtoms(text))
  expect(trace.map((atom) => atom.residueNumber)).toEqual([5, 6])
})

test('infers covalent bonds by distance', () => {
  const text = [
    atom('ATOM', 'C1', 'UNL', 1, 0, 0, 0, 'C'),
    atom('ATOM', 'C2', 'UNL', 1, 1.5, 0, 0, 'C'),
    atom('ATOM', 'CL1', 'UNL', 1, 2.2, 1.4, 0, 'Cl'),
  ].join('\n')
  const atoms = parsePdbqtModels(text)[0]

  // C1–C2 (1.5 Å) and C2–Cl (~1.6 Å) bond; C1–Cl (~2.6 Å) does not.
  expect(inferBonds(atoms)).toEqual([[0, 1], [1, 2]])
})
