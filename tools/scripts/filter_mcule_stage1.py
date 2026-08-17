#!/usr/bin/env python3
"""Run the pre-docking MCULE intake screen; never prepares or docks ligands."""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import json
import multiprocessing as mp
import os
import random
import time
import zipfile
from collections import Counter
from pathlib import Path

from rdkit import Chem, DataStructs, rdBase
from rdkit.Chem import Crippen, Descriptors, FilterCatalog, Lipinski, MolSurf
from rdkit.Chem import rdFingerprintGenerator, rdMolDescriptors


DCMB_SMILES = "CC(N)c1cccc(Cl)c1Cl"
LIABILITY_SMARTS = {
    "ACID_HALIDE": "[CX3](=O)[F,Cl,Br,I]",
    "SULFONYL_HALIDE": "S(=O)(=O)[F,Cl,Br,I]",
    "ISOCYANATE": "N=C=O",
    "ISOTHIOCYANATE": "N=C=S",
    "EPOXIDE": "[O;r3]1[#6;r3][#6;r3]1",
    "AZIRIDINE": "[N;r3]1[#6;r3][#6;r3]1",
    "HALOACETAMIDE": "[F,Cl,Br,I][CH2][CX3](=O)N",
    "HYDRAZINE": "[NX3][NX3]",
    "REACTIVE_MICHAEL_ACCEPTOR": "[C,c]=[C,c]-[C,S](=O)[#6,#7,#8]",
    "VINYL_SULFONE": "[C,c]=[C,c]-S(=O)(=O)[#6]",
    "PEROXIDE": "O-O",
    "DIAZO": "[N-]=[N+]=[C,N]",
    "POLYPHENOL": "c1([OH])c([OH])c([OH])ccc1",
}
FIELDS = [
    "source_index", "mcule_id", "original_smiles", "canonical_smiles",
    "inchi_key", "fragment_pool", "valid", "formal_charge", "mw",
    "clogp", "hbd", "hba", "rotatable_bonds", "tpsa",
    "aromatic_rings", "heavy_atoms", "fraction_sp3",
    "lipinski_violations", "lipinski_preferred", "ro3_applicable",
    "ro3_passes", "ro3_violations", "pains_matches",
    "liability_findings", "dcmb_tanimoto", "dcmb_bucket",
    "drug_like_passes", "drug_like_rejection_reasons",
    "fragment_passes", "fragment_rejection_reasons",
    "status", "rejection_reasons",
]

_queries = None
_pains = None
_morgan = None
_dcmb_fp = None
_fragment_ids = None


def initialize(fragment_ids: set[str]) -> None:
    global _queries, _pains, _morgan, _dcmb_fp, _fragment_ids
    _queries = {name: Chem.MolFromSmarts(smarts)
                for name, smarts in LIABILITY_SMARTS.items()}
    params = FilterCatalog.FilterCatalogParams()
    params.AddCatalog(FilterCatalog.FilterCatalogParams.FilterCatalogs.PAINS_A)
    params.AddCatalog(FilterCatalog.FilterCatalogParams.FilterCatalogs.PAINS_B)
    params.AddCatalog(FilterCatalog.FilterCatalogParams.FilterCatalogs.PAINS_C)
    _pains = FilterCatalog.FilterCatalog(params)
    _morgan = rdFingerprintGenerator.GetMorganGenerator(radius=2, fpSize=2048)
    _dcmb_fp = _morgan.GetFingerprint(Chem.MolFromSmiles(DCMB_SMILES))
    _fragment_ids = fragment_ids


