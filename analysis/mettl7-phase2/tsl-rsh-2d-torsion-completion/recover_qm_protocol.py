#!/usr/bin/env python3
"""Recover, without inference, the protocol used by the sealed torsion evidence."""
import hashlib, json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[2]
SOURCE = REPO / "analysis/mettl7-phase2/tsl-rsh-torsiondrive-campaign/TORSION_PUBLICATION_REPRODUCIBILITY_MANIFEST.json"
QUAL = REPO / "analysis/mettl7-phase2/tsl-rsh-min01-derivative-qualification/QUALIFICATION_MANIFEST.json"
src = json.loads(SOURCE.read_text()); qual = json.loads(QUAL.read_text())
protocol = dict(src["scientific_protocol"])
protocol.update({
    "schema": "tsl-rsh-final-qm-protocol-v1",
    "electronic_state": "neutral closed-shell singlet; 202 electrons",
    "environment_model": "gas phase; no implicit or explicit solvent",
    "constraints": {"PHI_zero_based": [25,9,8,7], "PSI_zero_based": [9,8,7,1],
                    "two_dihedrals_fixed_simultaneously": True,
                    "all_other_coordinates": "unconstrained"},
    "energy_extraction": "total_hartree = converged PBE electronic energy + explicit simple-dftd3 D3(BJ) energy",
    "convergence_definition": "GPU SCF converged and geomeTRIC constrained optimization converged under frozen criteria",
    "restart_checkpoint": "atomic per-step energy/gradient/geometry evidence; checksum-verified candidate and wavefront state; completed candidates never rerun",
    "optimization_thresholds": {"energy":1e-5,"gmax":0.004,"grms":0.001,"dmax":0.005,"drms":0.002,"max_iterations":300},
    "provenance": {
        "torsion_manifest": str(SOURCE.relative_to(REPO)),
        "torsion_manifest_sha256": hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
        "derivative_qualification_manifest": str(QUAL.relative_to(REPO)),
        "derivative_qualification_manifest_sha256": hashlib.sha256(QUAL.read_bytes()).hexdigest(),
        "sealed_CHI_archive_sha256": src["torsions"]["CHI"]["archive_sha256"],
        "sealed_PHI_archive_sha256": src["torsions"]["PHI"]["archive_sha256"],
        "sealed_PSI_archive_sha256": src["torsions"]["PSI"]["archive_sha256"],
    }
})
(ROOT/"FINAL_QM_PROTOCOL.json").write_text(json.dumps(protocol,indent=2,sort_keys=True)+"\n")
