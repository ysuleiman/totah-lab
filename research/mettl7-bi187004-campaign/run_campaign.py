#!/usr/bin/env python3
# DEPRECATED FOR V2 USE (2026-09-05): near-attack geometry in this script is superseded by
# athena.tmt.NearAttackGeometry / NearAttackAssessor / EnsembleNacAnalyzer (Java).
# See ATHENA_NEAR_ATTACK_MIGRATION_NOTE.md. Retained for historical regression reproduction only.
"""Run and document the matched BI-187004 METTL7A/B state campaign."""
from __future__ import annotations

import csv
import hashlib
import json
import math
import shutil
import subprocess
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from rdkit import Chem
from rdkit.Chem import AllChem

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
CONTROL = ROOT / "analysis/dcmb/controlled_campaign/prepared"
SAH = ROOT / "research/mettl7-selectivity-forensics/dcmb-analog-program/sah-campaign-v1/prepared"
HEPH = ROOT / "software/modules/hephaestus/target/hephaestus-1.0-SNAPSHOT-standalone.jar"
VINA = Path("/Users/yazan/bin/vina")
RUN_KEY = "METTL7_BI187004_TAUTOMER_STATE_V1_2026_09_03"
SEEDS = (1, 7, 42)
EXHAUSTIVENESS = 32
MODES = 9
BOX = {
    "7A": (1.8020, -3.9254, -6.7763, 28.452, 22.0, 26.506),
    "7B": (2.8444, -2.1005, -4.2105, 25.334, 22.0, 23.923),
}
TAUTOMERS = {
    "TAUTOMER_1": "N#Cc1ccc2c(c1)[C@H]1CCCN(C(=O)c3ccc4[nH]cnc4c3)[C@H]1C2",
    "TAUTOMER_2": "N#Cc1ccc2c(c1)[C@H]1CCCN(C(=O)c3ccc4nc[nH]c4c3)[C@H]1C2",
}


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare_ligands() -> dict[str, dict]:
    out = HERE / "ligands"
    out.mkdir(parents=True, exist_ok=True)
    records = {}
    for number, (name, smiles) in enumerate(TAUTOMERS.items(), 1):
        base = Chem.MolFromSmiles(smiles)
        if base is None or Chem.GetFormalCharge(base) != 0:
            raise RuntimeError(f"invalid neutral tautomer {name}")
        centers = sorted(Chem.FindMolChiralCenters(base, includeUnassigned=True))
        if len(centers) != 2 or any(label == "?" for _, label in centers):
            raise RuntimeError(f"stereochemistry not fully assigned for {name}: {centers}")
        aromatic_ns = [a for a in base.GetAtoms() if a.GetSymbol() == "N" and a.GetIsAromatic()]
        acceptors = [a for a in aromatic_ns if a.GetTotalNumHs() == 0]
        donors = [a for a in aromatic_ns if a.GetTotalNumHs() == 1]
        if len(acceptors) != 1 or len(donors) != 1:
            raise RuntimeError(f"ambiguous benzimidazole nitrogens for {name}")
        acceptor_name = f"N{acceptors[0].GetIdx() + 1}"
        donor_name = f"N{donors[0].GetIdx() + 1}"
        mol = Chem.AddHs(base)
        params = AllChem.ETKDGv3()
        params.randomSeed = 187004 + number
        if AllChem.EmbedMolecule(mol, params) != 0:
            raise RuntimeError(f"3D embedding failed for {name}")
        if AllChem.MMFFOptimizeMolecule(mol, maxIters=2000) != 0:
            raise RuntimeError(f"MMFF did not converge for {name}")
        sdf = out / f"BI187004_{name}.sdf"
        writer = Chem.SDWriter(str(sdf))
        writer.write(mol)
        writer.close()
        pdbqt = out / f"BI187004_{name}.pdbqt"
        subprocess.run(["java", "-jar", str(HEPH), "prepare-ligand", "--input", str(sdf),
                        "--output", str(pdbqt), "--overwrite"], check=True)
        records[name] = {
            "smiles": Chem.MolToSmiles(base, isomericSmiles=True),
            "formal_charge": Chem.GetFormalCharge(base),
            "chiral_centers": centers,
            "acceptor_atom": acceptor_name,
            "protonated_ring_nitrogen": donor_name,
            "sdf": sdf,
            "pdbqt": pdbqt,
            "sdf_sha256": sha(sdf),
            "pdbqt_sha256": sha(pdbqt),
        }
    heavy_graphs = [Chem.RemoveHs(Chem.MolFromSmiles(x["smiles"])) for x in records.values()]
    if heavy_graphs[0].GetNumAtoms() != heavy_graphs[1].GetNumAtoms():
        raise RuntimeError("tautomer heavy-atom counts differ")
    return records


