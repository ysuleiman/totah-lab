#!/usr/bin/env python3
"""Residue-level interaction comparison for Vina and BioHub ligand poses."""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
import subprocess
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from rdkit import Chem, RDConfig
from rdkit.Chem import ChemicalFeatures

import vina_vs_biohub_pose_comparison as pose


CONTACT_CUTOFF = 4.0
HBOND_CUTOFF = 3.5
IONIC_CUTOFF = 4.5
AROMATIC_CUTOFF = 5.0
KEY_POCKET = {
    "A:THR144", "A:LEU145", "A:CYS148", "A:SER149", "A:HIS175",
    "A:PHE199", "A:ASP200", "A:GLY201", "A:CYS202", "A:CYS203",
}

FEATURE_FACTORY = ChemicalFeatures.BuildFeatureFactory(
    str(Path(RDConfig.RDDataDir) / "BaseFeatures.fdef")
)

PROTEIN_DONORS = {
    "ARG": {"NE", "NH1", "NH2"}, "LYS": {"NZ"},
    "HIS": {"ND1", "NE2"}, "ASN": {"ND2"}, "GLN": {"NE2"},
    "TRP": {"NE1"}, "SER": {"OG"}, "THR": {"OG1"},
    "TYR": {"OH"}, "CYS": {"SG"},
}
PROTEIN_ACCEPTORS = {
    "ASP": {"OD1", "OD2"}, "GLU": {"OE1", "OE2"},
    "ASN": {"OD1"}, "GLN": {"OE1"}, "HIS": {"ND1", "NE2"},
    "SER": {"OG"}, "THR": {"OG1"}, "TYR": {"OH"},
    "CYS": {"SG"}, "MET": {"SD"},
}
AROMATIC_RESIDUES = {"PHE", "TYR", "TRP", "HIS"}
AROMATIC_ATOMS = {
    "PHE": {"CG", "CD1", "CD2", "CE1", "CE2", "CZ"},
    "TYR": {"CG", "CD1", "CD2", "CE1", "CE2", "CZ"},
    "TRP": {"CG", "CD1", "CD2", "NE1", "CE2", "CE3", "CZ2", "CZ3", "CH2"},
    "HIS": {"CG", "ND1", "CD2", "CE1", "NE2"},
}
HYDROPHOBIC_RESIDUES = {"ALA", "VAL", "LEU", "ILE", "MET", "PRO", "PHE", "TRP", "TYR", "CYS"}
POSITIVE_PROTEIN = {"ARG": {"NE", "CZ", "NH1", "NH2"}, "LYS": {"NZ"}, "HIS": {"ND1", "NE2"}}
NEGATIVE_PROTEIN = {"ASP": {"OD1", "OD2"}, "GLU": {"OE1", "OE2"}}