def screen(item: tuple[int, str, str]) -> dict[str, object]:
    index, smiles, mcule_id = item
    base = {field: "" for field in FIELDS}
    base.update(source_index=index, mcule_id=mcule_id,
                original_smiles=smiles,
                fragment_pool=mcule_id in _fragment_ids)
    mol = Chem.MolFromSmiles(smiles)
    if mol is None:
        base.update(valid=False, status="REJECTED_BEFORE_DOCKING",
                    rejection_reasons="INVALID_SMILES")
        return base

    canonical = Chem.MolToSmiles(mol, canonical=True, isomericSmiles=True)
    charge = Chem.GetFormalCharge(mol)
    mw = Descriptors.MolWt(mol)
    clogp = Crippen.MolLogP(mol)
    hbd = Lipinski.NumHDonors(mol)
    hba = Lipinski.NumHAcceptors(mol)
    rotors = Lipinski.NumRotatableBonds(mol)
    tpsa = MolSurf.TPSA(mol)
    aromatic = rdMolDescriptors.CalcNumAromaticRings(mol)
    heavy = mol.GetNumHeavyAtoms()
    fsp3 = rdMolDescriptors.CalcFractionCSP3(mol)

    ro5 = []
    if mw > 500: ro5.append("MW_GT_500")
    if clogp > 5: ro5.append("CLOGP_GT_5")
    if hbd > 5: ro5.append("HBD_GT_5")
    if hba > 10: ro5.append("HBA_GT_10")
    ro3 = []
    if mw > 300: ro3.append("MW_GT_300")
    if clogp > 3: ro3.append("CLOGP_GT_3")
    if hbd > 3: ro3.append("HBD_GT_3")
    if hba > 3: ro3.append("HBA_GT_3")
    if rotors > 3: ro3.append("ROTATABLE_BONDS_GT_3_PREFERRED")
    if tpsa > 60: ro3.append("TPSA_GT_60_PREFERRED")

    shared_reasons = []
    if len(Chem.GetMolFrags(mol)) != 1:
        shared_reasons.append("DISCONNECTED_STRUCTURE")
    if charge not in (-1, 0, 1):
        shared_reasons.append("FORMAL_CHARGE_OUTSIDE_-1_0_1")

    drug_reasons = list(shared_reasons)
    if not 150 <= mw <= 425: drug_reasons.append("MW_OUTSIDE_150_425")
    if not 0 <= hbd <= 3: drug_reasons.append("HBD_OUTSIDE_0_3")
    if not 1 <= hba <= 7: drug_reasons.append("HBA_OUTSIDE_1_7")
    if rotors > 6: drug_reasons.append("ROTATABLE_BONDS_GT_6")
    if not 20 <= tpsa <= 100: drug_reasons.append("TPSA_OUTSIDE_20_100")
    if not 0.5 <= clogp <= 4.5: drug_reasons.append("CLOGP_OUTSIDE_0.5_4.5")
    if aromatic > 3: drug_reasons.append("AROMATIC_RINGS_GT_3")
    if not 10 <= heavy <= 30: drug_reasons.append("HEAVY_ATOMS_OUTSIDE_10_30")

    fragment_reasons = list(shared_reasons)
    if mw > 300: fragment_reasons.append("RO3_MW_GT_300")
    if clogp > 3: fragment_reasons.append("RO3_CLOGP_GT_3")
    if hbd > 3: fragment_reasons.append("RO3_HBD_GT_3")
    if hba > 3: fragment_reasons.append("RO3_HBA_GT_3")

    liabilities = sorted(name for name, query in _queries.items()
                         if mol.HasSubstructMatch(query))
    pains = sorted(entry.GetDescription() for entry in _pains.GetMatches(mol))
    liability_reasons = ["LIABILITY_" + value for value in liabilities]
    if pains: liability_reasons.append("PAINS")
    drug_reasons.extend(liability_reasons)
    fragment_reasons.extend(liability_reasons)

    similarity = DataStructs.TanimotoSimilarity(
        _morgan.GetFingerprint(mol), _dcmb_fp)
    if similarity <= 0.45:
        bucket = "PRIMARY_NOVEL_CHEMOTYPE"
    elif similarity <= 0.75:
        bucket = "DCMB_NEIGHBORHOOD_CONTROL"
    else:
        bucket = "TOO_SIMILAR_TO_DCMB"
        drug_reasons.append("DCMB_TANIMOTO_GT_0.75")
        fragment_reasons.append("DCMB_TANIMOTO_GT_0.75")

    is_fragment = mcule_id in _fragment_ids
    drug_passes = not drug_reasons
    fragment_passes = is_fragment and not fragment_reasons
    if not drug_passes and not fragment_passes:
        status = "REJECTED_BEFORE_DOCKING"
    elif bucket == "DCMB_NEIGHBORHOOD_CONTROL":
        status = "DCMB_CONTROL_ELIGIBLE_FOR_DOCKING"
    else:
        status = "ELIGIBLE_FOR_DOCKING"
    base.update(
        canonical_smiles=canonical, inchi_key=Chem.MolToInchiKey(mol),
        valid=True, formal_charge=charge, mw=f"{mw:.6f}",
        clogp=f"{clogp:.6f}", hbd=hbd, hba=hba,
        rotatable_bonds=rotors, tpsa=f"{tpsa:.6f}",
        aromatic_rings=aromatic, heavy_atoms=heavy,
        fraction_sp3=f"{fsp3:.6f}", lipinski_violations=";".join(ro5),
        lipinski_preferred=len(ro5) <= 1,
        ro3_applicable=mcule_id in _fragment_ids,
        ro3_passes=not ro3 if mcule_id in _fragment_ids else "",
        ro3_violations=";".join(ro3) if mcule_id in _fragment_ids else "",
        pains_matches=";".join(pains),
        liability_findings=";".join(liabilities),
        dcmb_tanimoto=f"{similarity:.6f}", dcmb_bucket=bucket,
        drug_like_passes=drug_passes,
        drug_like_rejection_reasons=";".join(drug_reasons),
        fragment_passes=fragment_passes,
        fragment_rejection_reasons=(";".join(fragment_reasons)
                                    if is_fragment else "NOT_FRAGMENT_TAGGED"),
        status=status,
        rejection_reasons=";".join(sorted(set(
            drug_reasons + (fragment_reasons if is_fragment else [])))))
    return base