def receptors() -> dict[tuple[str, str], Path]:
    result = {}
    for paralog in ("7A", "7B"):
        result[(paralog, "APO")] = CONTROL / f"{paralog}_WT_APO.pdbqt"
        result[(paralog, "SAM")] = CONTROL / f"{paralog}_WT_SAM_BOUND.pdbqt"
        result[(paralog, "SAH")] = SAH / f"{paralog}_WT_SAH_BOUND.pdbqt"
    for path in result.values():
        if not path.is_file():
            raise FileNotFoundError(path)
    return result


def run_one(command: list[str], log: Path) -> None:
    with log.open("w") as handle:
        subprocess.run(command, stdout=handle, stderr=subprocess.STDOUT, check=True)


def run_matrix(ligands: dict[str, dict], recs: dict[tuple[str, str], Path]) -> list[dict]:
    raw = HERE / "raw"
    raw.mkdir(exist_ok=True)
    jobs, rows = [], []
    for (paralog, state), receptor in recs.items():
        cx, cy, cz, sx, sy, sz = BOX[paralog]
        for tautomer, ligand in ligands.items():
            for seed in SEEDS:
                stem = f"{paralog}_{state}__{tautomer}__s{seed}"
                pose, log = raw / f"{stem}.pdbqt", raw / f"{stem}.log"
                command = [str(VINA), "--receptor", str(receptor), "--ligand", str(ligand["pdbqt"]),
                           "--center_x", str(cx), "--center_y", str(cy), "--center_z", str(cz),
                           "--size_x", str(sx), "--size_y", str(sy), "--size_z", str(sz),
                           "--exhaustiveness", str(EXHAUSTIVENESS), "--num_modes", str(MODES),
                           "--seed", str(seed), "--out", str(pose)]
                jobs.append((command, log))
                rows.append({"paralog": paralog, "state": state, "tautomer": tautomer, "seed": seed,
                             "receptor": str(receptor.relative_to(ROOT)), "receptor_sha256": sha(receptor),
                             "ligand": str(ligand["pdbqt"].relative_to(ROOT)),
                             "ligand_sha256": ligand["pdbqt_sha256"], "output": str(pose.relative_to(ROOT)),
                             "log": str(log.relative_to(ROOT))})
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(lambda item: run_one(*item), jobs))
    for row in rows:
        pose, log = ROOT / row["output"], ROOT / row["log"]
        row["output_sha256"], row["log_sha256"] = sha(pose), sha(log)
    return rows


def atom_xyz(line: str):
    return (float(line[30:38]), float(line[38:46]), float(line[46:54]))


def angle(a, b, c) -> float:
    u = [a[i] - b[i] for i in range(3)]
    v = [c[i] - b[i] for i in range(3)]
    dot = sum(u[i] * v[i] for i in range(3))
    nu = math.sqrt(sum(x*x for x in u)); nv = math.sqrt(sum(x*x for x in v))
    return math.degrees(math.acos(max(-1.0, min(1.0, dot / (nu * nv)))))


def distance(a, b) -> float:
    return math.sqrt(sum((a[i] - b[i]) ** 2 for i in range(3)))


def cofactor_atoms(paralog: str) -> dict[str, tuple]:
    path = ROOT / "analysis/dcmb/sam_state/validated" / f"WT_METTL{paralog}_SAM_BOUND.pdb"
    result = {}
    for line in path.read_text().splitlines():
        if line.startswith(("ATOM", "HETATM")) and line[17:20].strip() == "SAM":
            result[line[12:16].strip()] = atom_xyz(line)
    return result


