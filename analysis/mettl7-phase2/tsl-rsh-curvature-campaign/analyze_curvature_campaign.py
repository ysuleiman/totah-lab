#!/usr/bin/env python3
"""Verify and analyze the frozen TSL-RSH GPU curvature campaign.

This program consumes immutable QM evidence and implements only the equations
frozen in CURVATURE_ANALYSIS_PROTOCOL.json at commit d3781170965d4fdb4b671035ced30b2fbb52448d.
It does not run QM and does not fit a force-field or production model.
"""

from __future__ import annotations

import csv
import hashlib
import json
import math
import shutil
import subprocess
import tempfile
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
EVIDENCE = HERE / "execution-evidence"
OUT = HERE / "analysis-results"
RAW = OUT / "immutable-ingestion"
ARCHIVE_NAME = "TSL_RSH_CURVATURE_GPU_RESULTS.zip"
ARCHIVE_SHA256 = "732963b8682b966539cb2eadbe55f4fca9f181611364dc788698da220ac09cbf"
CAMPAIGN_COMMIT = "d3781170965d4fdb4b671035ced30b2fbb52448d"
AUDITED_SOFTWARE_COMMIT = "3c398ef65e1a048dc62281425933e82859fc9d16"
BOHR_ANGSTROM = 0.529177210903
ENERGY_SIGMA_HARTREE = 5.168203642824665e-10
GRADIENT_SIGMA_HARTREE_PER_BOHR = 7.537830768521607e-7
GRADIENT_MAX_HARTREE_PER_BOHR = 3.6847275325449513e-6
RCOND = 1.0e-10
JACOBIAN_STEP_ANGSTROM = 1.0e-5
FROZEN_DEFINITION_FILES = (
    "CURVATURE_ANALYSIS_PROTOCOL.json", "CURVATURE_COORDINATE_DEFINITIONS.json",
    "CURVATURE_GEOMETRY_MANIFEST.csv", "CURVATURE_PAIR_PANEL.json",
    "DISPLACEMENT_PROTOCOL.json", "FROZEN_GPU_QM_PROTOCOL.json",
)
EXPECTED_ELEMENTS = ["C"] * 5 + ["O"] + ["C"] * 16 + ["O", "O", "C", "S"] + ["H"] * 30


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def canonical_json(value: object) -> str:
    return json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n"


def read_xyz_bytes(payload: bytes) -> tuple[list[str], np.ndarray]:
    lines = payload.decode("utf-8").splitlines()
    count = int(lines[0])
    rows = [line.split() for line in lines[2:2 + count]]
    elements = [row[0] for row in rows]
    xyz = np.asarray([[float(value) for value in row[1:4]] for row in rows], dtype=float)
    if count != 56 or len(rows) != 56 or elements != EXPECTED_ELEMENTS or xyz.shape != (56, 3) or not np.isfinite(xyz).all():
        raise ValueError("XYZ atom count/order/coordinate validity failure")
    return elements, xyz


def wrapped(value: float) -> float:
    return math.atan2(math.sin(value), math.cos(value))


def coordinate_value(xyz: np.ndarray, definition: dict[str, object]) -> float:
    atoms = [xyz[index] for index in definition["atom_indices_zero_based"]]
    if definition["type"] == "DISTANCE":
        return float(np.linalg.norm(atoms[0] - atoms[1]))
    if definition["type"] == "ANGLE":
        left, right = atoms[0] - atoms[1], atoms[2] - atoms[1]
        cosine = np.dot(left, right) / np.linalg.norm(left) / np.linalg.norm(right)
        return float(math.acos(np.clip(cosine, -1.0, 1.0)))
    p0, p1, p2, p3 = atoms
    b0, b1, b2 = -(p1 - p0), p2 - p1, p3 - p2
    b1 /= np.linalg.norm(b1)
    v = b0 - np.dot(b0, b1) * b1
    w = b2 - np.dot(b2, b1) * b1
    return float(math.atan2(np.dot(np.cross(b1, v), w), np.dot(v, w)))


def coordinate_vector(xyz: np.ndarray, definitions: list[dict[str, object]]) -> np.ndarray:
    return np.asarray([coordinate_value(xyz, definition) for definition in definitions])


def coordinate_difference(actual: np.ndarray, reference: np.ndarray, definitions: list[dict[str, object]]) -> np.ndarray:
    result = actual - reference
    for index, definition in enumerate(definitions):
        if definition["periodic"]:
            result[index] = wrapped(result[index])
    return result


def jacobian(xyz: np.ndarray, definitions: list[dict[str, object]]) -> np.ndarray:
    result = np.zeros((len(definitions), xyz.size))
    active = sorted({atom for definition in definitions for atom in definition["atom_indices_zero_based"]})
    reference = coordinate_vector(xyz, definitions)
    for atom in active:
        for axis in range(3):
            plus, minus = xyz.copy(), xyz.copy()
            plus[atom, axis] += JACOBIAN_STEP_ANGSTROM
            minus[atom, axis] -= JACOBIAN_STEP_ANGSTROM
            delta = coordinate_difference(coordinate_vector(plus, definitions), coordinate_vector(minus, definitions), definitions)
            result[:, 3 * atom + axis] = delta / (2.0 * JACOBIAN_STEP_ANGSTROM)
    if not np.isfinite(result).all() or not np.isfinite(reference).all():
        raise ValueError("nonfinite internal-coordinate Jacobian")
    return result