def fragment_ids(path: Path) -> set[str]:
    result = set()
    with zipfile.ZipFile(path) as archive:
        for member in archive.namelist():
            if not member.endswith(".smi.gz"):
                continue
            with archive.open(member) as compressed:
                with gzip.open(compressed, "rt") as rows:
                    for line in rows:
                        _, identifier = line.rstrip("\n").split("\t")
                        result.add(identifier)
    return result


def source_rows(path: Path, limit: int | None,
                sample_size: int | None, sample_seed: int):
    if sample_size is not None:
        rng = random.Random(sample_seed)
        reservoir = []
        with gzip.open(path, "rt") as rows:
            for index, line in enumerate(rows, 1):
                smiles, identifier = line.rstrip("\n").split("\t")
                item = (index, smiles, identifier)
                if len(reservoir) < sample_size:
                    reservoir.append(item)
                else:
                    replacement = rng.randrange(index)
                    if replacement < sample_size:
                        reservoir[replacement] = item
        yield from sorted(reservoir)
        return
    with gzip.open(path, "rt") as rows:
        for index, line in enumerate(rows, 1):
            if limit is not None and index > limit:
                break
            smiles, identifier = line.rstrip("\n").split("\t")
            yield index, smiles, identifier


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--fragments", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--workers", type=int, default=max(1, (os.cpu_count() or 2) - 1))
    parser.add_argument("--limit", type=int)
    parser.add_argument("--sample-size", type=int)
    parser.add_argument("--sample-seed", type=int, default=20260814)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    fragments = fragment_ids(args.fragments)
    counts = Counter()
    started = time.time()
    audit_path = args.output / "stage1-audit.csv.gz"
    survivors_path = args.output / "stage1-survivors.smi.gz"
    context = mp.get_context("spawn")
    with gzip.open(audit_path, "wt", newline="") as audit_stream, \
            gzip.open(survivors_path, "wt") as survivor_stream, \
            context.Pool(args.workers, initializer=initialize,
                         initargs=(fragments,)) as pool:
        writer = csv.DictWriter(audit_stream, fieldnames=FIELDS)
        writer.writeheader()
        for row in pool.imap(screen, source_rows(
                args.source, args.limit, args.sample_size, args.sample_seed),
                             chunksize=500):
            writer.writerow(row)
            counts["processed"] += 1
            counts[row["status"]] += 1
            if row["fragment_pool"]:
                counts["fragment_pool_current"] += 1
            if row["drug_like_passes"]:
                counts["drug_like_passes"] += 1
            if row["fragment_passes"]:
                counts["fragment_passes"] += 1
            if row["drug_like_passes"] and row["fragment_passes"]:
                counts["branch_overlap"] += 1
            if row["status"] != "REJECTED_BEFORE_DOCKING":
                survivor_stream.write(
                    f'{row["canonical_smiles"]}\t{row["mcule_id"]}\t'
                    f'{row["status"]}\t{row["fragment_pool"]}\n')
            if counts["processed"] % 100_000 == 0:
                elapsed = time.time() - started
                print(json.dumps({"processed": counts["processed"],
                                  "elapsed_seconds": round(elapsed, 1),
                                  "records_per_second": round(counts["processed"] / elapsed, 1)}),
                      flush=True)
    summary = {
        "schema": "mcule_stage1_predocking_filter_v1",
        "source": str(args.source),
        "source_sha256": sha256(args.source),
        "fragment_source": str(args.fragments),
        "fragment_source_sha256": sha256(args.fragments),
        "rdkit_version": rdBase.rdkitVersion,
        "dcmb_smiles": DCMB_SMILES,
        "workers": args.workers,
        "limit": args.limit,
        "sample_size": args.sample_size,
        "sample_seed": args.sample_seed,
        "counts": dict(counts),
        "elapsed_seconds": round(time.time() - started, 3),
        "audit": str(audit_path),
        "survivors": str(survivors_path),
        "note": "Pre-docking only; no ligand preparation or docking performed.",
    }
    (args.output / "SUMMARY.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2), flush=True)


if __name__ == "__main__":
    main()
