# CHI controller recovery audit

No QM was executed. The preserved backup was read-only.

## Frozen pre-fix identities

- production runner SHA-256: `688935db3bc4bbba5fb70ff74d61fcca7ad445d8c64fa9ba84dc4e5ce47b3de0`
- `WAVEFRONT_STATE.json` SHA-256: `ce0a9649feb723def9e07cd12cd4cafd00b02bc947d9bef7f01c0c7c6a2dacbb`
- `STATE_SHA256SUMS` SHA-256: `b167942b57e16cc3da2e3573b05636b0a7dc131f69d58e0747c97f32a6e1e274`
- candidate manifests present in local backup: `0`

The state checksum listed in `STATE_SHA256SUMS` matches. The state records 24
populated CHI cells, round 13, 225 completed task IDs, zero failures and 20
queued tasks. The user-reported earlier count of 221 is not the count in the
downloaded state artifact.

## Confirmed defect

The deployed transition reactivated a cell after every strict numerical energy
decrease. TorsionDrive 1.2.0 (`lpwgroup/torsiondrive` commit `5ffa9ef`) defaults
`energy_decrease_thresh` to `1e-5` Hartree and reactivates only when
`energy < old_energy - threshold`. Its command-line documentation reports the
same `1e-5` Hartree default.

The corrected controller retains any strictly lower result as the authoritative
best cell but propagates only when the decrease is strictly greater than
`1e-5` Hartree. This separation retains paid numerical evidence while restoring
canonical activation and termination behavior.

## Offline replay result

The later lightweight Drive export supplied 225 candidate records; all 225
record checksums verify against their original candidate manifests and the
exported state SHA matches the preserved local state. The canonical replay:

- converges at round 10 with all 24 cells populated;
- uses 67 of the completed candidates;
- classifies 158 completed candidates as unnecessary under canonical control;
- reconstructs 97 historical energy-decrease reactivations, 89 of which were
  at or below the canonical `1e-5` Hartree threshold;
- requires no missing candidate task and therefore no new CHI QM.

The backup state was captured mid-round: nine cell energies are stale relative
to lower paid candidate results not yet folded into the state, with maximum
staleness `4.905666628474137e-7` Hartree. The recovery result reconstructs the
authoritative best energy for every cell from all paid candidate records. The
original state and records remain unchanged.

`offline_replay_chi.py` imports or executes no PySCF, GPU4PySCF, geomeTRIC, D3,
Sander, or other QM calculation.

## Canonical source evidence

- `canonical-source/dihedral_scanner_5ffa9ef.py` SHA-256:
  `3766279a34a82635ea32358ef4c711199e1631768007d3fa3b9841769c96ddca`
- `canonical-source/td_api_5ffa9ef.py` SHA-256:
  `66dc646f273afdc21eb944176da427bc90a92203b6a1b5503f497fb66de5bb79`
