#!/usr/bin/env python3
"""Prepare reciprocal local-architecture probes without changing backbones."""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import math
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[4]
BASE = ROOT / "research/mettl7-netarsudil-sam-mechanism/local-architecture"
PREP = BASE / "prepared"
PREP.mkdir(parents=True, exist_ok=True)
LOCAL = ROOT / "research/mettl7-netarsudil-sam-mechanism/local-flexibility"
CANONICAL = {e: ROOT / f"analysis/dcmb/sam_state/validated/WT_METTL{e}_SAM_BOUND.pdb" for e in ("7A", "7B")}
SELECTED_7B_G199F = ROOT / "analysis/dcmb/controlled_campaign/receptors/METTL7B_G199F_SELECTED.pdb"
PYTHON = LOCAL / ".venv/bin/python"
MEEKO = "meeko.cli.mk_prepare_receptor"
SYSTEMS = {
    "7B_WT": ("7B", {}),
    "7B_C203N": ("7B", {203: "7A"}),
    "7B_RECIP_196_199": ("7B", {196: "7A", 197: "7A", 198: "7A", 199: "7A"}),
    "7B_RECIP_196_199_C203N": ("7B", {196: "7A", 197: "7A", 198: "7A", 199: "7A", 203: "7A"}),
    "7A_WT_TRANSFER": ("7A", {}),
    "7A_N203C_TRANSFER": ("7A", {203: "7B"}),
    "7B_K196H": ("7B", {196: "7A"}),
    "7B_H197L_I198L": ("7B", {197: "7A", 198: "7A"}),
    "7B_G199F": ("7B", {199: "7A"}),
    "7B_T208S": ("7B", {208: "7A"}),
}
FLEX_BASE = [29, 33, 149, 150, 151, 192, 196, 197, 198, 199, 200, 202, 203, 204, 205, 206, 207, 208, 211]
BACKBONE = {"N", "CA", "C", "O", "OXT"}


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def atom_rows(path: Path):
    rows = []
    for line in path.read_text().splitlines():
        if line.startswith("ATOM"):
            rows.append({"line": line, "num": int(line[22:26]), "name": line[12:16].strip(),
                         "xyz": np.array([float(line[30:38]), float(line[38:46]), float(line[46:54])])})
    return rows


def kabsch(mobile, reference):
    mc, rc = mobile.mean(0), reference.mean(0)
    u, _, vt = np.linalg.svd((mobile - mc).T @ (reference - rc))
    rotation = u @ vt
    if np.linalg.det(rotation) < 0:
        u[:, -1] *= -1
        rotation = u @ vt
    return rotation, rc - mc @ rotation


def dihedral(a, b, c, d):
    b0, b1, b2 = -(b-a), c-b, d-c
    b1 /= np.linalg.norm(b1)
    v, w = b0-np.dot(b0, b1)*b1, b2-np.dot(b2, b1)*b1
    return math.degrees(math.atan2(np.dot(np.cross(b1, v), w), np.dot(v, w)))


def rotate(point, origin, axis, degrees):
    axis = axis/np.linalg.norm(axis); vector = point-origin; angle = math.radians(degrees)
    return origin + vector*math.cos(angle) + np.cross(axis, vector)*math.sin(angle) + axis*np.dot(axis, vector)*(1-math.cos(angle))


def set_histidine_torsion(coords, target, which):
    quartet = ("N", "CA", "CB", "CG") if which == 1 else ("CA", "CB", "CG", "ND1")
    moving = {"CG", "ND1", "CD2", "CE1", "NE2"} if which == 1 else {"ND1", "CD2", "CE1", "NE2"}
    origin = coords["CA"] if which == 1 else coords["CB"]
    axis = coords["CB"]-coords["CA"] if which == 1 else coords["CG"]-coords["CB"]
    current = dihedral(*(coords[name] for name in quartet)); delta = (target-current+180) % 360-180
    candidates = []
    for sign in (1, -1):
        trial = {name: xyz.copy() for name, xyz in coords.items()}
        for name in moving:
            trial[name] = rotate(trial[name], origin, axis, sign*delta)
        error = abs((dihedral(*(trial[name] for name in quartet))-target+180) % 360-180)
        candidates.append((error, trial))
    return min(candidates, key=lambda item: item[0])[1]


