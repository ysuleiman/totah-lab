#!/usr/bin/env python3
"""Build the local Meeko reference set for the ligand-prep comparison.

Recursively collects single-molecule V2000 SDFs under
/Users/yazan/artifacts/ligands (excluding this script's output
directory), adds explicit hydrogens with RDKit where missing, runs the
locally installed Meeko mk_prepare_ligand.py on each, and writes the
reference directory:

    meeko-prepared/
        manifest.tsv        id <TAB> name <TAB> h_added
        <id>.sdf            source SDF (hydrogens added if flagged)
        <id>.meeko.pdbqt    meeko output for that SDF

Run with the venv that has meeko + rdkit, e.g.:

    /tmp/meeko-venv/bin/python analysis/ligand-prep-comparison/prepare-reference.py
"""

import csv
import subprocess
import sys
from pathlib import Path

from rdkit import Chem
from rdkit.Chem import AllChem

LIGAND_ROOT = Path("/Users/yazan/artifacts/ligands")
OUTPUT_DIR = LIGAND_ROOT / "meeko-prepared"
MK_PREPARE = sys.prefix and str(
    Path(sys.prefix) / "bin" / "mk_prepare_ligand.py")


def identifier(sdf: Path) -> str:
    relative = sdf.relative_to(LIGAND_ROOT).with_suffix("")
    return "".join(c if c.isalnum() else "-" for c in str(relative))


def load(sdf: Path):
    supplier = Chem.SDMolSupplier(str(sdf), sanitize=False, removeHs=False)
    molecules = [m for m in supplier if m is not None]
    if len(molecules) != 1:
        return None, f"not exactly one molecule ({len(molecules)})"
    molecule = molecules[0]
    block = sdf.read_text(errors="replace")
    if "V3000" in block.splitlines()[3] if len(block.splitlines()) > 3 else False:
        return None, "V3000"
    try:
        molecule.UpdatePropertyCache(strict=False)
        Chem.SanitizeMol(molecule)
    except Exception as exception:  # noqa: BLE001 - record and skip
        return None, f"sanitize: {exception}"
    if molecule.GetNumConformers() == 0:
        return None, "no conformer"
    return molecule, None


def main() -> int:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    manifest_rows = []
    skipped = []

    for sdf in sorted(LIGAND_ROOT.rglob("*.sdf")):
        if OUTPUT_DIR in sdf.parents:
            continue
        ligand_id = identifier(sdf)
        molecule, problem = load(sdf)
        if molecule is None:
            skipped.append((ligand_id, problem))
            continue

        name = molecule.GetProp("_Name") if molecule.HasProp("_Name") else ""
        has_hydrogens = any(
            atom.GetAtomicNum() == 1 for atom in molecule.GetAtoms())
        h_added = not has_hydrogens
        if h_added:
            molecule = Chem.AddHs(molecule, addCoords=True)
            name = name or sdf.stem

        prepared_sdf = OUTPUT_DIR / f"{ligand_id}.sdf"
        # Strip carried-over properties (PubChem blocks and friends):
        # the comparison needs only the molecule, and our SDF reader
        # is strict about what follows them.
        for prop in molecule.GetPropNames():
            molecule.ClearProp(prop)
        writer = Chem.SDWriter(str(prepared_sdf))
        writer.write(molecule)
        writer.close()

        pdbqt = OUTPUT_DIR / f"{ligand_id}.meeko.pdbqt"
        result = subprocess.run(
            [MK_PREPARE, "-i", str(prepared_sdf), "-o", str(pdbqt)],
            capture_output=True, text=True)
        if result.returncode != 0 or not pdbqt.is_file():
            prepared_sdf.unlink(missing_ok=True)
            skipped.append((ligand_id,
                            "meeko: " + result.stderr.strip()[:200]))
            continue

        manifest_rows.append((ligand_id, name or sdf.stem, h_added))

    with (OUTPUT_DIR / "manifest.tsv").open("w", newline="") as handle:
        writer = csv.writer(handle, delimiter="\t", lineterminator="\n")
        writer.writerow(["id", "name", "h_added"])
        writer.writerows(manifest_rows)

    print(f"prepared: {len(manifest_rows)}")
    for ligand_id, problem in skipped:
        print(f"skipped: {ligand_id}: {problem}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
