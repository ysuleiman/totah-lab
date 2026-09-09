# METTL7 Netarsudil–SAM Mechanism — Preflight

**Branch:** `METTL7_NETARSUDIL_SAM_MECHANISM`  
**Status:** `PREFLIGHT_COMPLETE_MAIN_MATCHED_STUDY_NOT_LAUNCHED`  
**Date:** 2026-08-29

Boundaries retained:

- `RNA_INTERFACE_COMPUTATIONAL_ROUTE = CLOSED_PENDING_EXPERIMENTAL_CONSTRAINTS`
- `TSL_RSH_FORCE_FIELD = IN_PROGRESS_BY_KIMI`
- No RNA docking, MD, TSL parameter work, or netarsudil catalytic-activation assumption is part of this branch.

## PAPER_PROTOCOL_RECONSTRUCTION

| Item | Explicitly reported | Reconstruction / unresolved |
|---|---|---|
| Ligand | Netarsudil from HY-L022 FDA-Approved Drug Library (~2.3K compounds) | Library chemical form, salt, protonation and LigPrep output were not supplied |
| Receptor | AlphaFold-predicted human METTL7B, UniProt Q6UX53, downloaded as PDB | AlphaFold entry is an apo prediction; no experimental receptor structure was used |
| Preparation | “prepare” library and perform Virtual Screening Workflow | Protein Preparation Wizard settings, water treatment, protonation, minimization and constraints absent |
| Software | Schrödinger Virtual Screening Workflow followed by Glide | Schrödinger/Glide version and precision stages absent |
| Search region | Not reported | Grid center, dimensions and whether whole-protein/site-directed are indeterminate |
| Sampling/ranking | “geometric and energy matching”; final Glide docking | number of poses, ligand-state enumeration, score terms and pose-retention rules absent |
| Reported result | docking score −11.494; pose assigned contacts to D98, L145 and D200 | coordinate file/source data not supplied; figure distances are approximately 3.0, 1.7 and 4.2 Å |
| SAM | Not mentioned or shown as part of receptor preparation/docking | Because the specified input is an AlphaFold PDB and no SAM addition is described, `PAPER_RECEPTOR_SAM_STATE = APPARENTLY_ABSENT`; this is a high-confidence reconstruction, not an explicit author statement |

Source: Chen et al. 2026, main text near Figure 8 and Figures S8A–B; Supplement p. 24, “Molecular Docking.”

The paper protocol is underdetermined. A faithful score/pose reproduction cannot be claimed without the Glide version, prepared ligand state, receptor-preparation output and grid. Any attempt will be labeled reconstruction.

## PAPER_DOCKING_REPRODUCTION

`PAPER_DOCKING_REPRODUCED = INDETERMINATE`

No reproduction run is launched at preflight. Exact reproduction is presently blocked by missing proprietary configuration and pose coordinates. A separately labeled **paper-like reconstruction** may use the AlphaFold Q6UX53 apo receptor and a declared Schrödinger-compatible state if the licensed runtime and missing settings become available. It will not be tuned until D98/L145/D200 appear.

## SAM_STARTING_STATE_VALIDATION

Canonical matched complexes:

| System | Protein atoms | SAM atoms | SHA-256 | Status |
|---|---:|---:|---|---|
| WT METTL7A + SAM | 2,001 | 27 | `a2f2e0a27681ae339cfc31683738d1d97b5e650df774d0bd3d9e0bfe8c65f687` | canonical retained input |
| WT METTL7B + SAM | 1,949 | 27 | `937452b35fab5c3567d5eb1c4a24b9f0f08ceea5b0aa79f28d73dac3a1a95fb7` | canonical retained input |

The uploaded DiffDock archives contain byte-identical copies of these PDBs. A dedicated archive/code-path audit found no preprocessed receptor graph, configuration, command, log, exact DiffDock version, weights, seeds, or environment record. Reverse-process files contain ligand coordinates only. Therefore `DIFFDOCK_SAM_USED_BY_INFERENCE = INDETERMINATE` for both paralogs. File inclusion is not evidence that DiffDock encoded SAM, and the ensembles remain conservative global-site hypotheses with post-hoc SAM geometry, not SAM-present docking. See `DIFFDOCK_SAM_INFERENCE_AUDIT.md`.