def select_k196h_rotamer(coords, target_atoms):
    ligand = [a for a in atom_rows(LOCAL / "prepared/netarsudil_neutral_start.pdbqt") if a["name"] and a["line"].split()[-1] not in {"H", "HD"}]
    environment = [a for a in target_atoms if a["num"] != 196 and a["line"][76:78].strip() != "H"]
    env_xyz, lig_xyz = np.array([a["xyz"] for a in environment]), np.array([a["xyz"] for a in ligand])
    side_names = ("CB", "CG", "ND1", "CD2", "CE1", "NE2")
    candidates = []
    for chi1 in (-60, 60, 180):
        for chi2 in (-90, 0, 90, 180):
            trial = set_histidine_torsion(set_histidine_torsion(coords, chi1, 1), chi2, 2)
            side = np.array([trial[name] for name in side_names])
            env_min = float(np.min(np.linalg.norm(side[:, None, :]-env_xyz[None, :, :], axis=2)))
            lig_min = float(np.min(np.linalg.norm(side[:, None, :]-lig_xyz[None, :, :], axis=2)))
            if env_min >= 1.8 and lig_min >= 1.8:
                candidates.append((abs(lig_min-3.1), -env_min, chi1, chi2, env_min, lig_min, trial))
    if not candidates:
        raise RuntimeError("No clash-free K196H rotamer")
    selected = min(candidates, key=lambda item: item[:2])
    return selected[-1], {"chi1_deg": selected[2], "chi2_deg": selected[3],
                          "minimum_environment_distance_a": selected[4], "minimum_ligand_distance_a": selected[5]}


def mutate(target: str, donors: dict[int, str], destination: Path):
    target_lines = CANONICAL[target].read_text().splitlines()
    by_enzyme = {e: atom_rows(CANONICAL[e]) for e in ("7A", "7B")}
    selected_7b_g199f = atom_rows(SELECTED_7B_G199F)
    replacements = {}
    mutation_qc = []
    for position, donor in donors.items():
        target_atoms = [a for a in by_enzyme[target] if a["num"] == position]
        use_selected_199 = target == "7B" and position == 199 and donor == "7A"
        donor_atoms = [a for a in (selected_7b_g199f if use_selected_199 else by_enzyme[donor]) if a["num"] == position]
        target_bb = np.array([next(a["xyz"] for a in target_atoms if a["name"] == name) for name in ("N", "CA", "C")])
        donor_bb = np.array([next(a["xyz"] for a in donor_atoms if a["name"] == name) for name in ("N", "CA", "C")])
        rotation, translation = kabsch(donor_bb, target_bb)
        donor_resname = donor_atoms[0]["line"][17:20]
        sidechain = []
        for atom in donor_atoms:
            if atom["name"] in BACKBONE:
                continue
            xyz = atom["xyz"] @ rotation + translation
            line = atom["line"]
            line = line[:17] + donor_resname + line[20:22] + f"{position:4d}" + line[26:]
            line = line[:30] + f"{xyz[0]:8.3f}{xyz[1]:8.3f}{xyz[2]:8.3f}" + line[54:]
            sidechain.append(line)
        rotamer_qc = None
        if target == "7B" and position == 196 and donor == "7A":
            coords = {"N": target_bb[0], "CA": target_bb[1]}
            for line in sidechain:
                coords[line[12:16].strip()] = np.array([float(line[30:38]), float(line[38:46]), float(line[46:54])])
            selected, rotamer_qc = select_k196h_rotamer(coords, by_enzyme[target])
            revised = []
            for line in sidechain:
                name = line[12:16].strip(); xyz = selected[name]
                revised.append(line[:30] + f"{xyz[0]:8.3f}{xyz[1]:8.3f}{xyz[2]:8.3f}" + line[54:])
            sidechain = revised
        replacements[position] = sidechain
        fitted = donor_bb @ rotation + translation
        mutation_qc.append({"position": position, "target": target,
                            "donor": "SELECTED_7B_G199F_ROTAMER" if use_selected_199 else donor,
                            "new_residue": donor_resname.strip(),
                            "backbone_fit_rmsd_a": float(np.sqrt(np.mean(np.sum((fitted - target_bb) ** 2, axis=1)))),
                            "rotamer_selection": rotamer_qc})

    output = []
    inserted = set()
    for line in target_lines:
        if line.startswith("HETATM") and line[17:20].strip() == "SAM":
            continue
        if line.startswith("ATOM") and int(line[22:26]) in replacements:
            position = int(line[22:26])
            if line[12:16].strip() in BACKBONE:
                donor_resname = replacements[position][0][17:20]
                line = line[:17] + donor_resname + line[20:]
                output.append(line)
                if line[12:16].strip() in {"O", "OXT"} and position not in inserted:
                    output.extend(replacements[position])
                    inserted.add(position)
            continue
        output.append(line)
    # Renumber ATOM serials deterministically while retaining residue/atom order.
    serial = 0
    normalized = []
    for line in output:
        if line.startswith("ATOM"):
            serial += 1
            line = line[:6] + f"{serial:5d}" + line[11:]
        normalized.append(line)
    destination.write_text("\n".join(normalized) + "\n")
    return mutation_qc