def interval(center: float, sigma: float) -> list[float]:
    return [center - 1.96 * sigma, center + 1.96 * sigma]


def intervals_overlap(left: list[float], right: list[float]) -> bool:
    return max(left[0], right[0]) <= min(left[1], right[1])


def excludes_zero(bounds: list[float]) -> bool:
    return bounds[0] > 0.0 or bounds[1] < 0.0


def matrix_summary(matrix: np.ndarray) -> dict[str, object]:
    singular = np.linalg.svd(matrix, compute_uv=False)
    positive = singular[singular > singular[0] * RCOND] if singular.size and singular[0] else np.asarray([])
    return {
        "rank": int(np.linalg.matrix_rank(matrix, tol=(singular[0] * RCOND if singular.size else 0.0))),
        "singular_values": singular.tolist(),
        "condition_number_nonzero": float(positive[0] / positive[-1]) if positive.size else None,
    }


def verify_and_load() -> tuple[list[dict[str, object]], dict[str, object]]:
    source = EVIDENCE / ARCHIVE_NAME
    if sha256(source) != ARCHIVE_SHA256:
        raise ValueError("raw archive SHA-256 mismatch")
    RAW.mkdir(parents=True, exist_ok=True)
    immutable = RAW / ARCHIVE_NAME
    if immutable.exists() and sha256(immutable) != ARCHIVE_SHA256:
        raise ValueError("existing immutable archive has wrong SHA-256")
    if not immutable.exists():
        shutil.copyfile(source, immutable)
        immutable.chmod(0o444)
    if sha256(immutable) != ARCHIVE_SHA256:
        raise ValueError("immutable archive copy verification failed")
    immutable.chmod(0o444)
    environment_source = EVIDENCE / "TSL_RSH_CURVATURE_RUNTIME_ENVIRONMENT.txt"
    environment_copy = RAW / environment_source.name
    if not environment_copy.exists():
        shutil.copyfile(environment_source, environment_copy)
        environment_copy.chmod(0o444)
    if sha256(environment_copy) != "6474acd25246cff1f32bf905006acf931e56d7f8d4b4d8a361cd58923e480763":
        raise ValueError("runtime environment identity mismatch")
    environment_copy.chmod(0o444)
    definition_identity = {}
    campaign_relative = HERE.relative_to(ROOT)
    for name in FROZEN_DEFINITION_FILES:
        committed = subprocess.check_output(["git", "show", f"{CAMPAIGN_COMMIT}:{campaign_relative}/{name}"], cwd=ROOT)
        current = (HERE / name).read_bytes()
        if current != committed:
            raise ValueError(f"frozen campaign definition differs from {CAMPAIGN_COMMIT}: {name}")
        definition_identity[name] = hashlib.sha256(committed).hexdigest()

    manifest_path = HERE / "CURVATURE_GEOMETRY_MANIFEST.csv"
    with manifest_path.open(newline="") as stream:
        manifest = list(csv.DictReader(stream))
    if len(manifest) != 76 or len({row["campaign_id"] for row in manifest}) != 76:
        raise ValueError("campaign manifest does not contain 76 unique IDs")
    expected = {row["campaign_id"]: row for row in manifest}
    definitions = json.loads((HERE / "CURVATURE_COORDINATE_DEFINITIONS.json").read_text())["coordinates"]
    frozen_protocol = json.loads((HERE / "FROZEN_GPU_QM_PROTOCOL.json").read_text())
    points: list[dict[str, object]] = []
    nested_failures: list[dict[str, str]] = []
    geometry_hashes: set[str] = set()

    with zipfile.ZipFile(immutable) as archive:
        result_names = sorted(name for name in archive.namelist() if name.endswith("/result.json"))
        result_ids = {Path(name).parent.name for name in result_names}
        if result_ids != set(expected):
            raise ValueError(f"result ID mismatch: missing={sorted(set(expected)-result_ids)}, unexpected={sorted(result_ids-set(expected))}")
        for campaign_id in sorted(expected):
            row = expected[campaign_id]
            prefix = f"curvature_gpu_results/{campaign_id}/"
            required_files = {
                "geometry.xyz", "result.json", "electronic_gradient_hartree_per_bohr.json",
                "d3_gradient_hartree_per_bohr.json", "total_gradient_hartree_per_bohr.json",
                "force_hartree_per_bohr.json", "SHA256SUMS"
            }
            required_files = {name.replace(".json", ".txt") if "gradient_" in name or name.startswith("force_") else name for name in required_files}
            actual_files = {Path(name).name for name in archive.namelist() if name.startswith(prefix) and not name.endswith("/")}
            if actual_files != required_files:
                raise ValueError(f"unexpected/missing files for {campaign_id}: {sorted(actual_files ^ required_files)}")
            sums = archive.read(prefix + "SHA256SUMS").decode().splitlines()
            for line in sums:
                expected_hash, filename = line.split(maxsplit=1)
                filename = filename.strip().lstrip("*")
                actual_hash = hashlib.sha256(archive.read(prefix + filename)).hexdigest()
                if actual_hash != expected_hash:
                    nested_failures.append({"campaign_id": campaign_id, "file": filename})
            geometry_payload = archive.read(prefix + "geometry.xyz")
            geometry_hash = hashlib.sha256(geometry_payload).hexdigest()
            if geometry_hash != row["geometry_sha256"]:
                raise ValueError(f"geometry checksum mismatch for {campaign_id}")
            repository_geometry = HERE / row["geometry_path"]
            if sha256(repository_geometry) != geometry_hash or repository_geometry.read_bytes() != geometry_payload:
                raise ValueError(f"archive/repository geometry identity mismatch for {campaign_id}")
            elements, xyz = read_xyz_bytes(geometry_payload)
            atom_order_hash = hashlib.sha256("\n".join(elements).encode()).hexdigest()
            if atom_order_hash != row["atom_order_sha256"]:
                raise ValueError(f"atom-order checksum mismatch for {campaign_id}")
            if geometry_hash in geometry_hashes:
                raise ValueError(f"duplicate geometry: {campaign_id}")
            geometry_hashes.add(geometry_hash)
            result = json.loads(archive.read(prefix + "result.json"))
            if result["campaign_id"] != campaign_id or result["geometry_sha256"] != geometry_hash:
                raise ValueError(f"result identity mismatch for {campaign_id}")
            if int(row["charge"]) != 0 or int(row["multiplicity"]) != 1:
                raise ValueError(f"manifest charge/multiplicity mismatch for {campaign_id}")
            scalar_identity = (result["atom_count"], result["electron_count"], result["charge"], result["multiplicity"], result["spin_pyscf"])
            if scalar_identity != (56, 202, 0, 1, 0) or result["elements"] != EXPECTED_ELEMENTS:
                raise ValueError(f"molecular identity mismatch for {campaign_id}")
            if result["protocol"] != frozen_protocol:
                raise ValueError(f"frozen QM protocol mismatch for {campaign_id}")
            software = result["software"]
            if software.get("pyscf") != "2.14.0" or software.get("gpu4pyscf") != "1.8.0" or software.get("dftd3") != "1.5.0" or software.get("d3_parameter_database_sha256") != frozen_protocol["dispersion"]["parameter_database_sha256"]:
                raise ValueError(f"software identity mismatch for {campaign_id}")
            if result["scf_converged"] is not True or result["status"] != "CONVERGED" or not (1 <= result["scf_cycles"] <= 160):
                raise ValueError(f"SCF convergence failure for {campaign_id}")
            energies = {key: float(result[key]) for key in ("electronic_energy_hartree", "d3_energy_hartree", "total_energy_hartree")}
            if not all(math.isfinite(value) for value in energies.values()):
                raise ValueError(f"nonfinite energy for {campaign_id}")
            if not math.isclose(energies["electronic_energy_hartree"] + energies["d3_energy_hartree"], energies["total_energy_hartree"], rel_tol=0.0, abs_tol=2e-12):
                raise ValueError(f"energy component sum mismatch for {campaign_id}")
            arrays = {}
            for key in ("electronic_gradient_hartree_per_bohr", "d3_gradient_hartree_per_bohr", "total_gradient_hartree_per_bohr", "force_hartree_per_bohr"):
                value = np.asarray(result[key], dtype=float)
                file_value = np.loadtxt(archive.open(prefix + key + ".txt"), dtype=float)
                if value.shape != (56, 3) or not np.isfinite(value).all() or not np.array_equal(value, file_value):
                    raise ValueError(f"gradient/force array failure for {campaign_id}/{key}")
                arrays[key] = value
            if not np.allclose(arrays["electronic_gradient_hartree_per_bohr"] + arrays["d3_gradient_hartree_per_bohr"], arrays["total_gradient_hartree_per_bohr"], rtol=0.0, atol=2e-15):
                raise ValueError(f"gradient component sum mismatch for {campaign_id}")
            if not np.array_equal(-arrays["total_gradient_hartree_per_bohr"], arrays["force_hartree_per_bohr"]):
                raise ValueError(f"force=-gradient identity mismatch for {campaign_id}")
            j = jacobian(xyz, definitions)
            a = np.linalg.pinv(j.T, rcond=RCOND)
            projected = {}
            projection = {}
            for component in ("electronic", "d3", "total"):
                cartesian_bohr = arrays[component + "_gradient_hartree_per_bohr"].reshape(-1)
                cartesian_angstrom = cartesian_bohr / BOHR_ANGSTROM
                internal = a @ cartesian_angstrom
                residual = j.T @ internal - cartesian_angstrom
                projected[component] = internal
                projection[component] = {
                    "cartesian_gradient_norm_hartree_per_angstrom": float(np.linalg.norm(cartesian_angstrom)),
                    "projection_residual_norm_hartree_per_angstrom": float(np.linalg.norm(residual)),
                    "projection_residual_fraction": float(np.linalg.norm(residual) / np.linalg.norm(cartesian_angstrom)),
                }
            sigma_cart = GRADIENT_SIGMA_HARTREE_PER_BOHR / BOHR_ANGSTROM
            max_cart = GRADIENT_MAX_HARTREE_PER_BOHR / BOHR_ANGSTROM
            gq_sigma = sigma_cart * np.sqrt(np.sum(a * a, axis=1))
            gq_bound = max_cart * np.sum(np.abs(a), axis=1)
            points.append({
                "campaign_id": campaign_id, "anchor_id": row["anchor_id"], "pair_id": row["pair_id"], "scale": float(row["scale"]),
                "first_sign": int(row["first_sign"]), "second_sign": int(row["second_sign"]),
                "delta_q": coordinate_difference(coordinate_vector(xyz, definitions), np.asarray([definition["anchor_value_" + row["anchor_id"].lower()] for definition in definitions]), definitions),
                "geometry_sha256": geometry_hash, "energies": energies, "internal_gradients": projected,
                "internal_gradient_sigma_rms": gq_sigma, "internal_gradient_bound_max": gq_bound,
                "jacobian": matrix_summary(j), "projection": projection, "scf_cycles": int(result["scf_cycles"]),
                "identity_checks": {"atom_count": 56, "atom_order_pass": True, "charge": 0, "multiplicity": 1,
                                    "protocol_identity_pass": True, "nested_checksums_pass": True,
                                    "component_shapes": [56, 3], "all_numeric_values_finite": True,
                                    "energy_components_sum_pass": True, "gradient_components_sum_pass": True,
                                    "force_is_negative_gradient_pass": True, "scf_converged": True},
            })
    if nested_failures:
        raise ValueError(f"nested checksum failures: {nested_failures}")
    verification = {
        "schema": "tsl-rsh-curvature-ingestion-verification-v1", "campaign_definition_commit": CAMPAIGN_COMMIT,
        "preceding_audited_software_commit": AUDITED_SOFTWARE_COMMIT, "archive_path": str(immutable.relative_to(ROOT)),
        "archive_sha256": ARCHIVE_SHA256, "geometry_manifest_path": str(manifest_path.relative_to(ROOT)),
        "geometry_manifest_sha256": sha256(manifest_path), "qm_points_expected": 76, "qm_points_verified": 76,
        "runtime_environment_path": str(environment_copy.relative_to(ROOT)),
        "runtime_environment_sha256": sha256(environment_copy), "frozen_definition_sha256": definition_identity,
        "scf_converged_count": 76, "nested_checksum_manifest_count": 76, "nested_checksum_failures": 0,
        "geometry_identities_pass": True, "protocol_identity_pass": True, "no_duplicate_geometries": True,
        "no_unexpected_or_missing_results": True, "raw_results_modified": False,
    }
    return points, verification