Apo controls will be generated only by deleting the 27 SAM records from the corresponding canonical complexes, preserving every protein coordinate and atom order. A/B preparation will otherwise be identical.

## NETARSUDIL_CHEMICAL_STATE

Authoritative identity: PubChem CID 66599893, netarsudil free base, formula C28H27N3O3, one defined stereocenter. PubChem stereochemical SMILES:

`CC1=CC(=C(C=C1)C(=O)OCC2=CC=C(C=C2)[C@@H](CN)C(=O)NC3=CC4=C(C=C3)C=NC=C4)C`

The uploaded pose SDFs contain the correct 34-heavy-atom connectivity and the defined stereocenter. DiffDock outputs omit explicit hydrogens.

Chemical states must remain separate:

1. `NET_NEUTRAL_FREE_BASE`: neutral primary amine and neutral isoquinoline nitrogen; exact state represented by the supplied PubChem free-base record and current DiffDock input.
2. `NET_MONOCATION_PRIMARY_AMMONIUM`: +1 primary ammonium; chemically plausible/dominant near physiological pH, to be prepared explicitly for charge-aware docking.
3. `NET_DICATION`: protonated primary amine plus isoquinolinium; retained as an acidic-pH sensitivity state only, not pooled with the primary states.

No tautomeric change to the amide/ester is justified. Stereochemistry is fixed; no opposite enantiomer will be introduced. State-specific input files, formal charges and hashes are required before docking.

## PROPOSED_MATCHED_A_B_PROTOCOL

### Stage A — global site discovery

- Retain the uploaded 20-pose DiffDock ensembles for A and B as an initial, matched, global search for the neutral free base.
- Do not use DiffDock confidence as affinity and do not call these SAM-present calculations.
- Before interpretation, require engine/model/configuration/seeds from the generating environment. If unavailable, mark search replicates unknown and family reproducibility untested.
- A new DiffDock campaign is not launched unless its exact version, weights and seeds can be pinned for all four systems.

### Stage B — cofactor-aware matched docking

- Engine: AutoDock Vina version pinned at execution; Meeko/Open Babel preparation versions recorded.
- Systems: `7A+SAM`, `7B+SAM`, `7A apo`, `7B apo`.
- SAM is rigid and included in the receptor PDBQT for primary systems; a preflight parser test must prove all SAM atoms and charges survive.
- Netarsudil chemical states are docked and analyzed separately.
- One preregistered global box enclosing the homologously aligned A/B proteins, with identical dimensions and settings; no result-dependent box changes.
- Three fixed seeds per system/state; exhaustiveness 32; 20 retained modes per run. Scores remain engine outputs only.
- Global families may nominate one focused refinement box. Focused work, if triggered, is a separate declared test using the same homologous box in A/B and apo/SAM states.
- Uploaded DiffDock and new Vina populations are independent evidence channels and are not merged into a master score.

### Stage C — paper-site comparison

Without changing search or acceptance rules, measure whether admissible families contact D98/L145/D200 and whether any orientation resembles the published figure. Absence is a result, not a reason to retune.

## FROZEN_POSE_ADMISSIBILITY_GATES

These gates are frozen before comparative pose inspection. Every dimension is reported separately.

