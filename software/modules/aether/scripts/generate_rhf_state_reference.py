#!/usr/bin/env python3
"""Independent PySCF supplied-state energies and occupied densities; oracle generation only.
External RHF kernels generate two reference states. Aether does not implement SCF.
"""
import csv
import hashlib
import importlib.metadata
import json
import platform
from pathlib import Path
import numpy as np
import pyscf
from pyscf import gto, lib, scf

assert pyscf.__version__ == '2.10.0'
lib.num_threads(1)
root = Path(__file__).resolve().parents[1]
reference = root / 'src/test/resources/totah/lab/aether/reference'
previous_path = reference / 'jk-manifest.json'
previous = json.loads(previous_path.read_text())
basis_path = root / 'src/main/resources/totah/lab/aether/basis/sto-3g-h.properties'
assert hashlib.sha256(basis_path.read_bytes()).hexdigest() == previous['basis_resource_sha256']
bundled = dict(line.split('=', 1) for line in basis_path.read_text().splitlines() if line and not line.startswith('#'))
upstream = gto.basis.load('sto-3g', 'H')[0]
assert [p[0] for p in upstream[1:]] == list(map(float, bundled['exponents'].split(',')))
assert [p[1] for p in upstream[1:]] == list(map(float, bundled['coefficients'].split(',')))
molecules = {name: gto.M(atom=spec['atom'], basis=spec['basis'], unit='Bohr', charge=spec['charge'],
                         spin=spec['multiplicity']-1, cart=True, verbose=0)
             for name, spec in previous['systems'].items()}
inputs = list(previous['inputs'])
converged = {}
for key, mol in molecules.items():
    mf = scf.RHF(mol)
    mf.conv_tol = 1e-13
    mf.conv_tol_grad = 1e-10
    mf.max_cycle = 200
    total = mf.kernel()
    assert mf.converged
    name = key + '_fresh_rhf'
    inputs.append(dict(case=name, system=key, density=mf.make_rdm1().tolist()))
    converged[name] = dict(external_reference_converged=True, energy_total=float(total))
rows, density_rows, diagnostics = [], [], {}
for spec in inputs:
    name, mol = spec['case'], molecules[spec['system']]
    p = np.array(spec['density'])
    mf = scf.RHF(mol)
    s, h = mf.get_ovlp(), mf.get_hcore()
    vhf = mf.get_veff(dm=p)
    f = h + vhf
    eps, c = mf.eig(f, s)
    nocc = mol.nelectron // 2
    occ = np.zeros(len(eps)); occ[:nocc] = 2
    built = mf.make_rdm1(c, occ)
    # Library energy routines supply the independent energy oracle, including deliberately non-SCF P.
    electronic, _ = mf.energy_elec(dm=p, h1e=h, vhf=vhf)
    total = mf.energy_tot(dm=p, h1e=h, vhf=vhf)
    built_vhf = mf.get_veff(dm=built)
    built_electronic, _ = mf.energy_elec(dm=built, h1e=h, vhf=built_vhf)
    built_total = mf.energy_tot(dm=built, h1e=h, vhf=built_vhf)
    scalar = dict(occupied=nocc, trace_ps=float(np.einsum('ij,ji', built, s)), nuclear=mol.energy_nuc(),
                  electronic=electronic, total=total, built_electronic=built_electronic, built_total=built_total)
    for label, value in scalar.items(): rows.append((name, label, 0, 0, format(value, '.17g')))
    for i in range(len(p)):
        for j in range(len(p)):
            density_rows.append((name, i, j, format(p[i,j], '.17g')))
            rows.append((name, 'density', i, j, format(built[i,j], '.17g')))
    diagnostics[name] = dict(input_to_built_density_max_difference=float(np.max(np.abs(p-built))),
                             idempotency_error=float(np.max(np.abs(built @ s @ built-2*built))))
    if name in converged: assert abs(total-converged[name]['energy_total']) < 1e-12
    if name == 'h2_fresh_rhf': print('H2 fresh:', scalar); print('H2 built density:', built.tolist())
for filename, header, values in [('rhf-state.csv', ['case','evidence','row','column','value'], rows),
                                  ('rhf-state-input.csv', ['case','row','column','value'], density_rows)]:
    with (reference / filename).open('w', newline='') as stream:
        writer = csv.writer(stream, lineterminator='\n'); writer.writerow(header); writer.writerows(values)
manifest = dict(status='SCREENING_ONLY', oracle='PySCF RHF.make_rdm1, energy_elec, energy_tot, Mole.energy_nuc; libcint integrals',
                pyscf=pyscf.__version__, python=platform.python_version(), platform=platform.platform(),
                packages={p: importlib.metadata.version(p) for p in ['numpy','scipy','h5py']}, threads=lib.num_threads(),
                entries=len(rows), systems=previous['systems'], inputs=inputs, independently_converged_references=converged,
                diagnostics=diagnostics, basis_resource_sha256=previous['basis_resource_sha256'], units='bohr;hartree;spin-summed doubled AO density',
                tolerance=1e-10, hashes={p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                     for p in [Path(__file__), previous_path, reference/'rhf-state.csv', reference/'rhf-state-input.csv']})
(reference/'rhf-state-manifest.json').write_text(json.dumps(manifest, indent=2)+'\n')
print('Entries:', len(rows)); print(json.dumps(manifest['hashes'], indent=2))