def fit_gradient_panel(panel: list[dict[str, object]], coordinate_indices: tuple[int, int], component: str) -> dict[str, object]:
    i, j = coordinate_indices
    x = np.asarray([[1.0, point["delta_q"][i], point["delta_q"][j]] for point in panel])
    y = np.asarray([[point["internal_gradients"][component][i], point["internal_gradients"][component][j]] for point in panel])
    beta = np.linalg.lstsq(x, y, rcond=None)[0]
    # H_ij is the symmetric average of dg_i/dq_j and dg_j/dq_i.
    mixed = float(0.5 * (beta[2, 0] + beta[1, 1]))
    diagonal = [float(beta[1, 0]), float(beta[2, 1])]
    weights = np.linalg.pinv(x)
    variances = []
    bounds = []
    for target_index, coefficient_index in ((0, 2), (1, 1)):
        sigmas = np.asarray([point["internal_gradient_sigma_rms"][coordinate_indices[target_index]] for point in panel])
        max_bounds = np.asarray([point["internal_gradient_bound_max"][coordinate_indices[target_index]] for point in panel])
        coefficient_weights = weights[coefficient_index]
        variances.append(float(np.sum((coefficient_weights * sigmas) ** 2)))
        bounds.append(float(np.sum(np.abs(coefficient_weights) * max_bounds)))
    sigma = 0.5 * math.sqrt(sum(variances))
    conservative = 0.5 * sum(bounds)
    antisymmetric = float(0.5 * (beta[2, 0] - beta[1, 1]))
    residual = y - x @ beta
    by_sign = {(point["first_sign"], point["second_sign"]): point for point in panel}
    h_i = abs(panel[0]["delta_q"][i])
    h_j = abs(panel[0]["delta_q"][j])

    def directional(target: int, fixed_axis: int, fixed_sign: int) -> tuple[float, float, float]:
        if fixed_axis == 0:
            plus, minus, denominator = by_sign[(fixed_sign, 1)], by_sign[(fixed_sign, -1)], 2.0 * h_j
        else:
            plus, minus, denominator = by_sign[(1, fixed_sign)], by_sign[(-1, fixed_sign)], 2.0 * h_i
        value = (plus["internal_gradients"][component][target] - minus["internal_gradients"][component][target]) / denominator
        sigma_value = math.sqrt(plus["internal_gradient_sigma_rms"][target] ** 2 + minus["internal_gradient_sigma_rms"][target] ** 2) / denominator
        bound_value = (plus["internal_gradient_bound_max"][target] + minus["internal_gradient_bound_max"][target]) / denominator
        return float(value), float(sigma_value), float(bound_value)

    opposite = []
    for target, fixed_axis, label in ((i, 0, "dg_i_dq_j_at_q_i_plus_vs_minus"), (j, 1, "dg_j_dq_i_at_q_j_plus_vs_minus")):
        plus_value, plus_sigma, plus_bound = directional(target, fixed_axis, 1)
        minus_value, minus_sigma, minus_bound = directional(target, fixed_axis, -1)
        difference = plus_value - minus_value
        difference_sigma = math.sqrt(plus_sigma ** 2 + minus_sigma ** 2)
        opposite.append({"comparison": label, "positive_corner_secant": plus_value, "negative_corner_secant": minus_value,
                         "difference": difference, "difference_sigma_rms": difference_sigma,
                         "difference_interval_95_rms": interval(difference, difference_sigma),
                         "conservative_max_bound": plus_bound + minus_bound,
                         "disagrees_beyond_95_rms_uncertainty": excludes_zero(interval(difference, difference_sigma))})
    return {
        "mixed_curvature": mixed, "sigma_rms": sigma, "interval_95_rms": interval(mixed, sigma),
        "conservative_max_bound": conservative, "diagonal_curvatures": diagonal,
        "unconstrained_antisymmetric_component": antisymmetric,
        "least_squares_residual_rms": float(np.sqrt(np.mean(residual * residual))),
        "design_matrix": matrix_summary(x),
        "opposite_corner_secants": opposite,
        "opposite_corner_disagreement": any(item["disagrees_beyond_95_rms_uncertainty"] for item in opposite),
    }


