#!/usr/bin/env python3
"""Build the fixed WT receptor and historical benzylamine SAR inputs.

This script never optimizes receptor or SAM coordinates. Ligands are embedded with
ETKDGv3, MMFF94s-minimized, and converted by the same Hephaestus preparation tool.
"""
from __future__ import annotations

import csv
import hashlib
import json
import shutil
import subprocess
from pathlib import Path

from rdkit import Chem
from rdkit.Chem import AllChem, Descriptors, rdMolDescriptors

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
HEPH = ROOT / "software/modules/hephaestus/target/hephaestus-1.0-SNAPSHOT-standalone.jar"

RECEPTORS = {
    "7A_APO": ROOT / "resources/shared-resources/src/main/resources/Q9H8H3/Q9H8H3_TMT1A_HUMAN.pdb",
    "7A_SAM": ROOT / "analysis/dcmb/sam_state/validated/WT_METTL7A_SAM_BOUND.pdb",
    "7B_APO": ROOT / "experiments/METTL7B-v6_diffdock/target_protein.pdb",
    "7B_SAM": ROOT / "analysis/dcmb/sam_state/validated/WT_METTL7B_SAM_BOUND.pdb",
}
POCKET_SPHERES = {
    "7A": ROOT / "resources/shared-resources/src/main/resources/Q9H8H3/fpocket/pockets/pocket1_vert.pqr",
    "7B": Path("/Users/yazan/artifacts/UP000005640_9606_HUMAN_v6_pockets/fpocket-human/AF-Q6UX53-F1-model_v6-1472429501895029362/AF-Q6UX53-F1-model_v6_out/pockets/pocket1_vert.pqr"),
}

