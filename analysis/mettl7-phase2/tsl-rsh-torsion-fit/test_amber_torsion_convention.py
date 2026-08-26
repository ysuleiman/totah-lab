#!/usr/bin/env python3
"""Verify the AMBER proper-torsion amplitude convention against Sander."""

import math
import tempfile
from pathlib import Path

import numpy as np
import parmed as pmd
import sander

import run_first_pass as first


def energy(topology: Path, coordinates: np.ndarray) -> float:
    options = sander.gas_input()
    options.cut = 999.0
    sander.setup(str(topology), coordinates, None, options)
    try:
        terms, _ = sander.energy_forces(as_numpy=True)
        return float(terms.tot)
    finally:
        sander.cleanup()


checks = 0
# ParmEd's prmtop phi_k is the already-divided AMBER amplitude used in
# phi_k * (1 + cos(n*phi - phase)).
assert abs((1 + math.cos(3 * 0.0)) - 2.0) < 1e-15; checks += 1
assert abs(1 + math.cos(3 * math.radians(60))) < 1e-15; checks += 1

surface = first.raw_surface_records()["CHI"]
record = next(row for row in surface if row["angle_degrees"] == 45)
_, coordinates = first.read_xyz_bytes(record["xyz"])
original = pmd.load_file(str(first.BASELINE))
type_index = 17
delta = 0.123456
instances = []
for term in original.dihedrals:
    if not term.improper and original.dihedral_types.index(term.type) == type_index:
        instances.append((term.atom1.idx, term.atom2.idx, term.atom3.idx, term.atom4.idx))
parameter = original.dihedral_types[type_index]
expected = delta * sum(
    1.0 + math.cos(float(parameter.per) * first.dihedral(coordinates, atoms) - math.radians(float(parameter.phase)))
    for atoms in instances)
baseline_energy = energy(first.BASELINE, coordinates)
parameter.phi_k += delta
with tempfile.TemporaryDirectory() as temporary:
    mutated = Path(temporary) / "mutated.parm7"
    original.save(str(mutated), format="amber")
    observed = energy(mutated, coordinates) - baseline_energy
assert abs(observed - expected) <= 1e-8, (observed, expected); checks += 1
print(f"AMBER_TORSION_CONVENTION_TESTS_PASS={checks}")