def append_sam(enzyme: str, rigid: Path, destination: Path):
    source = LOCAL / f"prepared/METTL{enzyme}_localflex_rigid_with_SAM.pdbqt"
    sam = [line for line in source.read_text().splitlines()
           if line.startswith(("ATOM", "HETATM")) and line[17:20].strip() == "SAM"]
    heavy = [line for line in sam if line.split()[-1] not in {"H", "HD"}]
    if len(heavy) != 27:
        raise RuntimeError(f"{enzyme}: expected 27 SAM heavy atoms")
    destination.write_text(rigid.read_text().rstrip() + "\n" + "\n".join(sam) + "\n")


def main():
    prepared = []
    for system, (enzyme, donors) in SYSTEMS.items():
        pdb = PREP / f"{system}.pdb"
        mutation_qc = mutate(enzyme, donors, pdb)
        flex_positions = []
        residues = {a["num"]: a["line"][17:20].strip() for a in atom_rows(pdb)}
        for position in FLEX_BASE:
            if residues.get(position) not in {None, "GLY", "PRO", "ALA"}:
                flex_positions.append(position)
        prefix = PREP / system
        command = [str(PYTHON), "-m", MEEKO, "--read_pdb", str(pdb), "-o", str(prefix), "-p",
                   "-f", "A:" + ",".join(map(str, flex_positions))]
        subprocess.run(command, check=True)
        receptor = PREP / f"{system}_rigid_with_SAM.pdbqt"
        append_sam(enzyme, PREP / f"{system}_rigid.pdbqt", receptor)
        prepared.append({"system": system, "enzyme": enzyme, "mutations": mutation_qc,
                         "flexible_positions": flex_positions, "pdb": str(pdb.relative_to(ROOT)),
                         "pdb_sha256": sha(pdb), "rigid_receptor": str(receptor.relative_to(ROOT)),
                         "rigid_receptor_sha256": sha(receptor),
                         "flex_receptor": str((PREP / f"{system}_flex.pdbqt").relative_to(ROOT)),
                         "flex_receptor_sha256": sha(PREP / f"{system}_flex.pdbqt"), "command": command})
    manifest = {"method": "reciprocal side-chain transplant in target N-CA-C frame", "systems": prepared,
                "meeko_version": "0.8.0", "sam_heavy_atoms": 27,
                "starting_ligands": {
                    "7B": "research/mettl7-netarsudil-sam-mechanism/local-flexibility/prepared/netarsudil_neutral_start.pdbqt",
                    "7A": "research/mettl7-netarsudil-sam-mechanism/local-flexibility/prepared/netarsudil_neutral_start_7A_transfer.pdbqt"}}
    (BASE / "preparation_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
