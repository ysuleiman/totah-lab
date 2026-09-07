"""Fail-closed lifecycle for validation success artifacts."""
import hashlib, json
from pathlib import Path

def invalidate_success_manifest(success_manifest: Path, failure_receipt: Path, errors):
    disposition = "ABSENT"
    superseded = None
    if success_manifest.exists():
        digest = hashlib.sha256(success_manifest.read_bytes()).hexdigest()
        superseded = success_manifest.with_name(success_manifest.name + f".superseded-{digest[:12]}")
        success_manifest.replace(superseded)
        disposition = "RENAMED_SUPERSEDED"
    payload = {
        "validation_status": "FAIL", "success_manifest_disposition": disposition,
        "superseded_manifest": str(superseded) if superseded else None,
        "errors": list(errors)
    }
    temporary = failure_receipt.with_suffix(failure_receipt.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, indent=2) + "\n")
    temporary.replace(failure_receipt)
    return payload