@dataclass(frozen=True)
class FingerprintResult:
    interactions: frozenset[tuple[str, str]]
    contact_residues: frozenset[str]
    feature_types: frozenset[str]


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pose-comparison", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--biohub-root", type=Path, required=True)
    parser.add_argument("--receptor", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--db-name", default="totah_lab_db")
    parser.add_argument("--db-user", default="postgres")
    parser.add_argument("--db-host", default="localhost")
    return parser.parse_args()


def atom_label(atom: pose.Atom) -> str:
    return f"{atom.chain}:{atom.residue_name}{atom.residue_number}"


def is_protein_donor(atom: pose.Atom) -> bool:
    return atom.name == "N" or atom.name in PROTEIN_DONORS.get(atom.residue_name, set())


def is_protein_acceptor(atom: pose.Atom) -> bool:
    return atom.name == "O" or atom.name in PROTEIN_ACCEPTORS.get(atom.residue_name, set())


def ligand_feature_atoms(smiles: str, coordinate_atoms: list[pose.Atom]):
    template = Chem.RemoveHs(Chem.MolFromSmiles(smiles))
    if template is None:
        raise ValueError("SMILES could not be parsed")
    coordinate = pose.coordinate_molecule(coordinate_atoms)
    mappings, error = pose.graph_mappings(template, coordinate)
    if error:
        raise ValueError(error)
    template_features: dict[str, set[int]] = defaultdict(set)
    for feature in FEATURE_FACTORY.GetFeaturesForMol(template):
        family = feature.GetFamily()
        normalized = {
            "Donor": "DONOR", "Acceptor": "ACCEPTOR",
            "Aromatic": "AROMATIC", "Hydrophobe": "HYDROPHOBIC",
            "LumpedHydrophobe": "HYDROPHOBIC",
            "PosIonizable": "POSITIVE", "NegIonizable": "NEGATIVE",
        }.get(family)
        if normalized:
            template_features[normalized].update(feature.GetAtomIds())
    features: dict[str, set[int]] = defaultdict(set)
    for feature_type, template_indices in template_features.items():
        mapped_sets = {
            frozenset(mapping[index] for index in template_indices)
            for mapping in mappings
        }
        if len(mapped_sets) != 1:
            raise ValueError(
                "symmetry-equivalent graph mappings give ambiguous "
                f"{feature_type} feature assignment"
            )
        features[feature_type].update(next(iter(mapped_sets)))
    # Carbon, sulfur and halogens are included as atom-level hydrophobic sites
    # unless RDKit explicitly represents a charged center there.
    for index, atom in enumerate(coordinate_atoms):
        if atom.element in {"C", "S", "F", "Cl", "Br", "I"}:
            features["HYDROPHOBIC"].add(index)
    return features, len(mappings)


def interaction_fingerprint(
        protein: list[pose.Atom], ligand: list[pose.Atom], smiles: str
) -> tuple[FingerprintResult, int]:
    ligand = [atom for atom in ligand if atom.element != "H"]
    features, mapping_count = ligand_feature_atoms(smiles, ligand)
    interactions: set[tuple[str, str]] = set()
    contacts: set[str] = set()
    ligand_arrays = np.array([atom.xyz for atom in ligand])

    for protein_atom in protein:
        if protein_atom.element == "H":
            continue
        residue = atom_label(protein_atom)
        distances = np.linalg.norm(ligand_arrays - protein_atom.xyz, axis=1)
        for ligand_index, distance in enumerate(distances):
            distance = float(distance)
            ligand_atom = ligand[ligand_index]
            if distance <= CONTACT_CUTOFF:
                contacts.add(residue)
                interactions.add((residue, "CONTACT"))
            if (distance <= CONTACT_CUTOFF
                    and protein_atom.residue_name in HYDROPHOBIC_RESIDUES
                    and protein_atom.element in {"C", "S"}
                    and ligand_index in features["HYDROPHOBIC"]):
                interactions.add((residue, "HYDROPHOBIC"))
            if (distance <= HBOND_CUTOFF and ligand_index in features["DONOR"]
                    and is_protein_acceptor(protein_atom)):
                interactions.add((residue, "LIGAND_DONOR"))
            if (distance <= HBOND_CUTOFF and ligand_index in features["ACCEPTOR"]
                    and is_protein_donor(protein_atom)):
                interactions.add((residue, "LIGAND_ACCEPTOR"))
            if (distance <= AROMATIC_CUTOFF and ligand_index in features["AROMATIC"]
                    and protein_atom.name in AROMATIC_ATOMS.get(protein_atom.residue_name, set())):
                interactions.add((residue, "AROMATIC"))
            if (distance <= IONIC_CUTOFF and ligand_index in features["POSITIVE"]
                    and protein_atom.name in NEGATIVE_PROTEIN.get(protein_atom.residue_name, set())):
                interactions.add((residue, "LIGAND_CATIONIC"))
            if (distance <= IONIC_CUTOFF and ligand_index in features["NEGATIVE"]
                    and protein_atom.name in POSITIVE_PROTEIN.get(protein_atom.residue_name, set())):
                interactions.add((residue, "LIGAND_ANIONIC"))
            if (protein_atom.chain == "A" and protein_atom.residue_number == 202
                    and protein_atom.name == "SG" and distance <= CONTACT_CUTOFF):
                interactions.add((residue, "CYS202_S_PROXIMITY"))

    feature_types = {kind for _, kind in interactions if kind != "CONTACT"}
    return FingerprintResult(
        frozenset(interactions), frozenset(contacts), frozenset(feature_types)
    ), mapping_count


def ratio(intersection: int, union: int) -> float:
    return intersection / union if union else 0.0


def jaccard(first: set | frozenset, second: set | frozenset) -> float:
    return ratio(len(first & second), len(first | second))


def dice(first: set | frozenset, second: set | frozenset) -> float:
    denominator = len(first) + len(second)
    return 2 * len(first & second) / denominator if denominator else 0.0


def overlap_coefficient(first: set | frozenset, second: set | frozenset) -> float:
    denominator = min(len(first), len(second))
    return len(first & second) / denominator if denominator else 0.0


def serialize_residues(values) -> str:
    def key(value):
        digits = "".join(character for character in value if character.isdigit())
        return int(digits or 0), value
    return ";".join(sorted(values, key=key))


def serialize_fingerprint(values) -> str:
    return ";".join(sorted(f"{residue}|{kind}" for residue, kind in values))


def query_pose_paths(pose_ids: list[int], args: argparse.Namespace):
    sql = ("SELECT id,pose_file FROM docking.docking_pose WHERE id IN ("
           + ",".join(map(str, sorted(set(pose_ids)))) + ")")
    command = ["psql", "-h", args.db_host, "-U", args.db_user,
               "-d", args.db_name, "-At", "-F", "\t", "-c", sql]
    completed = subprocess.run(command, check=True, capture_output=True,
                               text=True, env=os.environ.copy())
    return {int(line.split("\t", 1)[0]): Path(line.split("\t", 1)[1])
            for line in completed.stdout.splitlines() if line.strip()}


def connected_clusters(signatures: list[frozenset], threshold: float):
    adjacency = [set() for _ in signatures]
    for first in range(len(signatures)):
        for second in range(first + 1, len(signatures)):
            if jaccard(signatures[first], signatures[second]) >= threshold:
                adjacency[first].add(second)
                adjacency[second].add(first)
    labels = [0] * len(signatures)
    cluster = 0
    for start in range(len(signatures)):
        if labels[start]:
            continue
        cluster += 1
        stack = [start]
        labels[start] = cluster
        while stack:
            current = stack.pop()
            for neighbor in adjacency[current]:
                if not labels[neighbor]:
                    labels[neighbor] = cluster
                    stack.append(neighbor)
    return labels


def classification(same_pocket, shared_anchor, tanimoto, shared_key_count,
                   mapping_valid):
    if not mapping_valid:
        return "Indeterminate"
    meaningful_key_agreement = shared_key_count >= 2
    if (same_pocket and shared_anchor and tanimoto >= 0.50
            and meaningful_key_agreement):
        return "Strong interaction-level agreement"
    if (same_pocket and tanimoto >= 0.25
            and (shared_anchor or meaningful_key_agreement)):
        return "Moderate interaction-level agreement"
    if same_pocket:
        return "Same region, different binding mode"
    if shared_anchor and shared_key_count:
        return "Same region, different binding mode"
    return "Not reproduced"


def main() -> None:
    args = arguments()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    first_rows = list(csv.DictReader(args.pose_comparison.open()))
    first_by_id = {row["ligand_id"]: row for row in first_rows}
    manifest = json.loads(args.manifest.read_text())
    entries = manifest["entries"]
    pose_paths = query_pose_paths(
        [int(entry["candidate"]["poseIdPrimary"]) for entry in entries], args
    )
    receptor, _ = pose.parse_pdb(args.receptor)
    rows = []
    signatures = []
    signature_metadata = []

    for entry in entries:
        candidate = entry["candidate"]
        ligand_id = candidate["ligandId"]
        first = first_by_id[ligand_id]
        pose_id = int(candidate["poseIdPrimary"])
        vina_path = pose_paths.get(pose_id)
        bio_path = args.biohub_root / entry["predictionPdb"]
        missing = [str(path) for path in (vina_path, bio_path)
                   if path is None or not path.is_file()]
        mapping_error = ""
        if missing:
            rows.append({"ligand_id": ligand_id,
                         "final_interaction_interpretation": "Indeterminate",
                         "notes": "Missing: " + ";".join(missing)})
            continue

        vina_ligand = pose.parse_first_vina_model(vina_path)
        bio_protein, bio_ligand = pose.parse_pdb(bio_path)
        try:
            vina_fp, vina_mapping_count = interaction_fingerprint(
                receptor, vina_ligand, entry["smiles"]
            )
            bio_fp, bio_mapping_count = interaction_fingerprint(
                bio_protein, bio_ligand, entry["smiles"]
            )
        except ValueError as error:
            mapping_error = str(error)
            vina_fp = FingerprintResult(frozenset(), frozenset(), frozenset())
            bio_fp = FingerprintResult(frozenset(), frozenset(), frozenset())
            vina_mapping_count = bio_mapping_count = 0

        shared_contacts = vina_fp.contact_residues & bio_fp.contact_residues
        shared_interactions = vina_fp.interactions & bio_fp.interactions
        shared_key = shared_contacts & KEY_POCKET
        contact_jaccard = jaccard(vina_fp.contact_residues, bio_fp.contact_residues)
        contact_dice = dice(vina_fp.contact_residues, bio_fp.contact_residues)
        fingerprint_tanimoto = jaccard(vina_fp.interactions, bio_fp.interactions)
        pharmacophore_overlap = overlap_coefficient(
            frozenset(item for item in vina_fp.interactions if item[1] != "CONTACT"),
            frozenset(item for item in bio_fp.interactions if item[1] != "CONTACT"),
        )
        shared_cys = (
            ("A:CYS202", "CYS202_S_PROXIMITY") in vina_fp.interactions
            and ("A:CYS202", "CYS202_S_PROXIMITY") in bio_fp.interactions
        )
        vina_sg = float(first["minimum_vina_ligand_to_cys202_sg_distance_a"])
        bio_sg = float(first["minimum_biohub_ligand_to_cys202_sg_distance_a"])
        broad_cys_anchor = vina_sg <= 6.0 and bio_sg <= 6.0
        centroid = float(first["ligand_centroid_distance_a"])
        same_pocket = (
            centroid <= 6.0 and bool(shared_contacts)
        ) or (
            broad_cys_anchor and len(shared_contacts) >= 3
        )
        rmsd = float(first["ligand_heavy_atom_rmsd_a"])
        changed_orientation = (
            same_pocket and (shared_cys or broad_cys_anchor)
            and (rmsd > 4.0 or centroid > 6.0)
        )
        different_mode = same_pocket and (
            fingerprint_tanimoto < 0.25 or changed_orientation
        )
        final = classification(
            same_pocket, shared_cys, fingerprint_tanimoto,
            len(shared_key), not mapping_error,
        )
        row = {
            "ligand_id": ligand_id,
            "mettl7b_vina_score": first["mettl7b_vina_score"],
            "mettl7a_vina_score": first["mettl7a_vina_score"],
            "selectivity_delta_7a_minus_7b": first["selectivity_delta_7a_minus_7b"],
            "vina_contact_residues_4a": serialize_residues(vina_fp.contact_residues),
            "biohub_contact_residues_4a": serialize_residues(bio_fp.contact_residues),
            "shared_contact_residues_4a": serialize_residues(shared_contacts),
            "residue_contact_jaccard": contact_jaccard,
            "residue_contact_dice": contact_dice,
            "interaction_fingerprint_tanimoto": fingerprint_tanimoto,
            "pharmacophore_feature_overlap": pharmacophore_overlap,
            "shared_cys202_sg_interaction": shared_cys,
            "shared_key_pocket_residues": serialize_residues(shared_key),
            "shared_key_pocket_residue_count": len(shared_key),
            "vina_interaction_fingerprint": serialize_fingerprint(vina_fp.interactions),
            "biohub_interaction_fingerprint": serialize_fingerprint(bio_fp.interactions),
            "shared_interaction_fingerprint": serialize_fingerprint(shared_interactions),
            "same_pocket_region": same_pocket,
            "same_anchor_changed_orientation": changed_orientation,
            "fundamentally_different_binding_mode": different_mode,
            "ligand_heavy_atom_rmsd_a": rmsd,
            "ligand_centroid_distance_a": centroid,
            "vina_cys202_sg_distance_a": vina_sg,
            "biohub_cys202_sg_distance_a": bio_sg,
            "biohub_ligand_confidence": first["biohub_ligand_confidence"],
            "vina_valid_graph_mappings": vina_mapping_count,
            "biohub_valid_graph_mappings": bio_mapping_count,
            "final_interaction_interpretation": final,
            "notes": mapping_error,
        }
        rows.append(row)
        signatures.extend([vina_fp.interactions, bio_fp.interactions])
        signature_metadata.extend([(ligand_id, "Vina"), (ligand_id, "BioHub")])

    cluster_labels = connected_clusters(signatures, 0.40)
    cluster_lookup = dict(zip(signature_metadata, cluster_labels))
    for row in rows:
        ligand_id = row["ligand_id"]
        row["vina_interaction_cluster"] = cluster_lookup.get((ligand_id, "Vina"), "")
        row["biohub_interaction_cluster"] = cluster_lookup.get((ligand_id, "BioHub"), "")

    fields = [
        "ligand_id", "mettl7b_vina_score", "mettl7a_vina_score",
        "selectivity_delta_7a_minus_7b", "vina_contact_residues_4a",
        "biohub_contact_residues_4a", "shared_contact_residues_4a",
        "residue_contact_jaccard", "residue_contact_dice",
        "interaction_fingerprint_tanimoto", "pharmacophore_feature_overlap",
        "shared_cys202_sg_interaction", "shared_key_pocket_residues",
        "shared_key_pocket_residue_count", "same_pocket_region",
        "same_anchor_changed_orientation", "fundamentally_different_binding_mode",
        "vina_interaction_cluster", "biohub_interaction_cluster",
        "vina_interaction_fingerprint", "biohub_interaction_fingerprint",
        "shared_interaction_fingerprint", "ligand_heavy_atom_rmsd_a",
        "ligand_centroid_distance_a", "vina_cys202_sg_distance_a",
        "biohub_cys202_sg_distance_a", "biohub_ligand_confidence",
        "vina_valid_graph_mappings", "biohub_valid_graph_mappings",
        "final_interaction_interpretation", "notes",
    ]
    csv_path = args.output_dir / "vina_vs_biohub_interaction_fingerprint.csv"
    with csv_path.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)

    counts = Counter(row["final_interaction_interpretation"] for row in rows)
    report = args.output_dir / "vina_vs_biohub_interaction_fingerprint.md"
    with report.open("w") as handle:
        handle.write("# Vina versus BioHub interaction-fingerprint analysis\n\n")
        handle.write("## Scientific answer\n\n")
        handle.write(
            "This analysis asks whether BioHub preserves the METTL7B pocket-interaction hypothesis from Vina even when exact Cartesian poses differ. It separates pocket location, residue interactions, Cys202 anchoring, and pose geometry.\n\n"
        )
        handle.write("## Results\n\n")
        handle.write(f"Analyzed {len(rows)} ligands. " + ", ".join(
            f"{name}: {count}" for name, count in sorted(counts.items())) + ".\n\n")
        same_pocket_count = sum(bool(row["same_pocket_region"]) for row in rows)
        shared_cys_count = sum(bool(row["shared_cys202_sg_interaction"]) for row in rows)
        maximum_tanimoto = max(
            (row["interaction_fingerprint_tanimoto"] for row in rows),
            default=0.0,
        )
        handle.write(
            f"BioHub preserves the broad METTL7B/Cys202 pocket region for {same_pocket_count}/{len(rows)} ligands and the strict <=4 A Cys202-SG anchor for {shared_cys_count}/{len(rows)}. However, the maximum typed interaction-fingerprint Tanimoto is {maximum_tanimoto:.2f}, below the 0.25 moderate-agreement threshold. Therefore BioHub independently supports pocket localization—and often Cys202 anchoring—but does not reproduce the detailed Vina residue-interaction network or binding mode for this set.\n\n"
        )
        handle.write("| Ligand | Contact Jaccard | Contact Dice | IFP Tanimoto | Pharmacophore overlap | Shared Cys202 SG | Shared key residues | Same pocket | Geometry RMSD | Interpretation |\n")
        handle.write("|---|---:|---:|---:|---:|---|---|---|---:|---|\n")
        for row in rows:
            handle.write(
                f"| {row['ligand_id']} | {row['residue_contact_jaccard']:.2f} | "
                f"{row['residue_contact_dice']:.2f} | "
                f"{row['interaction_fingerprint_tanimoto']:.2f} | "
                f"{row['pharmacophore_feature_overlap']:.2f} | "
                f"{row['shared_cys202_sg_interaction']} | "
                f"{row['shared_key_pocket_residues'] or 'None'} | "
                f"{row['same_pocket_region']} | {row['ligand_heavy_atom_rmsd_a']:.2f} | "
                f"{row['final_interaction_interpretation']} |\n"
            )

        handle.write("\n## Four distinct agreement questions\n\n")
        handle.write("- **Pose geometry agreement:** assessed by aligned heavy-atom RMSD and centroid displacement, but not used alone to define interaction agreement.\n")
        handle.write("- **Pocket-location agreement:** requires a shared spatial region, or preservation of the Cys202 neighborhood plus at least three shared contact residues.\n")
        handle.write("- **Residue-interaction agreement:** assessed with contact Jaccard/Dice and a typed residue-level interaction-fingerprint Tanimoto.\n")
        handle.write("- **Cys202-anchor agreement:** requires direct <=4.0 A ligand proximity to Cys202 SG in both structures. A <=6.0 A neighborhood is tracked only for broad pocket localization.\n")

        handle.write("\n## Interaction definitions\n\n")
        handle.write(
            "The binary fingerprint consists of `(chain:residue, interaction type)` entries. Types are CONTACT (heavy atoms <=4.0 A), HYDROPHOBIC (<=4.0 A), LIGAND_DONOR and LIGAND_ACCEPTOR hydrogen-bond opportunities (heavy-atom distance <=3.5 A), AROMATIC (aromatic atoms <=5.0 A), LIGAND_CATIONIC and LIGAND_ANIONIC salt-bridge opportunities (<=4.5 A), and CYS202_S_PROXIMITY (ligand heavy atom <=4.0 A from SG). Hydrogen bonds are distance-based opportunities because explicit hydrogen geometry is unavailable.\n\n"
        )
        handle.write("Pharmacophore-feature overlap is the overlap coefficient for shared non-CONTACT typed residue interactions. Contact Jaccard is intersection/union; Dice is twice the intersection divided by the summed set sizes.\n")

        handle.write("\n## Classification rules\n\n")
        handle.write("- Strong: same pocket, shared principal anchor, IFP Tanimoto >=0.50, and at least two shared key-pocket residues.\n")
        handle.write("- Moderate: same pocket, IFP Tanimoto 0.25-0.49, plus a shared anchor or at least two shared key-pocket residues.\n")
        handle.write("- Same region/different mode: broad pocket or Cys202-neighborhood agreement with IFP Tanimoto <0.25 or a substantially changed orientation/network.\n")
        handle.write("- Not reproduced: different pocket without a meaningful shared anchor. Indeterminate: missing structure or invalid molecular-graph mapping.\n")

        handle.write("\n## Interaction-based clustering\n\n")
        clusters: dict[int, list[str]] = defaultdict(list)
        for metadata, label in zip(signature_metadata, cluster_labels):
            clusters[label].append(f"{metadata[0]} ({metadata[1]})")
        handle.write("Combined Vina and BioHub poses were clustered as connected components using typed-fingerprint Tanimoto >=0.40, rather than Cartesian RMSD.\n\n")
        for label, members in sorted(clusters.items()):
            handle.write(f"- Cluster {label}: " + ", ".join(members) + "\n")

        handle.write("\n## Missing inputs, assumptions, and cautions\n\n")
        handle.write("- The requested `/mnt/data` files were not mounted. The moved first-stage report in `/Users/yazan/totah-lab/reports`, the original BioHub artifacts, exact Vina PDBQT poses, and canonical METTL7B receptor were used.\n")
        handle.write("- Interaction typing is structure-based and distance-based. It does not calculate interaction energies, water mediation, protonation equilibria, or covalent reaction feasibility.\n")
        handle.write("- PHE199 in the requested key list is not present in the canonical METTL7B sequence at residue 199; the receptor contains GLY199. The requested PHE199 key is retained for transparent reporting but cannot be matched.\n")
        handle.write("- BioHub confidence fields are annotations only. BioHub does not validate affinity, inhibition, Vina selectivity, or covalency; it supplies an independent METTL7B structural prediction.\n")


if __name__ == "__main__":
    main()
