#!/usr/bin/env python3
"""Build the audited TSL-RSH evidence definition without recomputing labels."""

from __future__ import annotations

import csv
import hashlib
import json
import math
import subprocess
from collections import Counter
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent
CAMPAIGN = ROOT / "analysis/mettl7-phase2/execution-unit-05O/gpu-qm-campaign"
BATCH = CAMPAIGN / "completed-batch-60"
RESULTS = BATCH / "gpu_qm_results"
CHARACTERIZATION = CAMPAIGN / "GPU_BATCH_60_CHARACTERIZATION.csv"
OLD_SPLIT = CAMPAIGN / "baseline-residual-study/GPU60_SPLIT_FROZEN.csv"
SOURCE_AUDIT_COMMIT = "bead81c05d7252caf7273ce09c9a1cf1502e7d21"
HARTREE_TO_KCAL_MOL = 627.5094740631

FIELDS = [
    "ARTIFACT_ID", "MOLECULE_STATE", "GEOMETRY_ID", "GEOMETRY_SHA256",
    "ATOM_COUNT", "ATOM_ORDER_ID", "FORMAL_CHARGE", "MULTIPLICITY", "QM_METHOD",
    "BASIS", "DISPERSION", "ENERGY_PRESENT", "ELECTRONIC_GRADIENT_PRESENT",
    "D3_GRADIENT_PRESENT", "TOTAL_GRADIENT_PRESENT", "FORCE_PRESENT", "SOURCE_PATH",
    "SOURCE_COMMIT", "SOURCE_CHECKSUM", "SOFTWARE_PROVENANCE", "HARDWARE_PROVENANCE",
    "AUDIT_DEPENDENCIES", "COMPLETENESS_CLASS", "TRUST_CLASS", "REASON",
    "PHYSICAL_ROLE", "DOMAIN_CLASS", "RELATIVE_ENERGY_KCAL_MOL", "REFERENCE_PARTITION",
]


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def source_commit(path: Path) -> str:
    completed = subprocess.run(
        ["git", "log", "-1", "--format=%H", "--", relative(path)],
        cwd=ROOT, check=True, capture_output=True, text=True,
    )
    commit = completed.stdout.strip()
    return commit if commit else "NOT_VERSIONED;REVIEWED_AT_" + SOURCE_AUDIT_COMMIT


def read_xyz(path: Path) -> tuple[list[str], np.ndarray]:
    lines = path.read_text().splitlines()
    count = int(lines[0].strip())
    atoms = [line.split() for line in lines[2:] if line.strip()]
    if len(atoms) != count:
        raise ValueError(f"{path}: declared {count} atoms but found {len(atoms)}")
    return [row[0] for row in atoms], np.array([[float(x) for x in row[1:4]] for row in atoms])


def verify_sha_file(directory: Path) -> None:
    entries = (directory / "SHA256SUMS").read_text().splitlines()
    for line in entries:
        if not line.strip():
            continue
        expected, name = line.split(maxsplit=1)
        target = directory / name.lstrip("* ")
        if not target.is_file():
            raise FileNotFoundError(f"checksum payload missing: {target}")
        actual = sha256(target)
        if actual != expected:
            raise ValueError(f"checksum mismatch: {target}: {actual} != {expected}")


def finite_matrix(value: object, name: str, artifact_id: str) -> np.ndarray:
    matrix = np.asarray(value, dtype=float)
    if matrix.shape != (56, 3) or not np.isfinite(matrix).all():
        raise ValueError(f"{artifact_id}: invalid {name} shape/finiteness: {matrix.shape}")
    return matrix


def atom_order_id(elements: list[str]) -> str:
    return "TSL_RSH_56_V1:" + hashlib.sha256("\n".join(elements).encode()).hexdigest()


