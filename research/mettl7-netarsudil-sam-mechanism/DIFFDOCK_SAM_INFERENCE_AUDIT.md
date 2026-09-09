# DiffDock SAM Inference Audit

`DIFFDOCK_SAM_USED_BY_INFERENCE = INDETERMINATE`

No docking was rerun and no netarsudil pose was interpreted.

## Evidence

### METTL7A

- Archive: `experiments/netarsudil-diffdock/7a.zip`, SHA-256 `bd16970a5afd9f8ad9a963004b346269a71ce90982cf39c46f72a6a2f752cbcd`.
- Exact receptor packaged with results: archive member `target_protein.pdb`, SHA-256 `a2f2e0a27681ae339cfc31683738d1d97b5e650df774d0bd3d9e0bfe8c65f687`.
- Source receptor contains 2,001 protein `ATOM` records and 27 `HETATM` records named `SAM`.
- Archive contents are limited to 20 ranked ligand SDFs, `target_protein.pdb`, `pose_confidence.txt`, and 20 `rank*_reverseprocess.pdb` files.
- Reverse-process PDBs contain only `UNL` netarsudil trajectory coordinates; they contain no receptor or SAM representation.
- No preprocessed receptor graph/cache, configuration, command line, log, DiffDock code/model version, weights, seed, or environment record is present.
- Consequently, there is no retained artifact demonstrating that SAM became graph nodes/features or contributed coordinates to inference/scoring.

**Classification:** `DIFFDOCK_SAM_USED_BY_INFERENCE = INDETERMINATE`  
**Run class:** `INDETERMINATE` — it must not be called `SAM_PRESENT_GLOBAL_DOCKING`.

### METTL7B

- Archive: `experiments/netarsudil-diffdock/7b+sam.zip`, SHA-256 `1882d190a3c47d42ad71cce3d95afaa1b5a4c5fbcd27e80286c8cc30df214f05`.
- Exact receptor packaged with results: archive member `target_protein.pdb`, SHA-256 `937452b35fab5c3567d5eb1c4a24b9f0f08ceea5b0aa79f28d73dac3a1a95fb7`.
- Source receptor contains 1,949 protein `ATOM` records and 27 `HETATM` records named `SAM`.
- Archive contents are limited to 20 ranked ligand SDFs, `target_protein.pdb`, `pose_confidence.txt`, and 20 `rank*_reverseprocess.pdb` files.
- Reverse-process PDBs contain only `UNL` netarsudil trajectory coordinates; they contain no receptor or SAM representation.
- No preprocessed receptor graph/cache, configuration, command line, log, DiffDock code/model version, weights, seed, or environment record is present.
- Consequently, there is no retained artifact demonstrating that SAM became graph nodes/features or contributed coordinates to inference/scoring.

**Classification:** `DIFFDOCK_SAM_USED_BY_INFERENCE = INDETERMINATE`  
**Run class:** `INDETERMINATE` — it must not be called `SAM_PRESENT_GLOBAL_DOCKING`.

## Code-path determination

No exact DiffDock checkout or runtime environment used to generate these two archives is retained in the project. Therefore a statement that the used code path retained or stripped heteroatoms cannot be proven from local code. General behavior of another DiffDock version is not substituted for evidence about these runs.

## Final disposition

The presence of SAM in `target_protein.pdb` proves only that SAM was uploaded. It does not prove that SAM was represented during inference. Both ensembles remain conservative global-site hypotheses with post-hoc SAM geometry and are excluded from cofactor-aware claims unless the generating service supplies its preprocessed receptor graph or exact executable/version/configuration showing SAM retention.