def parse_models(path: Path) -> list[dict]:
    models, current = [], None
    for line in path.read_text().splitlines():
        if line.startswith("MODEL"):
            current = {"mode": int(line.split()[1]), "atoms": {}}
        elif current is not None and line.startswith("REMARK VINA RESULT:"):
            current["score"] = float(line.split()[3])
        elif current is not None and line.startswith(("ATOM", "HETATM")):
            current["atoms"][line[12:16].strip()] = atom_xyz(line)
        elif line.startswith("ENDMDL") and current is not None:
            models.append(current); current = None
    return models


def analyze(rows: list[dict], ligands: dict[str, dict], recs: dict[tuple[str, str], Path]):
    pose_rows = []
    for run in rows:
        cofactor = cofactor_atoms(run["paralog"]) if run["state"] == "SAM" else {}
        for model in parse_models(ROOT / run["output"]):
            acc = ligands[run["tautomer"]]["acceptor_atom"]
            record = {k: run[k] for k in ("paralog", "state", "tautomer", "seed")}
            record.update({"mode": model["mode"], "vina_score": model["score"],
                           "acceptor_atom": acc, "acceptor_methyl_distance_a": "",
                           "acceptor_c_s_angle_deg": "", "near_attack_screen": "NOT_APPLICABLE"})
            if cofactor and acc in model["atoms"] and "CE" in cofactor and "SD" in cofactor:
                d = distance(model["atoms"][acc], cofactor["CE"])
                ang = angle(model["atoms"][acc], cofactor["CE"], cofactor["SD"])
                record["acceptor_methyl_distance_a"] = f"{d:.6f}"
                record["acceptor_c_s_angle_deg"] = f"{ang:.6f}"
                record["near_attack_screen"] = "PASS" if d <= 3.5 and ang >= 150 else "FAIL"
            pose_rows.append(record)
    analysis = HERE / "analysis"
    analysis.mkdir(exist_ok=True)
    with (analysis / "pose_metrics.csv").open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(pose_rows[0]))
        writer.writeheader(); writer.writerows(pose_rows)
    summary = []
    for paralog in ("7A", "7B"):
        for state in ("APO", "SAM", "SAH"):
            for tautomer in TAUTOMERS:
                selected = [r for r in pose_rows if r["paralog"] == paralog and r["state"] == state and r["tautomer"] == tautomer]
                best = min(selected, key=lambda r: r["vina_score"])
                near = [r for r in selected if r["near_attack_screen"] == "PASS"]
                summary.append({"paralog": paralog, "state": state, "tautomer": tautomer,
                                "best_vina_score": best["vina_score"], "best_seed": best["seed"],
                                "best_mode": best["mode"], "poses": len(selected),
                                "near_attack_pass_poses": len(near),
                                "best_near_attack_vina_score": min((r["vina_score"] for r in near), default=""),
                                "minimum_acceptor_methyl_distance_a": min((float(r["acceptor_methyl_distance_a"]) for r in selected if r["acceptor_methyl_distance_a"]), default=""),
                                "maximum_acceptor_c_s_angle_deg": max((float(r["acceptor_c_s_angle_deg"]) for r in selected if r["acceptor_c_s_angle_deg"]), default="")})
    with (analysis / "condition_summary.csv").open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(summary[0]))
        writer.writeheader(); writer.writerows(summary)
    return pose_rows, summary