1. **Identity/integrity:** exact CID 66599893 heavy-atom graph, fixed stereocenter, declared formal charge; no broken or new covalent bonds.
2. **Protein clash:** zero protein–ligand heavy-atom pairs below 1.8 Å. Pairs 1.8–2.2 Å are reported separately as close contacts.
3. **SAM integrity:** SAM atom count, connectivity and formal charge preserved; rigid-docking SAM coordinate RMSD ≤0.01 Å from its input.
4. **SAM overlap:** zero netarsudil–SAM heavy-atom pairs below 2.0 Å. Minimum distance ≥2.5 Å is compatible; 2.0–2.5 Å is `INDETERMINATE_REQUIRES_LOCAL_RELAXATION`, not automatically compatible.
5. **Ligand strain:** state-specific MMFF94s energy after isolated restrained minimization no more than 15 kcal/mol above that state’s lowest prepared conformer. If the state is not parameterizable, strain is `NOT_EVALUATED`, never imputed.
6. **Burial:** ≥20% reduction in ligand SASA relative to isolated ligand for a binding-site family; more exposed poses remain recorded as surface encounters.
7. **Family reproducibility:** a family requires ≥3 poses from ≥2 independent seeds within 2.5 Å symmetry-aware heavy-atom RMSD. The current one-batch DiffDock files cannot pass the independent-seed clause unless their generation provenance establishes independent searches.
8. **Chemical plausibility:** no buried uncompensated formal charge without a polar/ionic partner within 4.0 Å; hydrogen-bond distance 2.4–3.5 Å and donor–H–acceptor angle ≥120° when hydrogens are available.
9. **SAM network:** canonical SAM–protein contact distances are measured before/after; rigid receptor runs can establish coexistence/overlap only, not disruption or rearrangement. Claims of disruption require a separately declared local-relaxation test.
10. **Selection:** no single pose is nominated by score or visual appeal. Only admissible reproducible families proceed to residue/site interpretation.

Predeclared site relationship using heavy-atom geometry and pocket membership:

- `OVERLAPPING`: direct steric overlap or shared core pocket volume that precludes simultaneous occupancy.
- `PARTIALLY_OVERLAPPING`: no severe overlap, but ≥25% ligand-volume intersection with the SAM pocket or extensive shared pocket-lining contacts.
- `ADJACENT`: closest heavy-atom distance 2.5–6.0 Å with connected pocket volumes.
- `DISTINCT`: closest distance >6.0 Å and nonoverlapping pocket components.
- `INDETERMINATE`: insufficient admissible/reproducible ensemble or ambiguous pocket segmentation.

## COMPUTE_RUNTIME_ESTIMATE

- Input/QC, state preparation and parser validation: 1–2 hours wall time.
- Vina global matrix for two primary states: 4 systems × 2 states × 3 seeds = 24 jobs; approximately 2–8 CPU-hours total depending on the global box, parallelizable to roughly 1–3 hours wall time on 8 workers.
- Optional dication sensitivity: 12 additional jobs, approximately 1–4 CPU-hours.
- Clustering, contacts, SASA, SAM/DCMB geometry and report/database persistence: 2–4 hours.
- No GPU/long MD is required. Exact timing will be benchmarked with one symmetric A/B canary before the main matrix.

## METHODOLOGICAL_ISSUES

1. **Decisive issue:** current DiffDock provenance does not prove that SAM influenced inference; `HETATM` retention in the uploaded PDB is insufficient.
2. The two uploaded archives contain one 20-pose batch each and no model version, weights, seeds or inference configuration. They cannot yet satisfy independent-search reproducibility.
3. The paper lacks the Glide version, grid, receptor preparation, ligand state, pose count and coordinate output; exact reproduction is impossible from reported text alone.
4. Global Vina searches are sampling-intensive and are a control/site-search device, not evidence of affinity.
5. Rigid receptor docking cannot measure SAM-network disruption; it can only test steric coexistence in the frozen canonical state.
6. Apo versus SAM score changes cannot establish SAM displacement.

## NEXT_DECISION

`MAIN_MATCHED_STUDY_AUTHORIZED = NO — AWAITING_PREFLIGHT_REVIEW`

Before launch, the required decision is whether to proceed with the cofactor-aware Vina matrix above while retaining uploaded DiffDock only as an independent global site-search channel. If approved, the first action is a parser/charge canary proving SAM survives receptor preparation, followed by a one-seed symmetric A/B runtime benchmark. No comparative scientific interpretation occurs before those technical gates pass.
