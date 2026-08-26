#!/usr/bin/env python3
"""Authoritative torsion-axis identity for execution metadata and status paths."""
from __future__ import annotations

import json
from enum import Enum
from pathlib import Path


class TorsionAxis(str, Enum):
    CHI = "CHI"
    PHI = "PHI"
    PSI = "PSI"

    @classmethod
    def parse(cls, value: "TorsionAxis | str") -> "TorsionAxis":
        if isinstance(value, cls):
            return value
        try:
            return cls(value)
        except ValueError as error:
            raise ValueError(f"unsupported torsion axis: {value!r}") from error

    @property
    def completion_status(self) -> str:
        return f"{self.value}_COMPLETE_PERSISTED"

    def result_directory(self, results_root: Path) -> Path:
        return Path(results_root) / self.value


def completion_receipt(
        axis: TorsionAxis | str,
        *,
        state_sha256: str,
        state_checksums_sha256: str,
        active_workers: int = 0,
        queue_size: int = 0) -> dict:
    """Build completion metadata entirely from one authoritative axis."""
    identity = TorsionAxis.parse(axis)
    return {
        "status": identity.completion_status,
        "torsion": identity.value,
        "active_workers": active_workers,
        "queue_size": queue_size,
        "state_sha256": state_sha256,
        "state_checksums_sha256": state_checksums_sha256,
    }


def persisted_status(results_root: Path, axis: TorsionAxis | str) -> dict:
    """Read only the selected axis state and reject cross-axis identity."""
    identity = TorsionAxis.parse(axis)
    path = identity.result_directory(results_root) / "WAVEFRONT_STATE.json"
    if not path.is_file():
        return {"status": "NOT_STARTED", "torsion": identity.value}
    state = json.loads(path.read_text())
    if state.get("torsion") != identity.value:
        raise RuntimeError(
            f"torsion state identity mismatch at {path}: "
            f"expected {identity.value}, found {state.get('torsion')!r}")
    queue = state.get("queue")
    if not isinstance(queue, list):
        raise RuntimeError(f"invalid queue in torsion state: {path}")
    return {
        "status": "COMPLETE" if not queue else "RUNNING_OR_INTERRUPTED",
        "torsion": identity.value,
        "state_path": str(path),
        "round": state["round"],
        "cells": len(state["cells"]),
        "queue": len(queue),
        "completed": len(state["completed_task_ids"]),
        "failed": len(state["failed_task_ids"]),
    }
