#!/usr/bin/env python3
"""Regression tests for torsion-axis paths, receipts, and status isolation."""
from __future__ import annotations

import json
import tempfile
from pathlib import Path

from torsion_axis_identity import (
    TorsionAxis,
    completion_receipt,
    persisted_status,
)


def state(axis: TorsionAxis, *, queued: bool = False) -> dict:
    return {
        "torsion": axis.value,
        "round": 3,
        "cells": {"0": {}},
        "queue": [{}] if queued else [],
        "completed_task_ids": ["candidate"],
        "failed_task_ids": [],
    }


checks = 0
for axis in TorsionAxis:
    assert axis.result_directory(Path("results")) == Path("results") / axis.value
    checks += 1
    assert axis.completion_status == f"{axis.value}_COMPLETE_PERSISTED"
    checks += 1
    receipt = completion_receipt(
        axis,
        state_sha256="state",
        state_checksums_sha256="manifest")
    assert receipt["torsion"] == axis.value
    assert receipt["status"] == f"{axis.value}_COMPLETE_PERSISTED"
    checks += 1

with tempfile.TemporaryDirectory() as temporary:
    root = Path(temporary)
    for axis in TorsionAxis:
        directory = axis.result_directory(root)
        directory.mkdir()
        (directory / "WAVEFRONT_STATE.json").write_text(
            json.dumps(state(axis), sort_keys=True) + "\n")

    for axis in TorsionAxis:
        result = persisted_status(root, axis)
        assert result["status"] == "COMPLETE"
        assert result["torsion"] == axis.value
        assert Path(result["state_path"]).parent.name == axis.value
        checks += 1

# A status query must not fall through to another torsion directory.
for requested in TorsionAxis:
    for present in TorsionAxis:
        if requested == present:
            continue
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = present.result_directory(root)
            directory.mkdir()
            (directory / "WAVEFRONT_STATE.json").write_text(
                json.dumps(state(present), sort_keys=True) + "\n")
            result = persisted_status(root, requested)
            assert result == {"status": "NOT_STARTED", "torsion": requested.value}
            checks += 1

# Even in the selected directory, a mismatched state identity fails closed.
for requested in TorsionAxis:
    wrong = next(axis for axis in TorsionAxis if axis != requested)
    with tempfile.TemporaryDirectory() as temporary:
        root = Path(temporary)
        directory = requested.result_directory(root)
        directory.mkdir()
        (directory / "WAVEFRONT_STATE.json").write_text(
            json.dumps(state(wrong), sort_keys=True) + "\n")
        try:
            persisted_status(root, requested)
        except RuntimeError as error:
            assert "identity mismatch" in str(error)
        else:
            raise AssertionError("cross-axis state identity was accepted")
        checks += 1

try:
    TorsionAxis.parse("OMEGA")
except ValueError:
    checks += 1
else:
    raise AssertionError("unknown torsion axis was accepted")

print(f"TORSION_AXIS_IDENTITY_TESTS_PASS={checks}")