def write_outputs(ligands, recs, runs, poses, summary):
    protocol = {"run_key": RUN_KEY, "compound": "BI 187004", "pubchem_cid": 67223373,
                "cas": "1303515-32-3", "inchi_key": "VVZNCSHIBODHMZ-UZLBHIALSA-N",
                "scope": "matched METTL7A/B APO/SAM/SAH docking of two neutral aromatic benzimidazole tautomers",
                "engine": subprocess.check_output([str(VINA), "--version"], text=True).strip(),
                "seeds": SEEDS, "exhaustiveness": EXHAUSTIVENESS, "num_modes": MODES,
                "boxes": BOX, "near_attack_screen": {"distance_max_a": 3.5, "n_c_s_angle_min_deg": 150.0,
                "application": "SAM only; geometric screen, not proof of catalysis"},
                "new_qm": False, "gpu": False, "interpolation": False,
                "evidence_note": "2018 paper supports microsomal TMT-mediated N-methylation; direct recombinant METTL7A/B catalysis was not tested."}
    (HERE / "protocol.json").write_text(json.dumps(protocol, indent=2) + "\n")
    with (HERE / "ligand_state_provenance.csv").open("w", newline="") as handle:
        fields = ["tautomer", "smiles", "formal_charge", "chiral_centers", "acceptor_atom", "protonated_ring_nitrogen", "sdf", "pdbqt", "sdf_sha256", "pdbqt_sha256"]
        writer = csv.DictWriter(handle, fields); writer.writeheader()
        for name, row in ligands.items():
            writer.writerow({**{k: str(row[k]) for k in fields if k not in {"tautomer", "sdf", "pdbqt"}},
                             "tautomer": name, "sdf": str(row["sdf"].relative_to(ROOT)), "pdbqt": str(row["pdbqt"].relative_to(ROOT))})
    with (HERE / "job_manifest.csv").open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(runs[0])); writer.writeheader(); writer.writerows(runs)
    best_by = {(x["paralog"], x["state"], x["tautomer"]): x for x in summary}
    lines = ["# BI 187004 matched METTL7A/B tautomer and cofactor-state campaign", "",
             "## Scope", "", "BI 187004 was evaluated as two explicit neutral aromatic benzimidazole tautomers against matched METTL7A and METTL7B APO, SAM and SAH receptors. This is static docking evidence, not affinity, turnover, or direct recombinant-enzyme evidence.", "",
             "The literature anchor is Maw et al. (2018), DOI `10.1124/dmd.117.079764`: microsomal BI 187004 N-methylation was DCMB-sensitive and attributed to the then-unidentified TMT activity. It does not directly establish recombinant METTL7A or METTL7B catalysis.", "",
             "## Results", "", "| Paralog | State | Tautomer | Best Vina | Near-attack passes / 27 | Best near-attack Vina |", "|---|---|---|---:|---:|---:|"]
    for p in ("7A", "7B"):
        for s in ("APO", "SAM", "SAH"):
            for t in TAUTOMERS:
                x = best_by[(p,s,t)]; b = x["best_near_attack_vina_score"]
                lines.append(f"| {p} | {s} | {t} | {x['best_vina_score']:.3f} | {x['near_attack_pass_poses'] if s == 'SAM' else 'N/A'} | {f'{float(b):.3f}' if b != '' else 'N/A'} |")
    sam_pass = [r for r in poses if r["state"] == "SAM" and r["near_attack_screen"] == "PASS"]
    conclusion = "At least one SAM-bound pose passed the preregistered geometric screen." if sam_pass else "No SAM-bound pose passed the geometric near-attack screen."
    lines += ["", "## Interpretation", "", conclusion,
              "Near-attack geometry is reported only for SAM. APO and SAH results describe predicted occupancy/orientation only. No docking score is interpreted as binding free energy.", "",
              "`BI187004_TMT_PRODUCTIVE_SUBSTRATE = STRONGLY_SUPPORTED`", "",
              "`BI187004_DIRECT_METTL7B_SUBSTRATE = NOT_YET_DIRECTLY_ESTABLISHED`", "",
              "`BI187004_DIRECT_METTL7A_SUBSTRATE = NOT_ESTABLISHED`", "",
              "## Provenance", "", f"Run key: `{RUN_KEY}`", "", "Machine-readable protocol, ligand-state provenance, job manifest, all poses, condition summaries, SQL persistence, and recursive hashes accompany this report."]
    (HERE / "BI187004_METTL7_MATCHED_DOCKING_REPORT.md").write_text("\n".join(lines) + "\n")

    artifacts = []
    for path in sorted(HERE.rglob("*")):
        if path.is_file() and path.name not in {"SHA256SUMS", "persist.sql"}:
            artifacts.append((sha(path), str(path.relative_to(HERE))))
    (HERE / "SHA256SUMS").write_text("\n".join(f"{digest}  {path}" for digest, path in artifacts) + "\n")

    def q(value): return "'" + str(value).replace("'", "''") + "'"
    sql = ["BEGIN;", """CREATE TABLE IF NOT EXISTS docking.mettl7_bi187004_condition (
run_key varchar(100) NOT NULL REFERENCES docking.mettl7_computational_run(run_key), paralog varchar(8) NOT NULL,
cofactor_state varchar(8) NOT NULL, tautomer varchar(24) NOT NULL, best_vina_score double precision NOT NULL,
pose_count integer NOT NULL, near_attack_pass_count integer NOT NULL, best_near_attack_vina_score double precision,
PRIMARY KEY(run_key,paralog,cofactor_state,tautomer));""", """CREATE TABLE IF NOT EXISTS docking.mettl7_bi187004_pose (
run_key varchar(100) NOT NULL REFERENCES docking.mettl7_computational_run(run_key), paralog varchar(8) NOT NULL,
cofactor_state varchar(8) NOT NULL, tautomer varchar(24) NOT NULL, seed integer NOT NULL, mode integer NOT NULL,
vina_score double precision NOT NULL, acceptor_atom varchar(8) NOT NULL, acceptor_methyl_distance_a double precision,
acceptor_c_s_angle_deg double precision, near_attack_screen varchar(20) NOT NULL,
PRIMARY KEY(run_key,paralog,cofactor_state,tautomer,seed,mode));"""]
    classification = "STATIC_DOCKING_ONLY; TMT_PRODUCTIVE_SUBSTRATE_STRONGLY_SUPPORTED; DIRECT_METTL7A_B_CATALYSIS_UNRESOLVED"
    sql.append(f"INSERT INTO docking.mettl7_computational_run(run_key,title,method,method_version,classification,report_path,input_path,completed_on,protocol,conclusion) VALUES ({q(RUN_KEY)},{q('BI 187004 tautomer-aware matched METTL7A/B APO/SAM/SAH docking')},{q('AutoDock Vina matched multi-seed docking')},{q('Vina 1.2.5-17-gda92a68')},{q(classification)},{q(str((HERE/'BI187004_METTL7_MATCHED_DOCKING_REPORT.md').relative_to(ROOT)))},{q(str((HERE/'protocol.json').relative_to(ROOT)))},'2026-09-03',{q(json.dumps(protocol,separators=(',',':')))}::jsonb,{q(conclusion)}) ON CONFLICT(run_key) DO UPDATE SET classification=EXCLUDED.classification,report_path=EXCLUDED.report_path,input_path=EXCLUDED.input_path,protocol=EXCLUDED.protocol,conclusion=EXCLUDED.conclusion;")
    sql += [f"DELETE FROM docking.mettl7_bi187004_pose WHERE run_key={q(RUN_KEY)};", f"DELETE FROM docking.mettl7_bi187004_condition WHERE run_key={q(RUN_KEY)};"]
    for x in summary:
        near = "NULL" if x["best_near_attack_vina_score"] == "" else str(x["best_near_attack_vina_score"])
        sql.append(f"INSERT INTO docking.mettl7_bi187004_condition VALUES ({q(RUN_KEY)},{q(x['paralog'])},{q(x['state'])},{q(x['tautomer'])},{x['best_vina_score']},{x['poses']},{x['near_attack_pass_poses']},{near});")
    for x in poses:
        d = "NULL" if x["acceptor_methyl_distance_a"] == "" else x["acceptor_methyl_distance_a"]
        a = "NULL" if x["acceptor_c_s_angle_deg"] == "" else x["acceptor_c_s_angle_deg"]
        sql.append(f"INSERT INTO docking.mettl7_bi187004_pose VALUES ({q(RUN_KEY)},{q(x['paralog'])},{q(x['state'])},{q(x['tautomer'])},{x['seed']},{x['mode']},{x['vina_score']},{q(x['acceptor_atom'])},{d},{a},{q(x['near_attack_screen'])});")
    sql.append("COMMIT;")
    (HERE / "persist.sql").write_text("\n".join(sql) + "\n")


def main():
    ligands = prepare_ligands()
    recs = receptors()
    runs = run_matrix(ligands, recs)
    poses, summary = analyze(runs, ligands, recs)
    write_outputs(ligands, recs, runs, poses, summary)
    print(json.dumps({"run_key": RUN_KEY, "jobs": len(runs), "poses": len(poses), "status": "COMPLETE"}, indent=2))


if __name__ == "__main__":
    main()
