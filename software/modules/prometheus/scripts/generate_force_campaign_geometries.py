#!/usr/bin/env python3
"""Generate the frozen 36-member QM-native force-campaign geometry set.

No energies are read or calculated. MIN01/MIN02 optimization-path geometries
are reused, exact final duplicates are removed, and the remaining balance is
filled with deterministic bounded normal-mode perturbations.
"""
import argparse
import hashlib
import json
from pathlib import Path

import numpy as np


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read_xyz(path):
    lines = Path(path).read_text().splitlines()
    n = int(lines[0])
    elements, xyz = [], []
    for line in lines[2:2+n]:
        fields = line.split()
        elements.append(fields[0])
        xyz.append([float(v) for v in fields[1:4]])
    return elements, np.asarray(xyz)


def geometry_key(elements, xyz):
    text = "\n".join(f"{e} {x:.8f} {y:.8f} {z:.8f}"
                     for e, (x, y, z) in zip(elements, xyz))
    return hashlib.sha256(text.encode()).hexdigest()


def write_xyz(path, elements, xyz, comment):
    rows = [str(len(elements)), comment]
    rows.extend(f"{e:<2s} {x: .12f} {y: .12f} {z: .12f}"
                for e, (x, y, z) in zip(elements, xyz))
    path.write_text("\n".join(rows) + "\n")


def mode_snapshots(parent, hessian_dir, count, output, seed):
    elements, origin = read_xyz(parent)
    modes = np.load(hessian_dir / "normal_modes_mass_weighted.npy")
    frequencies = np.loadtxt(hessian_dir / "frequencies_cm-1.txt")
    usable = np.where((frequencies >= 47.0) & (frequencies <= 1200.0))[0]
    local_atoms = np.array([7, 8, 9, 25, 55])
    participation = np.sum(modes[:, local_atoms, :] ** 2, axis=(1, 2))
    selected = usable[np.argsort(participation[usable])[-12:]]
    rng = np.random.default_rng(seed)
    records = []
    targets = [0.025, 0.035, 0.045, 0.055]
    for index in range(count):
        coefficients = rng.normal(size=len(selected)) / np.sqrt(frequencies[selected])
        displacement = np.tensordot(coefficients, modes[selected], axes=(0, 0))
        displacement -= displacement[:26].mean(axis=0)
        rms = np.sqrt(np.mean(np.sum(displacement[:26] ** 2, axis=1)))
        target = targets[index % len(targets)]
        displacement *= target / rms
        # Keep the cloud off equilibrium but chemically local. These gates are
        # applied before energies exist and therefore cannot tune to outcomes.
        parent_sh = np.linalg.norm(origin[55] - origin[25])
        parent_sc = np.linalg.norm(origin[25] - origin[9])
        while (abs(np.linalg.norm((origin + displacement)[55] - (origin + displacement)[25]) - parent_sh) > 0.10
               or abs(np.linalg.norm((origin + displacement)[25] - (origin + displacement)[9]) - parent_sc) > 0.08
               or np.max(np.linalg.norm(displacement, axis=1)) > 0.18):
            displacement *= 0.8
        candidate = origin + displacement
        name = f"normal_mode_{index + 1:02d}.xyz"
        path = output / name
        write_xyz(path, elements, candidate,
                  f"QM-native bounded normal-mode perturbation seed={seed} index={index+1}")
        records.append({
            "path": str(path.resolve()),
            "source_kind": "BOUNDED_NORMAL_MODE_PERTURBATION",
            "source_parent": str(parent.resolve()),
            "source_hessian": str((hessian_dir / "normal_modes_mass_weighted.npy").resolve()),
            "random_seed": seed,
            "sample_index": index + 1,
            "selected_mode_indices_zero_based": selected.tolist(),
            "target_heavy_atom_rms_displacement_angstrom": target,
            "achieved_heavy_atom_rms_displacement_angstrom": float(
                np.sqrt(np.mean(np.sum(displacement[:26] ** 2, axis=1)))),
            "geometry_file_sha256": sha256(path),
            "geometry_coordinate_identity": geometry_key(elements, candidate),
        })
    return records


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--archive", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    archive = Path(args.archive).resolve()
    output = Path(args.output).resolve()
    geometry_root = output / "geometries"
    geometry_root.mkdir(parents=True, exist_ok=True)
    records, seen = [], set()
    plan = {"MIN01": 7, "MIN02": 6}
    seeds = {"MIN01": 5101, "MIN02": 5102}
    for minimum in ("MIN01", "MIN02"):
        source = archive / "execution-unit-05O/qm-native-minima" / minimum
        destination = geometry_root / minimum
        destination.mkdir(parents=True, exist_ok=True)
        candidates = [source / "final.xyz"] + sorted(source.glob("trajectory-*.xyz"))
        for candidate in candidates:
            elements, xyz = read_xyz(candidate)
            key = geometry_key(elements, xyz)
            if key in seen:
                continue
            seen.add(key)
            target = destination / f"qm_native_{len([r for r in records if r['parent_minimum']==minimum])+1:02d}.xyz"
            write_xyz(target, elements, xyz, f"exact QM-native source {candidate}")
            records.append({
                "path": str(target.resolve()), "parent_minimum": minimum,
                "source_kind": "VERIFIED_MINIMUM" if candidate.name == "final.xyz" else "QM_OPTIMIZATION_PATH",
                "source_parent": str(candidate.resolve()),
                "source_file_sha256": sha256(candidate),
                "geometry_file_sha256": sha256(target),
                "geometry_coordinate_identity": key,
            })
        for record in mode_snapshots(source / "final.xyz",
                                     archive / "execution-unit-05O/hessians" / minimum,
                                     plan[minimum], destination, seeds[minimum]):
            record["parent_minimum"] = minimum
            if record["geometry_coordinate_identity"] in seen:
                raise RuntimeError("normal-mode perturbation duplicated an existing geometry")
            seen.add(record["geometry_coordinate_identity"])
            records.append(record)
    if len(records) != 36:
        raise RuntimeError(f"expected exactly 36 unique geometries, obtained {len(records)}")
    manifest = {
        "campaign": "PROMETHEUS_COMMON_PROTOCOL_FORCE_CAMPAIGN_36",
        "status": "GEOMETRIES_FROZEN_PREFLIGHT_ONLY",
        "target_count": 36,
        "development_minima": ["MIN01", "MIN02"],
        "sealed_holdout_minima": ["MIN04"],
        "protocol": {"method": "PBE", "basis": "def2-SVP", "dispersion": "D3(BJ)",
                     "density_fitted": True, "environment": "gas phase",
                     "formal_charge": 0, "multiplicity": 1,
                     "requested_outputs": ["energy_hartree", "gradient_hartree_per_bohr",
                                           "force_hartree_per_bohr"]},
        "records": records,
    }
    (output / "GEOMETRY_GENERATION_MANIFEST.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n")


if __name__ == "__main__":
    main()
