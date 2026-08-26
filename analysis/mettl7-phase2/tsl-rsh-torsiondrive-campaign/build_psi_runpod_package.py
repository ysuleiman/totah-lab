#!/usr/bin/env python3
"""Build a PSI-only RunPod execution package without changing science code."""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import tempfile
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCE = HERE / "TSL_RSH_TORSIONDRIVE_A100_CAMPAIGN"
PACKAGE_NAME = "TSL_RSH_TORSIONDRIVE_PSI_RUNPOD_CAMPAIGN"
OUTPUT = HERE / PACKAGE_NAME
ARCHIVE = HERE / f"{PACKAGE_NAME}.zip"
CANONICAL_PSI_WRAPPER = HERE / "run_multigpu_psi.py"
AXIS_IDENTITY = HERE / "torsion_axis_identity.py"
AXIS_IDENTITY_TEST = HERE / "test_torsion_axis_identity.py"
PSI_SCHEDULER_TEST = HERE / "test_multigpu_scheduler_psi.py"


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


with tempfile.TemporaryDirectory(dir=HERE) as temporary_name:
    temporary = Path(temporary_name) / PACKAGE_NAME
    shutil.copytree(SOURCE, temporary)
    for relative in ("run_multigpu_aws.py", "test_multigpu_scheduler.py", "AWS_RUNBOOK.md",
                     "PACKAGE_SHA256SUMS"):
        path = temporary / relative
        if path.exists():
            path.unlink()
    # The PSI wrapper is an authoritative source file.  Never derive it by
    # blind PHI->PSI substitutions: that transformation caused the historical
    # receipt/status defect documented by this campaign.
    shutil.copy2(CANONICAL_PSI_WRAPPER, temporary / "run_multigpu_psi.py")
    shutil.copy2(AXIS_IDENTITY, temporary / "torsion_axis_identity.py")
    shutil.copy2(AXIS_IDENTITY_TEST, temporary / "test_torsion_axis_identity.py")
    shutil.copy2(PSI_SCHEDULER_TEST, temporary / "test_multigpu_scheduler.py")
    seal = {
        "schema": "tsl-rsh-torsiondrive-runpod-psi-only-package-v1",
        "status": "SEALED_NOT_EXECUTED",
        "scientific_controller_sha256": digest(SOURCE / "run_torsiondrive_a100.py"),
        "scientific_controller_changed": False,
        "execution_torsions": ["PSI"],
        "phi_execution_authorized": False,
        "chi_execution_authorized": False,
        "psi_execution_authorized": True,
        "max_gpu_workers": 8,
        "recommended_gpu_workers": 2,
        "gpu_requirement": "NVIDIA A100",
        "persistent_mount": "/workspace/tsl-rsh/torsiondrive/results",
        "recommended_persistent_storage_gb": 20,
        "phi_measured_result_size_bytes": 22738570,
        "canonical_energy_decrease_threshold_hartree": 1e-5,
        "propagation_energy_upper_limit_hartree": 0.05,
        "round_barrier": True,
        "deterministic_batch_reduction": True,
        "qm_run": False,
    }
    (temporary / "PSI_PACKAGE_SEAL.json").write_text(
        json.dumps(seal, indent=2, sort_keys=True) + "\n")
    runbook = """# RunPod PSI-only execution gate

Do not launch until an exact A100 pod and persistent volume have been verified.
The only execution command is:

```bash
python run_multigpu_psi.py --run-psi --workers 2 \\
  --results-root /workspace/tsl-rsh/torsiondrive/results
```

There is no PHI or CHI execution entrypoint. Download and checksum the sealed
result archive before stopping and deleting paid RunPod resources.
"""
    (temporary / "RUNPOD_PSI_RUNBOOK.md").write_text(runbook)
    files = sorted(p for p in temporary.rglob("*") if p.is_file()
                   and p.name != "PACKAGE_SHA256SUMS" and "__pycache__" not in p.parts)
    (temporary / "PACKAGE_SHA256SUMS").write_text("".join(
        f"{digest(path)}  {path.relative_to(temporary)}\n" for path in files))
    if OUTPUT.exists():
        shutil.rmtree(OUTPUT)
    shutil.copytree(temporary, OUTPUT)

files = sorted(p for p in OUTPUT.rglob("*") if p.is_file() and "__pycache__" not in p.parts)
archive_tmp = ARCHIVE.with_suffix(".zip.tmp")
with zipfile.ZipFile(archive_tmp, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as target:
    for path in files:
        info = zipfile.ZipInfo(str(Path(PACKAGE_NAME) / path.relative_to(OUTPUT)),
                               (1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = (0o755 if path.suffix == ".py" else 0o644) << 16
        target.writestr(info, path.read_bytes())
os.replace(archive_tmp, ARCHIVE)
print(f"PSI_ZIP_PATH={ARCHIVE}")
print(f"PSI_ZIP_SHA256={digest(ARCHIVE)}")
