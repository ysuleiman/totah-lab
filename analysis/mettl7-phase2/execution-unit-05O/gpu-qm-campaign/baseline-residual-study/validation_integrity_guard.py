"""Fail-closed provenance guards for atlas validation.

This module contains no scientific atlas implementation.  It controls which
geometry and label sources may participate in a validation fit and records the
dependency provenance of every test-side fitting artifact.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from enum import Enum
from typing import Any, Iterable, Mapping


class LeakageError(RuntimeError):
    pass


class Phase(str, Enum):
    FIT = "FIT"
    PREDICT = "PREDICT"
    SCORE = "SCORE"


class GeometryProtocol(str, Enum):
    INDUCTIVE = "INDUCTIVE"
    TRANSDUCTIVE = "TRANSDUCTIVE"


@dataclass(frozen=True)
class TaggedLabel:
    geometry_id: str
    field: str
    value: Any


@dataclass(frozen=True)
class FitScope:
    train_label_ids: frozenset[str]
    held_out_label_ids: frozenset[str]
    unlabeled_geometry_ids: frozenset[str]
    geometry_protocol: GeometryProtocol

    def __post_init__(self):
        if self.train_label_ids & self.held_out_label_ids:
            raise LeakageError("Train and held-out label IDs overlap")
        if self.train_label_ids & self.unlabeled_geometry_ids:
            raise LeakageError("A geometry cannot be both labeled-training and unlabeled in one scope")


@dataclass(frozen=True)
class ProvenanceArtifact:
    name: str
    payload: Any
    label_dependencies: frozenset[tuple[str, str]]
    geometry_dependencies: frozenset[str]

    def canonical_bytes(self) -> bytes:
        body = {
            "name": self.name,
            "payload": self.payload,
            "label_dependencies": sorted([list(item) for item in self.label_dependencies]),
            "geometry_dependencies": sorted(self.geometry_dependencies),
        }
        return json.dumps(body, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()

    def sha256(self) -> str:
        return hashlib.sha256(self.canonical_bytes()).hexdigest()


class LabelVault:
    def __init__(self, labels: Mapping[str, Mapping[str, Any]], scope: FitScope):
        self._labels = {geometry_id: dict(fields) for geometry_id, fields in labels.items()}
        self.scope = scope
        self.access_log: list[dict[str, str]] = []

    def read(self, geometry_id: str, field: str, phase: Phase) -> TaggedLabel:
        if phase in (Phase.FIT, Phase.PREDICT) and geometry_id not in self.scope.train_label_ids:
            raise LeakageError(f"{phase.value} access denied for non-training label {geometry_id}:{field}")
        if phase == Phase.PREDICT:
            raise LeakageError(f"Prediction must use frozen artifacts, not raw labels: {geometry_id}:{field}")
        if phase == Phase.SCORE and geometry_id not in self.scope.held_out_label_ids:
            raise LeakageError(f"Scoring access is restricted to held-out labels: {geometry_id}:{field}")
        if geometry_id not in self._labels or field not in self._labels[geometry_id]:
            raise LeakageError(f"Label is physically absent: {geometry_id}:{field}")
        self.access_log.append({"geometry_id": geometry_id, "field": field, "phase": phase.value})
        return TaggedLabel(geometry_id, field, self._labels[geometry_id][field])


class GeometryVault:
    def __init__(self, geometry_checksums: Mapping[str, str], scope: FitScope):
        self._checksums = dict(geometry_checksums)
        self.scope = scope

    def fit_checksum(self, geometry_id: str) -> str:
        allowed = set(self.scope.train_label_ids) | set(self.scope.unlabeled_geometry_ids)
        if self.scope.geometry_protocol == GeometryProtocol.TRANSDUCTIVE:
            allowed |= set(self.scope.held_out_label_ids)
        if geometry_id not in allowed:
            raise LeakageError(f"Geometry {geometry_id} is unavailable during {self.scope.geometry_protocol.value} fit")
        if geometry_id not in self._checksums:
            raise LeakageError(f"Geometry is physically absent: {geometry_id}")
        return self._checksums[geometry_id]

    def prediction_checksum(self, geometry_id: str) -> str:
        if geometry_id not in self.scope.held_out_label_ids:
            raise LeakageError(f"Prediction geometry must be held out: {geometry_id}")
        if geometry_id not in self._checksums:
            raise LeakageError(f"Prediction geometry is physically absent: {geometry_id}")
        return self._checksums[geometry_id]


def _numeric_digest(value: Any) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode()).hexdigest()


def fit_training_artifact(label_vault: LabelVault, geometry_vault: GeometryVault) -> ProvenanceArtifact:
    """Integrity sentinel, not a scientific model.

    It deliberately exercises every permitted training-label pathway and emits
    only deterministic digests.  Held-out labels cannot be accessed.
    """
    label_dependencies: set[tuple[str, str]] = set()
    geometry_dependencies: set[str] = set()
    energy_digests, gradient_digests = [], []
    for geometry_id in sorted(label_vault.scope.train_label_ids):
        energy = label_vault.read(geometry_id, "energy", Phase.FIT)
        gradient = label_vault.read(geometry_id, "gradient", Phase.FIT)
        label_dependencies.update(((energy.geometry_id, energy.field), (gradient.geometry_id, gradient.field)))
        energy_digests.append((geometry_id, _numeric_digest(energy.value)))
        gradient_digests.append((geometry_id, _numeric_digest(gradient.value)))
        geometry_vault.fit_checksum(geometry_id)
        geometry_dependencies.add(geometry_id)
    for geometry_id in sorted(label_vault.scope.unlabeled_geometry_ids):
        geometry_vault.fit_checksum(geometry_id)
        geometry_dependencies.add(geometry_id)
    payload = {
        "geometry_protocol": label_vault.scope.geometry_protocol.value,
        "train_label_ids": sorted(label_vault.scope.train_label_ids),
        "held_out_label_ids": sorted(label_vault.scope.held_out_label_ids),
        "energy_digests": energy_digests,
        "gradient_digests": gradient_digests,
    }
    artifact = ProvenanceArtifact("validation-integrity-sentinel-fit", payload, frozenset(label_dependencies), frozenset(geometry_dependencies))
    forbidden = {dependency for dependency in artifact.label_dependencies if dependency[0] in label_vault.scope.held_out_label_ids}
    if forbidden:
        raise LeakageError(f"Frozen artifact carries held-out label provenance: {sorted(forbidden)}")
    return artifact


def predict_sentinel(artifact: ProvenanceArtifact, geometry_vault: GeometryVault, query_id: str) -> str:
    checksum = geometry_vault.prediction_checksum(query_id)
    return hashlib.sha256(artifact.canonical_bytes() + query_id.encode() + checksum.encode()).hexdigest()


def read_many(vault: LabelVault, ids: Iterable[str], field: str) -> list[TaggedLabel]:
    return [vault.read(geometry_id, field, Phase.FIT) for geometry_id in ids]


def gradient_secant(vault: LabelVault, first_id: str, second_id: str) -> tuple[TaggedLabel, TaggedLabel]:
    return vault.read(first_id, "gradient", Phase.FIT), vault.read(second_id, "gradient", Phase.FIT)


def guarded_cache(name: str, label_sources: Iterable[TaggedLabel], geometry_ids: Iterable[str], scope: FitScope) -> ProvenanceArtifact:
    dependencies = frozenset((source.geometry_id, source.field) for source in label_sources)
    forbidden = [dependency for dependency in dependencies if dependency[0] in scope.held_out_label_ids]
    if forbidden:
        raise LeakageError(f"Cache {name} contains held-out label provenance: {forbidden}")
    return ProvenanceArtifact(name, {}, dependencies, frozenset(geometry_ids))
