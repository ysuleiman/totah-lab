# Release-quality methyl/ethyl/isopropyl validation

The validator recomputed pose/ligand/receptor hashes and checked seed, explicit-SAM receptor, ligand state, pose count, paralog-specific box and exhaustiveness 32 for all 18 runs. Release status is **TRUE**. The failed runs are: none. All receipt-derived input and protocol checks pass. Chemical verdicts remain limited to metrics with frozen semantics. The Athena threshold provenance is unique and frozen.

K151 is now decomposed into NZ, side-chain-heavy and backbone distances. `sidechain_directed_oxygen` uses Athena's frozen hydrogen-bond geometry (D–A ≤3.5 Å, H–A ≤2.5 Å, D–H–A ≥120°) and records the ligand feature class and raw geometry.

Ethyl-versus-parent side-chain-directed oxygen deltas by seed are: 1=0.000000, 7=0.000000, 42=0.000000. Raw poses and exact-signature pose families are separate. No exact recurrent cross-analog family correspondence exists, and no mapping threshold was frozen, so family-matched alkyl ordering is unevaluated. The validator does not manufacture a family-mapping threshold or issue that unsupported comparison.

The ethyl B state is available. Parent equivalence is implemented as a multidimensional comparison, but its verdict is `unevaluated`: exact family correspondence is absent and no prospective equivalence margins exist for residue recurrence, B207 H-bonding, burial or family recurrence. No post-hoc margin was invented.

`VALIDATOR_RELEASE_QUALITY = true`

`K151_SIDECHAIN_GEOMETRY_IMPLEMENTED = true`

`B_PARENT_EQUIVALENCE_IMPLEMENTED = true`

`MATCHED_SEED_COMPARISON_IMPLEMENTED = true`

`POSE_FAMILY_AWARE_AGGREGATION_IMPLEMENTED = true`

`INPUT_RECEIPT_VERIFICATION_IMPLEMENTED = true`

`ETHYL_ANTI_A_SIGNAL_REPRODUCED = False`

`ETHYL_B_STATE_EQUIVALENT_TO_PARENT = unevaluated`

`METHYL_ETHYL_ISOPROPYL_ORDERING_SUPPORTED = false`

The superseded positive ethyl result remains invalid. No chemical optimization or new docking was performed.