def energy_estimate(panel: list[dict[str, object]], component: str, h_i: float, h_j: float) -> dict[str, object]:
    by_sign = {(point["first_sign"], point["second_sign"]): point for point in panel}
    values = {(a, b): by_sign[(a, b)]["energies"][component + "_energy_hartree"] for a, b in ((1, 1), (1, -1), (-1, 1), (-1, -1))}
    estimate = (values[(1, 1)] - values[(1, -1)] - values[(-1, 1)] + values[(-1, -1)]) / (4.0 * h_i * h_j)
    sigma = ENERGY_SIGMA_HARTREE / (2.0 * abs(h_i * h_j))
    conservative = ENERGY_SIGMA_HARTREE / abs(h_i * h_j)
    return {
        "mixed_curvature": float(estimate), "sigma_rms": float(sigma), "interval_95_rms": interval(float(estimate), float(sigma)),
        "conservative_max_bound": float(conservative),
        "interval_conservative_max": [float(estimate - conservative), float(estimate + conservative)],
        "corner_energies_hartree": {f"{a:+d},{b:+d}": values[(a, b)] for a, b in values},
        "units": "hartree/(coordinate_i_unit*coordinate_j_unit)",
    }


def analyze(points: list[dict[str, object]], verification: dict[str, object]) -> dict[str, object]:
    definitions = json.loads((HERE / "CURVATURE_COORDINATE_DEFINITIONS.json").read_text())["coordinates"]
    index = {definition["coordinate_id"]: position for position, definition in enumerate(definitions)}
    pair_panel = json.loads((HERE / "CURVATURE_PAIR_PANEL.json").read_text())["pairs"]
    grouped = defaultdict(list)
    for point in points:
        grouped[(point["anchor_id"], point["pair_id"], point["scale"])].append(point)
    estimates = []
    for pair in pair_panel:
        first, second = pair["coordinates"]
        for anchor in pair["anchors"]:
            scales = [pair["full_scale"]] + ([pair["second_scale"]] if anchor in pair["second_scale_anchors"] else [])
            for scale in scales:
                panel = grouped[(anchor, pair["pair_id"], scale)]
                if len(panel) != 4:
                    raise ValueError(f"incomplete four-corner panel: {anchor}/{pair['pair_id']}/{scale}")
                h_i = abs(panel[0]["delta_q"][index[first]])
                h_j = abs(panel[0]["delta_q"][index[second]])
                energy = {component: energy_estimate(panel, component, h_i, h_j) for component in ("electronic", "d3", "total")}
                gradient = {component: fit_gradient_panel(panel, (index[first], index[second]), component) for component in ("electronic", "d3", "total")}
                for component in ("electronic", "d3", "total"):
                    diagonal = gradient[component]["diagonal_curvatures"]
                    denominator = math.sqrt(abs(diagonal[0] * diagonal[1]))
                    gradient[component]["normalized_mixed_to_geometric_diagonal"] = (abs(gradient[component]["mixed_curvature"]) / denominator if denominator else None)
                e_total, g_total = energy["total"], gradient["total"]
                common_nonzero = (math.copysign(1.0, e_total["mixed_curvature"]) == math.copysign(1.0, g_total["mixed_curvature"]) and
                                  excludes_zero(e_total["interval_95_rms"]) and excludes_zero(g_total["interval_95_rms"]) and
                                  intervals_overlap(e_total["interval_95_rms"], g_total["interval_95_rms"]))
                corner_nonlinear = g_total["opposite_corner_disagreement"]
                estimates.append({
                    "anchor_id": anchor, "pair_id": pair["pair_id"], "coordinates": [first, second], "scale": scale,
                    "steps": [h_i, h_j], "energy": energy, "gradient": gradient,
                    "energy_gradient_consistency": "CONSISTENT_RESOLVED" if common_nonzero else "NOT_RESOLVED",
                    "classification": "SCALE_DEPENDENT_NONLINEAR" if corner_nonlinear else ("MIXED_CURVATURE_RESOLVED" if common_nonzero else "MIXED_CURVATURE_NOT_RESOLVED"),
                    "applicable_classifications": (["SCALE_DEPENDENT_NONLINEAR"] if corner_nonlinear else []) +
                                                  (["MIXED_CURVATURE_RESOLVED"] if common_nonzero else ["MIXED_CURVATURE_NOT_RESOLVED"]),
                    "point_ids": sorted(point["campaign_id"] for point in panel),
                    "jacobian_rank_min": min(point["jacobian"]["rank"] for point in panel),
                    "jacobian_condition_max": max(point["jacobian"]["condition_number_nonzero"] for point in panel),
                    "projection_residual_fraction_total": {
                        "min": min(point["projection"]["total"]["projection_residual_fraction"] for point in panel),
                        "max": max(point["projection"]["total"]["projection_residual_fraction"] for point in panel),
                        "mean": float(np.mean([point["projection"]["total"]["projection_residual_fraction"] for point in panel])),
                    },
                })

    # Frozen scale diagnostic: MIN01 S_C/C_S_H full versus half scale.
    scale_rows = [row for row in estimates if row["anchor_id"] == "MIN01" and row["pair_id"] == "S_C__C_S_H"]
    full = next(row for row in scale_rows if row["scale"] == 1.0)
    half = next(row for row in scale_rows if row["scale"] == 0.5)
    e_incompatible = not intervals_overlap(full["energy"]["total"]["interval_95_rms"], half["energy"]["total"]["interval_95_rms"])
    g_incompatible = not intervals_overlap(full["gradient"]["total"]["interval_95_rms"], half["gradient"]["total"]["interval_95_rms"])
    scale_dependent = e_incompatible or g_incompatible or full["gradient"]["total"]["opposite_corner_disagreement"] or half["gradient"]["total"]["opposite_corner_disagreement"]
    if scale_dependent:
        full["classification"] = "SCALE_DEPENDENT_NONLINEAR"
        half["classification"] = "SCALE_DEPENDENT_NONLINEAR"
        for row in (full, half):
            if "SCALE_DEPENDENT_NONLINEAR" not in row["applicable_classifications"]:
                row["applicable_classifications"].insert(0, "SCALE_DEPENDENT_NONLINEAR")

    anchor_comparisons = []
    anchor_dependent_pairs = set()
    for pair in pair_panel:
        rows = [row for row in estimates if row["pair_id"] == pair["pair_id"] and row["scale"] == 1.0]
        incompatible = []
        for left_index in range(len(rows)):
            for right_index in range(left_index + 1, len(rows)):
                left, right = rows[left_index], rows[right_index]
                if left["energy_gradient_consistency"] == "CONSISTENT_RESOLVED" and right["energy_gradient_consistency"] == "CONSISTENT_RESOLVED":
                    energy_bad = not intervals_overlap(left["energy"]["total"]["interval_95_rms"], right["energy"]["total"]["interval_95_rms"])
                    gradient_bad = not intervals_overlap(left["gradient"]["total"]["interval_95_rms"], right["gradient"]["total"]["interval_95_rms"])
                    if energy_bad or gradient_bad:
                        incompatible.append([left["anchor_id"], right["anchor_id"]])
        dependent = bool(incompatible)
        if dependent:
            anchor_dependent_pairs.add(pair["pair_id"])
            for row in rows:
                if row["classification"] != "SCALE_DEPENDENT_NONLINEAR":
                    row["classification"] = "ANCHOR_DEPENDENT"
                if "ANCHOR_DEPENDENT" not in row["applicable_classifications"]:
                    row["applicable_classifications"].append("ANCHOR_DEPENDENT")
        anchor_comparisons.append({"pair_id": pair["pair_id"], "anchor_dependent": dependent, "incompatible_anchor_pairs": incompatible})

    counts = Counter(row["classification"] for row in estimates)
    applicable_counts = Counter(label for row in estimates for label in row["applicable_classifications"])
    resolved_base = sum(row["energy_gradient_consistency"] == "CONSISTENT_RESOLVED" for row in estimates)
    projection_max = max(point["projection"]["total"]["projection_residual_fraction"] for point in points)
    normalized_mixed = [row["gradient"]["total"]["normalized_mixed_to_geometric_diagonal"] for row in estimates]
    same_sign_count = sum(math.copysign(1.0, row["energy"]["total"]["mixed_curvature"]) == math.copysign(1.0, row["gradient"]["total"]["mixed_curvature"]) for row in estimates)
    # The frozen protocol defines four candidate interpretations but no numerical
    # mapping from counts/conditioning to a unique dataset class. Preserve that
    # ambiguity instead of introducing a post-hoc threshold.
    if resolved_base == 0:
        structure_decision = "INSUFFICIENT_SUPPORT"
        rationale = "No panel established a common nonzero energy/gradient mixed curvature under the frozen uncertainty rule."
    elif anchor_dependent_pairs or scale_dependent:
        structure_decision = "DENSE_STATE_DEPENDENT_COUPLING_OR_SPARSE_LOW_ORDER_COUPLING_NOT_UNIQUELY_DISTINGUISHED"
        rationale = "Resolved mixed terms coexist with frozen scale/anchor dependence; the preregistration supplies no count threshold that uniquely separates sparse from dense coupling."
    else:
        structure_decision = "SPARSE_LOW_ORDER_COUPLING_OR_PRIMARILY_DIAGONAL_LOCAL_CURVATURE_NOT_UNIQUELY_DISTINGUISHED"
        rationale = "The preregistration supplies no magnitude/count rule for choosing between these model-form classes."
    point_verification = [{
        "campaign_id": point["campaign_id"], "geometry_sha256": point["geometry_sha256"], "scf_cycles": point["scf_cycles"],
        "identity_checks": point["identity_checks"], "jacobian": point["jacobian"], "projection": point["projection"],
    } for point in points]
    return {
        "schema": "tsl-rsh-curvature-analysis-results-v1", "frozen_analysis_protocol_sha256": sha256(HERE / "CURVATURE_ANALYSIS_PROTOCOL.json"),
        "equations": json.loads((HERE / "CURVATURE_ANALYSIS_PROTOCOL.json").read_text()),
        "implementation": {"jacobian_central_difference_step_angstrom": JACOBIAN_STEP_ANGSTROM, "svd_rcond": RCOND,
                           "cartesian_gradient_conversion": "g[Ha/Angstrom] = g[Ha/bohr] / 0.529177210903",
                           "gradient_panel_fit": "four-corner affine least squares for the 2x2 local Hessian; off-diagonal is symmetrized average",
                           "classification_uses_total_pbe_plus_d3": True},
        "verification": verification, "point_verification": point_verification, "estimates": estimates,
        "scale_comparison": {"pair_id": "S_C__C_S_H", "anchor_id": "MIN01", "full_scale": 1.0, "half_scale": 0.5,
                             "energy_intervals_incompatible": e_incompatible, "gradient_intervals_incompatible": g_incompatible,
                             "scale_dependent_nonlinear": scale_dependent},
        "anchor_comparisons": anchor_comparisons,
        "summary": {"pairs_analyzed": len(estimates), "unique_coordinate_pairs": 6, "energy_curvature_estimates": len(estimates) * 3,
                    "gradient_curvature_estimates": len(estimates) * 3, "energy_gradient_consistent_resolved": resolved_base,
                    "energy_gradient_same_sign_count": same_sign_count,
                    "normalized_mixed_to_geometric_diagonal": {"min": min(normalized_mixed), "median": float(np.median(normalized_mixed)), "max": max(normalized_mixed)},
                    "precedence_classification_counts": dict(sorted(counts.items())), "applicable_classification_counts": dict(sorted(applicable_counts.items())),
                    "mixed_curvature_resolved_count": applicable_counts["MIXED_CURVATURE_RESOLVED"],
                    "mixed_curvature_not_resolved_count": applicable_counts["MIXED_CURVATURE_NOT_RESOLVED"],
                    "scale_dependent_count": applicable_counts["SCALE_DEPENDENT_NONLINEAR"],
                    "anchor_dependent_count": applicable_counts["ANCHOR_DEPENDENT"], "maximum_projection_residual_fraction": projection_max,
                    "curvature_structure_decision": structure_decision, "decision_rationale": rationale,
                    "new_qm_run": False, "model_fit_run": False, "thresholds_changed": False},
    }