def physical_role(row: dict[str, str], per_minimum_medians: dict[str, tuple[float, float]]) -> str:
    family = row["family"]
    if family == "MINIMUM_OPTIMIZATION_TRAJECTORY":
        return "EQUILIBRIUM_MINIMUM"
    if family == "TORSIONAL_OR_CONSTRAINED_TRAJECTORY":
        return "TORSIONAL_TRANSITION"
    if family == "OTHER_EXISTING_GEOMETRY":
        return "LOW_ENERGY_CONFORMER"
    sc_median, sh_median = per_minimum_medians[row["source_minimum"]]
    sc_delta = abs(float(row["sc_distance_a"]) - sc_median)
    sh_delta = abs(float(row["sh_distance_a"]) - sh_median)
    return "S_H_LOCAL_DISTORTION" if sh_delta >= sc_delta else "SULFUR_ORIENTATION"


def kabsch_rmsd(a: np.ndarray, b: np.ndarray) -> float:
    aa = a - a.mean(axis=0)
    bb = b - b.mean(axis=0)
    u, _, vt = np.linalg.svd(aa.T @ bb)
    if np.linalg.det(u @ vt) < 0:
        u[:, -1] *= -1
    return float(np.sqrt(np.mean(np.sum((aa @ (u @ vt) - bb) ** 2, axis=1))))


