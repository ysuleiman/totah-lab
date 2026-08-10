#!/usr/bin/env python3
"""Fail-fast validation of the locked Stage 4 DCMB/interference artifacts."""

import csv
import json
from pathlib import Path

import numpy as np

import analyze_dcmb_campaign as analysis


ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
SYSTEMS = {"7A_WT", "7B_WT", "7A_F43L", "7A_F199G", "7A_F43L_F199G", "7B_L43F", "7B_G199F", "7B_L43F_G199F"}


def pdbqt_heavy(path: Path, sam_only: bool = False) -> np.ndarray:
    coordinates = []
    for line in path.read_text().splitlines():
        if not line.startswith(("ATOM  ", "HETATM")) or (sam_only and " SAM " not in line):
            continue
        if not line.split()[-1].upper().startswith("H"):
            coordinates.append([float(line[30:38]), float(line[38:46]), float(line[46:54])])
    return np.array(coordinates)


def main() -> None:
    manifest = json.loads((HERE / "campaign_manifest.json").read_text())
    assert {item["system"] for item in manifest["conditions"]} == SYSTEMS
    assert manifest["physical_state"] == "protein_plus_fixed_SAM"
    assert manifest["seeds"] == [1, 7, 42]
    assert manifest["exhaustiveness"] == 32
    for condition in manifest["conditions"]:
        assert condition["sam_atom_records"] == 49
        system = condition["system"]
        source_atoms = analysis.pdb_atoms(ROOT / f"analysis/mettl7-closure/stage2/prepared/{system}_SAM_BOUND.pdb")
        source_sam = np.array([atom["xyz"] for atom in source_atoms if atom["residue"] == "SAM"])
        prepared_sam = pdbqt_heavy(ROOT / condition["receptor_pdbqt"], sam_only=True)
        assert len(source_sam) == len(prepared_sam) == 27
        assert float(analysis.pair_distances(source_sam, prepared_sam).min(1).max()) <= 0.00101
    with (HERE / "seed_validation.csv").open() as handle:
        seeds = list(csv.DictReader(handle))
    assert len(seeds) == 48 and all(row["status"] == "PASS" and int(row["returned_modes"]) >= 8 for row in seeds)
    with (HERE / "pose_results.csv").open() as handle:
        poses = list(csv.DictReader(handle))
    with (HERE / "family_results.csv").open() as handle:
        families = list(csv.DictReader(handle))
    with (HERE / "interference_state_matrix.csv").open() as handle:
        comparisons = list(csv.DictReader(handle))
    with (HERE / "eight_system_interference_matrix.csv").open() as handle:
        matrix = list(csv.DictReader(handle))
    assert len(poses) == sum(int(row["returned_modes"]) for row in seeds)
    assert all(float(row["superpocket_atom_fraction"]) >= 0.70 for row in poses if row["site_status"] == "ACCEPTED_CANONICAL_SITE")
    assert all(float(row["superpocket_atom_fraction"]) < 0.70 for row in poses if row["site_status"] == "REJECTED_OUTSIDE_CANONICAL_SITE")
    assert all(row["sam_compatibility"] in {"compatible", "close_nonoverlapping", "sterically_incompatible"} for row in families)
    assert len(comparisons) == 600
    assert {row["system"] for row in matrix} == SYSTEMS
    assert all(row["classification"] in {"BROADLY_INTERFERING", "STATE_DEPENDENT_INTERFERING", "NON_INTERFERING_ESCAPE", "NOT_CLASSIFIED_SHARED_VOLUME_ONLY"} for row in csv.DictReader((HERE / "interference_family_matrix.csv").open()))
    print("Stage 4 DCMB pose-family/interference validation: PASS")


if __name__ == "__main__":
    main()