# Exact numeric activity is intentionally absent unless verified in the cited source.
# The 1973 paper establishes a qualitative series but its table values were not
# accessible in a machine-verifiable form during this run.
PARENTS = [
    ("BA", "benzylamine", "NCc1ccccc1", "rabbit adrenal PNMT", "reported inhibition", "", "", "Fuller et al., J Med Chem 1973, DOI:10.1021/jm00260a002", "historical series; exact table value not recovered"),
    ("AMBA", "alpha-methylbenzylamine", "CC(N)c1ccccc1", "rabbit adrenal PNMT", "reported inhibition", "", "", "Fuller et al., J Med Chem 1973, DOI:10.1021/jm00260a002", "stereochemistry unspecified/racemic; exact table value not recovered"),
    ("2CLBA", "2-chlorobenzylamine", "NCc1ccccc1Cl", "rabbit adrenal PNMT", "reported inhibition", "", "", "Fuller et al., J Med Chem 1973, DOI:10.1021/jm00260a002", "exact table value not recovered"),
    ("3CLBA", "3-chlorobenzylamine", "NCc1cccc(Cl)c1", "rabbit adrenal PNMT", "reported inhibition", "", "", "Fuller et al., J Med Chem 1973, DOI:10.1021/jm00260a002", "exact table value not recovered"),
    ("4CLBA", "4-chlorobenzylamine", "NCc1ccc(Cl)cc1", "rabbit adrenal PNMT", "reported inhibition", "", "", "Fuller et al., J Med Chem 1973, DOI:10.1021/jm00260a002", "positional comparator; exact table value not recovered"),
    ("23CL2BA", "2,3-dichlorobenzylamine", "NCc1cccc(Cl)c1Cl", "rabbit adrenal PNMT", "reported inhibition", "", "", "Fuller et al., J Med Chem 1973, DOI:10.1021/jm00260a002", "reported among most active ring substitutions; exact table value not recovered"),
    ("DCMB", "2,3-dichloro-alpha-methylbenzylamine", "CC(N)c1cccc(Cl)c1Cl", "human recombinant METTL7A", "IC50", "1.17", "uM", "Russell et al., Drug Metab Dispos 2023, DOI:10.1124/dmd.122.001100", "historical material stereochemistry unspecified/racemic; METTL7B not inhibited at tested high concentrations, no numeric IC50 reported"),
    ("24DCMB", "2,4-dichloro-alpha-methylbenzylamine", "CC(N)c1ccc(Cl)cc1Cl", "rabbit adrenal PNMT", "reported inhibition", "", "", "Fuller et al., J Med Chem 1973, DOI:10.1021/jm00260a002", "positional isomer; stereochemistry unspecified/racemic; exact value not recovered"),
    ("25DCMB", "2,5-dichloro-alpha-methylbenzylamine", "CC(N)c1cc(Cl)ccc1Cl", "rabbit adrenal PNMT", "reported inhibition", "", "", "Fuller et al., J Med Chem 1973, DOI:10.1021/jm00260a002", "positional isomer; stereochemistry unspecified/racemic; exact value not recovered"),
    ("26DCMB", "2,6-dichloro-alpha-methylbenzylamine", "CC(N)c1c(Cl)cccc1Cl", "rabbit adrenal PNMT", "reported inhibition", "", "", "Fuller et al., J Med Chem 1973, DOI:10.1021/jm00260a002", "positional isomer; stereochemistry unspecified/racemic; exact value not recovered"),
    ("34DCMB", "3,4-dichloro-alpha-methylbenzylamine", "CC(N)c1ccc(Cl)c(Cl)c1", "rabbit adrenal PNMT", "reported inhibition", "", "", "Fuller et al., J Med Chem 1973, DOI:10.1021/jm00260a002", "positional isomer; stereochemistry unspecified/racemic; exact value not recovered"),
    ("SKF64139", "7,8-dichloro-1,2,3,4-tetrahydroisoquinoline (SKF-64139)", "Clc1cccc2CC[NH2+]Cc12", "human PNMT", "Ki", "0.0031", "uM", "Gee et al., ACS Chem Biol 2020, DOI:10.1021/acschembio.0c00445", "identity verified as PubChem CID 123920; value pertains to PNMT, not METTL7A/B"),
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def variants(parent_id: str, smiles: str):
    mol = Chem.MolFromSmiles(smiles)
    centers = Chem.FindMolChiralCenters(mol, includeUnassigned=True)
    if not centers:
        yield parent_id, smiles, "achiral"
        return
    # Required series has one alpha center. Explicitly retain both members of the
    # historical unspecified/racemic material.
    atom_idx = centers[0][0]
    for label, tag in (("R", Chem.ChiralType.CHI_TETRAHEDRAL_CCW), ("S", Chem.ChiralType.CHI_TETRAHEDRAL_CW)):
        copy = Chem.Mol(mol)
        copy.GetAtomWithIdx(atom_idx).SetChiralTag(tag)
        Chem.AssignStereochemistry(copy, cleanIt=True, force=True)
        actual = Chem.FindMolChiralCenters(copy, includeUnassigned=False)[0][1]
        yield f"{parent_id}_{actual}", Chem.MolToSmiles(copy, isomericSmiles=True), actual


def protonate_primary_amine(mol: Chem.Mol) -> Chem.Mol:
    rw = Chem.RWMol(mol)
    for atom in rw.GetAtoms():
        if atom.GetSymbol() == "N" and atom.GetFormalCharge() == 0:
            atom.SetFormalCharge(1)
            atom.SetNoImplicit(False)
    return rw.GetMol()


def write_cif_from_pdb(pdb: Path, cif: Path):
    rows = []
    for line in pdb.read_text().splitlines():
        if not line.startswith(("ATOM  ", "HETATM")):
            continue
        rows.append((line[:6].strip(), int(line[6:11]), line[76:78].strip() or line[12:16].strip()[0],
                     line[12:16].strip(), line[17:20].strip(), line[21:22].strip() or "A",
                     int(line[22:26]), float(line[30:38]), float(line[38:46]), float(line[46:54]),
                     float(line[54:60] or 1), float(line[60:66] or 0)))
    header = """data_dcmb_sar_receptor
_entry.id dcmb_sar_receptor
loop_
_atom_site.group_PDB
_atom_site.id
_atom_site.type_symbol
_atom_site.label_atom_id
_atom_site.label_comp_id
_atom_site.label_asym_id
_atom_site.label_seq_id
_atom_site.Cartn_x
_atom_site.Cartn_y
_atom_site.Cartn_z
_atom_site.occupancy
_atom_site.B_iso_or_equiv
"""
    body = "\n".join(f"{r[0]} {r[1]} {r[2]} '{r[3]}' {r[4]} {r[5]} {r[6]} {r[7]:.3f} {r[8]:.3f} {r[9]:.3f} {r[10]:.2f} {r[11]:.2f}" for r in rows)
    cif.write_text(header + body + "\n#\n")


def main():
    recdir, ligdir = HERE / "receptors", HERE / "ligands"
    recdir.mkdir(parents=True, exist_ok=True); ligdir.mkdir(parents=True, exist_ok=True)
    receptor_rows = []
    for state, source in RECEPTORS.items():
        pdb = recdir / f"WT_METTL{state}.pdb"
        shutil.copyfile(source, pdb)
        write_cif_from_pdb(pdb, pdb.with_suffix(".cif"))
        receptor_rows.append({"state": state, "source": str(source), "source_sha256": sha256(source),
                              "artifact": str(pdb.relative_to(ROOT)), "artifact_sha256": sha256(pdb),
                              "sam_atom_records": sum(" SAM " in x for x in pdb.read_text().splitlines())})
    sphere_rows = {}
    for paralog, source in POCKET_SPHERES.items():
        target = recdir / f"METTL{paralog}_accepted_pocket_spheres.pqr"
        shutil.copyfile(source, target)
        sphere_rows[paralog] = {"source": str(source), "source_sha256": sha256(source),
                                 "artifact": str(target.relative_to(ROOT)), "artifact_sha256": sha256(target),
                                 "alpha_spheres": sum(x.startswith(("ATOM  ", "HETATM")) for x in target.read_text().splitlines())}
    (HERE / "receptor_manifest.json").write_text(json.dumps({
        "coordinate_policy": "canonical protein coordinates; SAM rigidly transferred from recovered BioHub complex; no minimization",
        "validation_metrics": "analysis/dcmb/sam_state/validated/validation_metrics.json",
        "pockets": {
            "7A": {"role": "59-alpha-sphere DCMB subsite intersecting the homologous SAM superpocket", "box": [1.8020,-3.9254,-6.7763,28.452,22.0,26.506]},
            "7B": {"biological_id": "FPOCKET pocket 2", "volume_A3": 1690.538, "alpha_spheres": 197,
                   "filesystem_note": "retained rerun artifact is named pocket1_vert.pqr; filename is not the biological pocket number",
                   "box": [2.8444,-2.1005,-4.2105,25.334,22.0,23.923]},
        }, "pocket_sphere_artifacts": sphere_rows, "receptors": receptor_rows}, indent=2) + "\n")

    fields = ["compound_id","parent_compound_id","name","canonical_smiles","inchi_key","experimental_target",
              "experimental_metric","experimental_value","experimental_units","literature_source","notes",
              "prepared_smiles","stereochemistry","formal_charge","rotatable_bonds","molecular_weight","source_structure"]
    rows = []
    for parent in PARENTS:
        pid, name, neutral, target, metric, value, units, source, notes = parent
        parent_mol = Chem.MolFromSmiles(neutral)
        inchi = Chem.MolToInchiKey(parent_mol)
        for compound_id, stereo_smiles, stereo in variants(pid, neutral):
            mol = protonate_primary_amine(Chem.MolFromSmiles(stereo_smiles))
            prepared_smiles = Chem.MolToSmiles(mol, isomericSmiles=True)
            mol = Chem.AddHs(mol)
            params = AllChem.ETKDGv3(); params.randomSeed = 20260809
            if AllChem.EmbedMolecule(mol, params) != 0:
                raise RuntimeError(f"embedding failed for {compound_id}")
            if AllChem.MMFFHasAllMoleculeParams(mol):
                AllChem.MMFFOptimizeMolecule(mol, mmffVariant="MMFF94s", maxIters=1000)
            mol.SetProp("_Name", compound_id)
            mol.SetProp("PARENT_COMPOUND", pid); mol.SetProp("PREPARED_SMILES", prepared_smiles)
            sdf = ligdir / f"{compound_id}.sdf"
            writer = Chem.SDWriter(str(sdf)); writer.write(mol); writer.close()
            pdbqt = ligdir / f"{compound_id}.pdbqt"
            subprocess.run(["java","-jar",str(HEPH),"prepare-ligand","--input",str(sdf),"--output",str(pdbqt),"--overwrite"], check=True)
            rows.append(dict(zip(fields, [compound_id,pid,name,Chem.MolToSmiles(parent_mol),inchi,target,metric,value,units,source,notes,
                                          prepared_smiles,stereo,Chem.GetFormalCharge(mol),rdMolDescriptors.CalcNumRotatableBonds(mol),
                                          f"{Descriptors.MolWt(mol):.3f}",neutral])))
    with (HERE / "sar_compounds.csv").open("w", newline="") as fh:
        writer = csv.DictWriter(fh, fieldnames=fields); writer.writeheader(); writer.writerows(rows)
    manifest = {"rdkit_version": Chem.rdBase.rdkitVersion, "embedding": "ETKDGv3 seed 20260809",
                "minimization": "MMFF94s, max 1000 iterations when parameterized", "protonation": "amine protonated (+1)",
                "receptor_preparation": "reused exact PDBQT from validated controlled campaign; Amber charges are source of truth",
                "ligand_conversion": "Hephaestus prepare-ligand; Gasteiger/AD4-compatible PDBQT", "ligands": []}
    for row in rows:
        cid = row["compound_id"]
        manifest["ligands"].append({"compound_id": cid, "sdf_sha256": sha256(ligdir/f"{cid}.sdf"),
                                    "pdbqt_sha256": sha256(ligdir/f"{cid}.pdbqt")})
    (HERE / "ligand_preparation_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