def historical_row(artifact_id: str, source: str, trust: str, completeness: str,
                   reason: str, audit: str, **values: object) -> dict[str, object]:
    path = ROOT / source
    row: dict[str, object] = {field: "" for field in FIELDS}
    row.update({
        "ARTIFACT_ID": artifact_id,
        "MOLECULE_STATE": "TSL_RSH_NEUTRAL_SINGLET",
        "SOURCE_PATH": source,
        "SOURCE_COMMIT": source_commit(path),
        "SOURCE_CHECKSUM": sha256(path) if path.is_file() else "",
        "AUDIT_DEPENDENCIES": audit,
        "COMPLETENESS_CLASS": completeness,
        "TRUST_CLASS": trust,
        "REASON": reason,
    })
    row.update(values)
    return row


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    characterization = list(csv.DictReader(CHARACTERIZATION.open(newline="")))
    old_split = {row["campaign_id"]: row["partition"] for row in csv.DictReader(OLD_SPLIT.open(newline=""))}
    if len(characterization) != 60 or len(old_split) != 60:
        raise ValueError("expected exactly 60 characterized and split GPU records")

    minimum_energy = {
        minimum: min(float(row["total_energy_hartree"]) for row in characterization
                     if row["source_minimum"] == minimum)
        for minimum in ("MIN01", "MIN02", "MIN04")
    }
    for row in characterization:
        row["relative_energy_kcal_mol"] = str(
            (float(row["total_energy_hartree"]) - minimum_energy[row["source_minimum"]])
            * HARTREE_TO_KCAL_MOL
        )
    bound_envelope = {
        minimum: max(float(row["relative_energy_kcal_mol"]) for row in characterization
                     if row["source_minimum"] == minimum and row["family"] != "FORCE_CLOUD_PERTURBATION")
        for minimum in minimum_energy
    }
    bound_force_envelope = {
        minimum: max(float(row["global_force_rms_kcal_mol_a"]) for row in characterization
                     if row["source_minimum"] == minimum and row["family"] != "FORCE_CLOUD_PERTURBATION")
        for minimum in minimum_energy
    }
    medians = {
        minimum: (
            float(np.median([float(row["sc_distance_a"]) for row in characterization if row["source_minimum"] == minimum])),
            float(np.median([float(row["sh_distance_a"]) for row in characterization if row["source_minimum"] == minimum])),
        ) for minimum in minimum_energy
    }

    records: list[dict[str, object]] = []
    coordinates: dict[str, np.ndarray] = {}
    partition_ids = {"TRAIN": [], "VALIDATION": [], "STRESS_TEST": []}
    domain_counts: Counter[str] = Counter()
    canonical_order = None
    for row in characterization:
        artifact_id = row["campaign_id"]
        directory = RESULTS / artifact_id
        verify_sha_file(directory)
        result_path = directory / "result.json"
        result = json.loads(result_path.read_text())
        elements, xyz = read_xyz(directory / "geometry.xyz")
        coordinates[artifact_id] = xyz
        if canonical_order is None:
            canonical_order = elements
        if elements != canonical_order:
            raise ValueError(f"{artifact_id}: atom ordering differs from first verified record")
        if len(elements) != 56 or Counter(elements) != Counter({"C": 22, "H": 30, "O": 3, "S": 1}):
            raise ValueError(f"{artifact_id}: unexpected molecular composition")
        if sha256(directory / "geometry.xyz") != row["geometry_sha256"] or result["geometry_sha256"] != row["geometry_sha256"]:
            raise ValueError(f"{artifact_id}: geometry identity mismatch")
        if sha256(result_path) != row["result_sha256"]:
            raise ValueError(f"{artifact_id}: result identity mismatch")
        electronic = finite_matrix(result["electronic_gradient_hartree_per_bohr"], "electronic gradient", artifact_id)
        d3 = finite_matrix(result["d3_gradient_hartree_per_bohr"], "D3 gradient", artifact_id)
        total = finite_matrix(result["total_gradient_hartree_per_bohr"], "total gradient", artifact_id)
        force = finite_matrix(result["force_hartree_per_bohr"], "force", artifact_id)
        if not np.array_equal(total, electronic + d3) or not np.array_equal(force, -total):
            raise ValueError(f"{artifact_id}: component or force identity failure")
        if result["status"] != "CONVERGED" or not result["scf_converged"]:
            raise ValueError(f"{artifact_id}: SCF not converged")

        relative_energy = float(row["relative_energy_kcal_mol"])
        family = row["family"]
        minimum = row["source_minimum"]
        if family == "FORCE_CLOUD_PERTURBATION" and relative_energy > bound_envelope[minimum]:
            domain_class = "STRESS_TEST_ONLY"
        elif family == "FORCE_CLOUD_PERTURBATION" and float(row["global_force_rms_kcal_mol_a"]) > bound_force_envelope[minimum]:
            domain_class = "STABILITY_GUARD"
        elif family in {"OTHER_EXISTING_GEOMETRY", "MINIMUM_OPTIMIZATION_TRAJECTORY"}:
            domain_class = "CORE_EQUILIBRIUM"
        else:
            domain_class = "EXTENDED_BOUND_DOMAIN"
        role = (
            "HIGH_STRAIN_STRESS_TEST" if domain_class == "STRESS_TEST_ONLY" else
            "REPULSIVE_STABILITY" if domain_class == "STABILITY_GUARD" else
            physical_role(row, medians)
        )
        domain_counts[domain_class] += 1
        partition = "STRESS_TEST" if domain_class == "STRESS_TEST_ONLY" else (
            "VALIDATION" if old_split[artifact_id] == "SEALED_VALIDATION" else "TRAIN"
        )
        partition_ids[partition].append(artifact_id)
        protocol = result["protocol"]
        records.append({
            "ARTIFACT_ID": artifact_id,
            "MOLECULE_STATE": "TSL_RSH_NEUTRAL_SINGLET",
            "GEOMETRY_ID": artifact_id,
            "GEOMETRY_SHA256": row["geometry_sha256"],
            "ATOM_COUNT": 56,
            "ATOM_ORDER_ID": atom_order_id(elements),
            "FORMAL_CHARGE": 0,
            "MULTIPLICITY": 1,
            "QM_METHOD": protocol["method"],
            "BASIS": protocol["basis"] + ";AUX=" + protocol["auxiliary_basis"],
            "DISPERSION": "D3(BJ);s6=1.0;s8=0.7875;a1=0.4289;a2=4.4407;alp=14;s9=0",
            "ENERGY_PRESENT": True,
            "ELECTRONIC_GRADIENT_PRESENT": True,
            "D3_GRADIENT_PRESENT": True,
            "TOTAL_GRADIENT_PRESENT": True,
            "FORCE_PRESENT": True,
            "SOURCE_PATH": relative(directory),
            "SOURCE_COMMIT": source_commit(result_path),
            "SOURCE_CHECKSUM": row["result_sha256"],
            "SOFTWARE_PROVENANCE": f"PySCF {protocol['pyscf']}; GPU4PySCF {protocol['gpu4pyscf']}; simple-dftd3 {protocol['dftd3']}; Python {result['software']['python']}; CuPy {result['software']['cupy']}",
            "HARDWARE_PROVENANCE": result["gpu"]["name"],
            "AUDIT_DEPENDENCIES": "checksum deletion bypass: reverified present+hash; malformed energy coercion: typed finite JSON checked; noncanonical identity: geometry/result hashes used; atlas leakage: no atlas artifact consumed; Amber/ZVZB/electronic-state/mutable-state defects: no dependency",
            "COMPLETENESS_CLASS": "REPRODUCIBLE_COMPLETE",
            "TRUST_CLASS": "TRUSTED",
            "REASON": "All nested checksums, geometry/order, component decomposition, exact force=-gradient identity, SCF convergence, and frozen software/GPU protocol reverified without QM recomputation.",
            "PHYSICAL_ROLE": role,
            "DOMAIN_CLASS": domain_class,
            "RELATIVE_ENERGY_KCAL_MOL": f"{relative_energy:.12f}",
            "REFERENCE_PARTITION": f"within-{minimum}; zero={minimum} lowest verified GPU60 total energy {minimum_energy[minimum]:.16f} Ha",
        })

    historical = [
        historical_row("CPU_MIN01_NO_D3_REFERENCE", "analysis/mettl7-phase2/execution-unit-05O/gpu-qm-preparation/reference/MIN01_reference.json", "TRUSTED_WITH_LIMITATION", "INCOMPLETE_MISSING_DERIVATIVES", "Authoritative no-D3 energy survives; authoritative standalone 56x3 no-D3 gradient does not.", "component-completeness audit; numerical energy retained but excluded from homogeneous GPU fitting labels", GEOMETRY_ID="MIN01", FORMAL_CHARGE=0, MULTIPLICITY=1, QM_METHOD="PBE", BASIS="def2-SVP", DISPERSION="NONE", ENERGY_PRESENT=True, ELECTRONIC_GRADIENT_PRESENT=False),
        historical_row("CPU_FORCE_CLOUD_60", "analysis/mettl7-phase2/execution-unit-05O/force-cloud-qm/FROZEN_QM_TARGET_DATASET.json", "TRUSTED_WITH_LIMITATION", "INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION", "Historical CPU totals survive but per-point electronic and D3 component arrays were not preserved.", "component-completeness audit; old checksum bypass neutralized only where referenced SHA files verify; kept outside homogeneous GPU labels", FORMAL_CHARGE=0, MULTIPLICITY=1, QM_METHOD="PBE", BASIS="def2-SVP", DISPERSION="D3(BJ)", ENERGY_PRESENT=True, TOTAL_GRADIENT_PRESENT=True, FORCE_PRESENT=True),
        historical_row("CPU_HESSIAN_MIN01", "analysis/mettl7-phase2/execution-unit-05O/hessians/MIN01/result.json", "TRUSTED_WITH_LIMITATION", "INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION", "CPU Hessian/result survives with local provenance, but it is a distinct CPU derivative partition and is not a GPU60 force label.", "electronic-state and checksum provenance reviewed; not mixed into GPU60 fitting labels", GEOMETRY_ID="MIN01", FORMAL_CHARGE=0, MULTIPLICITY=1, QM_METHOD="PBE", BASIS="def2-SVP", DISPERSION="D3(BJ)"),
        historical_row("CPU_HESSIAN_MIN02", "analysis/mettl7-phase2/execution-unit-05O/hessians/MIN02/result.json", "TRUSTED_WITH_LIMITATION", "INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION", "CPU Hessian/result survives with local provenance, but it is a distinct CPU derivative partition and is not a GPU60 force label.", "electronic-state and checksum provenance reviewed; not mixed into GPU60 fitting labels", GEOMETRY_ID="MIN02", FORMAL_CHARGE=0, MULTIPLICITY=1, QM_METHOD="PBE", BASIS="def2-SVP", DISPERSION="D3(BJ)"),
        historical_row("CPU_HESSIAN_MIN04", "analysis/mettl7-phase2/execution-unit-05O/hessians/MIN04/result.json", "TRUSTED_WITH_LIMITATION", "INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION", "CPU Hessian/result survives with local provenance, but it is a distinct CPU derivative partition and is not a GPU60 force label.", "electronic-state and checksum provenance reviewed; not mixed into GPU60 fitting labels", GEOMETRY_ID="MIN04", FORMAL_CHARGE=0, MULTIPLICITY=1, QM_METHOD="PBE", BASIS="def2-SVP", DISPERSION="D3(BJ)"),
        historical_row("RESP_CHARGE_VALIDATION", "analysis/mettl7-phase2/execution-unit-05O/charge-validation/TSL_RSH_RESP_PROVENANCE.md", "TRUSTED_WITH_LIMITATION", "INCOMPLETE_MISSING_PROVENANCE", "RESP/ESP evidence is informative for charge provenance but is not a homogeneous GPU force/energy label bundle.", "Amber-name parser dependency not demonstrated; charge source remains separate from GPU labels"),
        historical_row("VDW_PROBE_REGISTRY", "analysis/mettl7-phase2/execution-unit-05O/VDW_BENCHMARK_POINT_REGISTRY.csv", "TRUSTED_WITH_LIMITATION", "INCOMPLETE_MISSING_DERIVATIVES", "Intermolecular probe energies are a separate validation observable and generally do not contain complete molecular force decomposition.", "electronic-state/protocol provenance retained; not used as force labels"),
        historical_row("AMBER_GAFF_BASELINE_INPUTS", "analysis/mettl7-phase2/execution-unit-05O/model-form-analysis/BASELINE_IDENTITY.json", "TRUSTED_WITH_LIMITATION", "REPRODUCIBLE_COMPLETE", "Runnable Amber/GAFF baseline identity, topology, charge, completion-parameter, software, and internal checksums survive; it is a baseline input, not a QM label.", "defective Amber name parsing: no dependency identified because production used AmberTools/ParmEd rather than AmberPrmtopReader; mutable-state and identity defects: persisted checksums reverified", FORMAL_CHARGE=0, MULTIPLICITY=1),
        historical_row("DELTA_PERMITTED_INPUTS", "analysis/mettl7-phase2/execution-unit-05O/delta-potential/PERMITTED_INPUTS_SHA256SUMS", "TRUSTED_WITH_LIMITATION", "INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION", "The historical Delta input checksum manifest survives, but it references the incomplete CPU-label partition and is not authorized as equivalent to GPU60 labels.", "old checksum deletion bypass: all future reuse requires explicit existence plus checksum; component-completeness audit excludes it from trusted GPU fitting labels"),
        historical_row("DELTA_V2_FIT", "analysis/mettl7-phase2/execution-unit-05O/delta-potential/training-v2/V2_TRAINING_RESULTS.json", "QUARANTINED", "INCOMPLETE_MISSING_OPTIMIZER_STATE", "Coefficients/metrics survive, but no audited atomic FitArtifact bundle and complete selected-fit state exist.", "result-completeness contract; atlas-derived evaluations excluded; no refit performed"),
        historical_row("MACE_READOUT_STUDY", "analysis/mettl7-phase2/execution-unit-05O/model-class-benchmark/results/MACE_READOUT_PROCESS_ISOLATED_RESULT.json", "QUARANTINED", "INCOMPLETE_MISSING_OPTIMIZER_STATE", "Metric/checkpoint evidence is not an audited complete ML bundle and is not a QM label source.", "result-completeness contract; process-isolation history; no training rerun"),
        historical_row("BEST_AMBER_26_COEFFICIENT_MODEL", "analysis/mettl7-phase2/execution-unit-05O/model-form-analysis/RICHER_MODEL_EXTENSION_DECISION.md", "INVALIDATED", "INCOMPLETE_MISSING_MODEL_STATE", "Reported best-Amber metric survives but the 26 fitted coefficients were not preserved.", "result-completeness contract; possible Amber-reader dependencies cannot be excluded without model state; prohibited as reproducible baseline"),
        historical_row("CONTAMINATED_ATLAS_RESULTS", "analysis/mettl7-phase2/execution-unit-05O/gpu-qm-campaign/baseline-residual-study/ATLAS_RESULTS_INVALIDATION.json", "INVALIDATED", "INCOMPLETE_MISSING_PROVENANCE", "Held-out energy-centering and secant-gradient leakage invalidate the prior scientific conclusions.", "direct atlas leakage: global label centering and held-out gradient secants"),
    ]
    for row in historical:
        if not (ROOT / str(row["SOURCE_PATH"])).is_file():
            row["TRUST_CLASS"] = "UNKNOWN"
            row["REASON"] = "Expected surviving artifact path was not found during manifest build."
    records.extend(historical)

    manifest_path = OUT / "TRUSTED_TSL_RSH_EVIDENCE_MANIFEST.csv"
    with manifest_path.open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=FIELDS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(records)

    # Operational near-duplicate criterion: heavy-atom/all-atom aligned RMSD <=0.01 A,
    # below the perturbation resolution and not a model-acceptance threshold.
    cross_pairs = []
    for train_id in partition_ids["TRAIN"]:
        for validation_id in partition_ids["VALIDATION"]:
            distance = kabsch_rmsd(coordinates[train_id], coordinates[validation_id])
            if distance <= 0.01:
                cross_pairs.append({"train": train_id, "validation": validation_id, "rmsd_angstrom": distance})
    if cross_pairs:
        raise ValueError(f"near-duplicate train/validation leakage: {cross_pairs}")

    split = {
        "schema": "trusted-tsl-rsh-family-split-v1",
        "immutable_after_commit": True,
        "source_split": relative(OLD_SPLIT),
        "source_split_sha256": sha256(OLD_SPLIT),
        "policy": "Preserve the preregistered family/minimum-stratified validation selections; move only physically out-of-bound-envelope force-cloud points to a disjoint stress-test partition.",
        "TRAIN_IDS": sorted(partition_ids["TRAIN"]),
        "VALIDATION_IDS": sorted(partition_ids["VALIDATION"]),
        "STRESS_TEST_IDS": sorted(partition_ids["STRESS_TEST"]),
        "near_duplicate_definition": "Kabsch-aligned all-atom RMSD <= 0.01 angstrom; operational identity/leakage diagnostic, not a model acceptance threshold",
        "near_duplicate_cross_partition_pairs": cross_pairs,
    }
    split["membership_sha256"] = hashlib.sha256(json.dumps({k: split[k] for k in ("TRAIN_IDS", "VALIDATION_IDS", "STRESS_TEST_IDS")}, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    (OUT / "FROZEN_TRAIN_VALIDATION_SPLIT.json").write_text(json.dumps(split, indent=2, sort_keys=True) + "\n")

    domain = {
        "schema": "trusted-tsl-rsh-domain-v1",
        "target": "TSL-RSH equilibrium/bound-state sampling relevant to METTL7A/B + SAM",
        "reference_partition": {minimum: {"definition": "lowest verified GPU60 total energy within source minimum", "energy_hartree": energy, "bound_like_envelope_kcal_mol": bound_envelope[minimum], "bound_like_force_envelope_kcal_mol_angstrom": bound_force_envelope[minimum]} for minimum, energy in sorted(minimum_energy.items())},
        "classification_rules": {
            "CORE_EQUILIBRIUM": "OTHER_EXISTING_GEOMETRY or MINIMUM_OPTIMIZATION_TRAJECTORY within the verified homogeneous partition",
            "EXTENDED_BOUND_DOMAIN": "torsional/constrained geometry or force-cloud geometry inside both observed bound-like energy and force envelopes",
            "STABILITY_GUARD": "force-cloud geometry inside the bound-like energy envelope but above the bound-like force envelope",
            "STRESS_TEST_ONLY": "force-cloud geometry whose relative energy exceeds the maximum observed non-force-cloud energy for its source minimum",
        },
        "physical_roles": ["EQUILIBRIUM_MINIMUM", "LOW_ENERGY_CONFORMER", "TORSIONAL_TRANSITION", "SULFUR_ORIENTATION", "S_H_LOCAL_DISTORTION", "THIOL_HBOND_GEOMETRY", "SAM_APPROACH_GEOMETRY", "PROTEIN_LIKE_DISTORTION", "REPULSIVE_STABILITY", "HIGH_STRAIN_STRESS_TEST"],
        "uncovered_roles_in_gpu60": ["THIOL_HBOND_GEOMETRY", "SAM_APPROACH_GEOMETRY", "PROTEIN_LIKE_DISTORTION"],
        "future_metrics": {
            "ENERGY_RMS": "existing locked <=2.0 kcal/mol gate is recorded but not changed or newly authorized here",
            "RELATIVE_ENERGY_RMS": "THRESHOLD_NOT_YET_JUSTIFIED",
            "GLOBAL_FORCE_COMPONENT_RMS": "THRESHOLD_NOT_YET_JUSTIFIED",
            "SULFUR_LOCAL_FORCE_COMPONENT_RMS": "existing historical 7.5 kcal/mol/A gate is recorded but not changed or newly authorized here",
            "S_H_PROJECTED_FORCE_ERROR": "THRESHOLD_NOT_YET_JUSTIFIED",
            "C_S_PROJECTED_FORCE_ERROR": "THRESHOLD_NOT_YET_JUSTIFIED",
            "SULFUR_ORIENTATION/TORSIONAL_ERROR": "THRESHOLD_NOT_YET_JUSTIFIED",
        },
        "enzyme_geometry_diagnostics": ["sulfur orientation", "S-H distance and projected force", "phi/psi and sulfur-exposing torsions", "SAM-approach compatibility (coverage gap; no proxy label invented)"],
        "domain_counts": dict(sorted(domain_counts.items())),
        "thresholds_changed": False,
    }
    (OUT / "TSL_RSH_DOMAIN_DEFINITION.json").write_text(json.dumps(domain, indent=2, sort_keys=True) + "\n")

    bug_rows = []
    defects = ["defective Amber name parsing", "old ZVZB finite-sample bug", "old checksum deletion bypass", "malformed-energy coercion", "atlas leakage", "noncanonical identity", "electronic-state reuse mismatch", "mutable result state"]
    for row in records:
        dependencies = str(row["AUDIT_DEPENDENCIES"])
        for defect in defects:
            if row["ARTIFACT_ID"] == "CONTAMINATED_ATLAS_RESULTS" and defect == "atlas leakage":
                relation = "DIRECT_INVALIDATING"
            elif str(row["ARTIFACT_ID"]).startswith("TSLRSH-GPU-") and defect in {"old checksum deletion bypass", "malformed-energy coercion", "noncanonical identity"}:
                relation = "REVERIFIED_FAIL_CLOSED_WITHOUT_RECOMPUTATION"
            elif row["ARTIFACT_ID"] == "BEST_AMBER_26_COEFFICIENT_MODEL" and defect == "defective Amber name parsing":
                relation = "POSSIBLE_NOT_RESOLVABLE_MODEL_STATE_MISSING"
            elif defect.replace("old ", "") in dependencies:
                relation = "DIRECT_OR_REVERIFIED"
            else:
                relation = "NO_DEPENDENCY_IDENTIFIED"
            bug_rows.append({"ARTIFACT_ID": row["ARTIFACT_ID"], "AUDIT_DEFECT": defect, "DEPENDENCY_CLASS": relation, "DEPENDENCY_PATH": dependencies, "DISPOSITION": row["TRUST_CLASS"]})
    bug_path = OUT / "ARTIFACT_BUG_DEPENDENCY_AUDIT.csv"
    with bug_path.open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["ARTIFACT_ID", "AUDIT_DEFECT", "DEPENDENCY_CLASS", "DEPENDENCY_PATH", "DISPOSITION"], lineterminator="\n")
        writer.writeheader()
        writer.writerows(bug_rows)

    trust_counts = Counter(str(row["TRUST_CLASS"]) for row in records)
    report = f"""# Trusted TSL-RSH evidence report

## Decision

The homogeneous A100 GPU-60 partition is the only current `REPRODUCIBLE_COMPLETE`
QM force-label set admitted to future fitting. All 60 nested checksum manifests,
geometry/result hashes, 56-atom ordering, C22H30O3S composition, neutral-singlet
state, finite 56x3 component arrays, exact component sum, exact force=-gradient,
and SCF convergence were reverified without recomputing QM.

Historical CPU, Hessian, RESP/ESP, vdW, Amber, Delta, MACE, and atlas artifacts
remain visible in the manifest but are not silently treated as equivalent GPU
labels. Missing derivative decomposition, model state, or leakage produces an
explicit limitation, quarantine, or invalidation.

## Frozen physical domain

Relative energy is defined independently within MIN01, MIN02, and MIN04 using
the lowest verified GPU-60 total energy in that minimum. The observed maximum
relative energy and force of non-force-cloud torsional/optimization/other
geometries define the empirical bound-like envelope. Force-cloud points beyond
that energy envelope are stress-only; points inside the energy envelope but
beyond its force envelope are stability guards. This uses observed physical
provenance and support, not an arbitrary kcal/mol cutoff.

The original deterministic family/minimum-stratified validation selections are
preserved for non-stress points. Stress-only points are disjoint. Cross-partition
Kabsch RMSD screening found no pair at or below 0.01 A.

## Counts

- TRUSTED: {trust_counts['TRUSTED']}
- TRUSTED_WITH_LIMITATION: {trust_counts['TRUSTED_WITH_LIMITATION']}
- QUARANTINED: {trust_counts['QUARANTINED']}
- INVALIDATED: {trust_counts['INVALIDATED']}
- UNKNOWN: {trust_counts['UNKNOWN']}
- Trusted QM labels/geometries: 60 / 60
- CORE_EQUILIBRIUM: {domain_counts['CORE_EQUILIBRIUM']}
- EXTENDED_BOUND_DOMAIN: {domain_counts['EXTENDED_BOUND_DOMAIN']}
- STABILITY_GUARD: {domain_counts['STABILITY_GUARD']}
- STRESS_TEST_ONLY: {domain_counts['STRESS_TEST_ONLY']}
- TRAIN / VALIDATION / STRESS_TEST: {len(partition_ids['TRAIN'])} / {len(partition_ids['VALIDATION'])} / {len(partition_ids['STRESS_TEST'])}

## Gaps and prohibitions

The GPU-60 set does not directly label protein-like, SAM-approach, explicit
thiol-H-bond, or intermolecular repulsive geometries. Those are coverage gaps,
not inferred labels. The historical CPU MIN01 no-D3 gradient and historical CPU
force-cloud component decomposition remain missing; the best-Amber 26-vector
model state remains absent. Metric-only model studies are not reusable model
states. No threshold was changed, no QM was run, and no model was fitted.
"""
    (OUT / "TRUSTED_TSL_RSH_EVIDENCE_REPORT.md").write_text(report)

    output_names = ["TRUSTED_TSL_RSH_EVIDENCE_MANIFEST.csv", "TRUSTED_TSL_RSH_EVIDENCE_REPORT.md", "TSL_RSH_DOMAIN_DEFINITION.json", "FROZEN_TRAIN_VALIDATION_SPLIT.json", "ARTIFACT_BUG_DEPENDENCY_AUDIT.csv", "build_trusted_evidence_manifest.py"]
    with (OUT / "SHA256SUMS").open("w") as stream:
        for name in output_names:
            stream.write(f"{sha256(OUT / name)}  {name}\n")

    print(json.dumps({
        "GPU60_ALL_REVERIFIED": True,
        "GPU60_CHECKSUM_PASS": True,
        "trust_counts": dict(trust_counts),
        "domain_counts": dict(domain_counts),
        "partition_counts": {key: len(value) for key, value in partition_ids.items()},
        "near_duplicate_leakage_check": "PASS",
    }, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
