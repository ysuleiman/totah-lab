# Eight-system canonical preparation

Status: **PASS** under the locked Stage 1 protocol.

All eight receptors and SAM-bound complexes are under `prepared/`. Every system contains chain A residues 1–244, the required WT or mutant identities, and exactly 27 fixed SAM heavy atoms. SAM is fully contained by the corresponding homologous 197-sphere superpocket. No receptor–SAM heavy-atom pair is below 2.0 Å.

Backbone RMSD from the corresponding WT receptor is 0.000 Å for every construct. Every atom outside the explicitly allowed mutation/repacking residues has maximum displacement 0.000 Å. SAM coordinates were copied from the validated Stage 0 WT placement without minimization or repair.

## Rotamer selection

METTL7A F43L, F199G, and F43L/F199G use the retained fixed-backbone Proteus constructions after validation.

METTL7B introduced phenylalanines were selected without DCMB or TSL information. All preregistered discrete states were filtered for zero changed-side-chain/environment pairs below 2.0 Å, then ordered by minimum steric score, maximum minimum distance, and deterministic torsion order.

- L43F: F43 chi1/chi2 `60/0`, L229 offset `0`.
- G199F: F199 chi1/chi2 `180/0`, L229 offset `0`.
- L43F/G199F: F43 `60/0`, F199 `180/0`, L229 offset `0`.

The L43F grid explicitly included L229 repacking offsets. The selected state required no L229 rotation; this is an outcome of the locked geometric rule, not an assumption that accommodation was irrelevant. The previously favored trans-Phe/L229-repacked structure remains historical evidence but is not the canonical Stage 2 receptor.

Exact inputs, selections, hashes, and outputs are recorded in `provenance.json`; all validation measurements are in `validation.csv`. `l43f_joint_enumeration.csv` preserves the complete single-L43F grid.

No docking, TSL placement, relaxation, or result-dependent receptor adjustment occurred in this stage.