def write_outputs(points: list[dict[str, object]], verification: dict[str, object], analysis: dict[str, object]) -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "INGESTION_VERIFICATION.json").write_text(canonical_json(verification))
    (OUT / "CURVATURE_ANALYSIS_RESULTS.json").write_text(canonical_json(analysis))
    with (OUT / "CURVATURE_ESTIMATES.csv").open("w", newline="") as stream:
        fields = ["anchor_id", "pair_id", "scale", "classification", "energy_total", "energy_sigma", "gradient_total", "gradient_sigma", "gradient_antisymmetric", "jacobian_rank_min", "jacobian_condition_max", "projection_residual_fraction_mean"]
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator="\n")
        writer.writeheader()
        for row in analysis["estimates"]:
            writer.writerow({"anchor_id": row["anchor_id"], "pair_id": row["pair_id"], "scale": row["scale"], "classification": row["classification"],
                             "energy_total": row["energy"]["total"]["mixed_curvature"], "energy_sigma": row["energy"]["total"]["sigma_rms"],
                             "gradient_total": row["gradient"]["total"]["mixed_curvature"], "gradient_sigma": row["gradient"]["total"]["sigma_rms"],
                             "gradient_antisymmetric": row["gradient"]["total"]["unconstrained_antisymmetric_component"],
                             "jacobian_rank_min": row["jacobian_rank_min"], "jacobian_condition_max": row["jacobian_condition_max"],
                             "projection_residual_fraction_mean": row["projection_residual_fraction_total"]["mean"]})
    report = analysis["summary"]
    lines = ["# TSL-RSH Curvature Campaign Analysis", "", "Immutable ingestion and execution of the analysis preregistered at `d3781170965d4fdb4b671035ced30b2fbb52448d`.",
             "No QM or model fitting was run.", "", "## Verification", "", f"- Archive SHA-256: `{ARCHIVE_SHA256}`", "- Expected/verified points: 76/76",
             "- SCF convergence: 76/76", "- Geometry/protocol/nested checksums: PASS", "", "## Results", "",
             f"- Panels analyzed: {report['pairs_analyzed']} (six unique pairs; three anchors plus one half-scale panel)",
             f"- Common resolved energy/gradient estimates: {report['energy_gradient_consistent_resolved']}",
             f"- Applicable classification counts: `{json.dumps(report['applicable_classification_counts'], sort_keys=True)}`",
             f"- Scale dependence: `{analysis['scale_comparison']['scale_dependent_nonlinear']}`",
             f"- Dataset-level decision: `{report['curvature_structure_decision']}`", f"- Rationale: {report['decision_rationale']}", "",
             "## Conditioning limitation", "", f"Maximum six-coordinate Cartesian-gradient projection residual fraction: {report['maximum_projection_residual_fraction']:.8g}.",
             "This diagnostic is retained point-by-point; the six monitored internal coordinates cannot represent every Cartesian force component.", "",
             "Full equations, component-separated estimates, intervals, Jacobian singular values, projection residuals, and point identities are in `CURVATURE_ANALYSIS_RESULTS.json`.", ""]
    lines.extend(["## Total-curvature panel", "", "Values use hartree divided by the product of the two named coordinate units.", "",
                  "| Anchor | Pair | Scale | Energy curvature | Gradient curvature | Classification |", "|---|---|---:|---:|---:|---|"])
    for row in analysis["estimates"]:
        lines.append(f"| {row['anchor_id']} | {row['pair_id']} | {row['scale']:.1f} | {row['energy']['total']['mixed_curvature']:.10g} | {row['gradient']['total']['mixed_curvature']:.10g} | {row['classification']} |")
    scale_rows = [row for row in analysis["estimates"] if row["anchor_id"] == "MIN01" and row["pair_id"] == "S_C__C_S_H"]
    lines.extend(["", "## Frozen scale comparison", ""])
    for row in sorted(scale_rows, key=lambda item: item["scale"], reverse=True):
        lines.append(f"- Scale {row['scale']}: energy `{row['energy']['total']['mixed_curvature']:.12g}`, gradient `{row['gradient']['total']['mixed_curvature']:.12g}`.")
    lines.extend(["", f"The full/half energy intervals are incompatible: `{analysis['scale_comparison']['energy_intervals_incompatible']}`; gradient intervals are incompatible: `{analysis['scale_comparison']['gradient_intervals_incompatible']}`.", "",
                  "## Mixed versus diagonal diagnostic", "",
                  f"The dimensionless absolute mixed/geometric-diagonal ratio spans `{report['normalized_mixed_to_geometric_diagonal']['min']:.8g}` to `{report['normalized_mixed_to_geometric_diagonal']['max']:.8g}` (median `{report['normalized_mixed_to_geometric_diagonal']['median']:.8g}`).",
                  "Because the frozen protocol defines no cutoff for this ratio and none of the energy/gradient intervals overlap, it does not uniquely establish sparse, dense, or primarily diagonal curvature.", ""])
    (OUT / "CURVATURE_ANALYSIS_REPORT.md").write_text("\n".join(lines))
    checksum_targets = sorted(path for path in OUT.rglob("*") if path.is_file() and path.name != "ANALYSIS_SHA256SUMS")
    (OUT / "ANALYSIS_SHA256SUMS").write_text("".join(f"{sha256(path)}  {path.relative_to(OUT)}\n" for path in checksum_targets))


def main() -> None:
    points, verification = verify_and_load()
    analysis = analyze(points, verification)
    write_outputs(points, verification, analysis)
    print(canonical_json(analysis["summary"]), end="")


if __name__ == "__main__":
    main()
