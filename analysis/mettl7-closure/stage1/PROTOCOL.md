# Locked eight-system mechanistic comparison protocol

Status: **LOCKED_BEFORE_STAGE2**. Baseline commit: `74ac4580e`.

This protocol covers METTL7A WT, F43L, F199G, and F43L/F199G plus METTL7B WT, L43F, G199F, and L43F/G199F. It authorizes no other mutation or physical state. All canonical mechanistic calculations use fixed positioned SAM.

## Non-negotiable controls

- Native AlphaFold DB v6 frames and Stage 0 SAM coordinates are preserved.
- Cross-paralog measurements use the same 244-residue C-alpha fit.
- Mutation construction cannot move backbone or unrelated side chains. Only METTL7B positions 43 and 229 may undergo the frozen local repacking procedure.
- R- and S-DCMB are sampled separately with Vina 1.2.5, seeds 1/7/42, exhaustiveness 32, and nine requested modes.
- Docking scores remain engine outputs only.
- Missing or failed evidence is `NOT_EVALUABLE`, never zero.
- Evidence dimensions remain separate; there is no master score.

## Productive TSL gate

Static feasibility is tested first. Productive geometry requires a TSL sulfur-to-SAM methyl-carbon distance of 2.8, 3.0, or 3.2 Å; attack angle 150–180 degrees; no protein or nonreactive-SAM heavy-atom pair below 2.0 Å; and at least 70% TSL-heavy-atom containment in the 197-sphere superpocket.

Limited receptor response is allowed only after static failure. SAM and TSL remain fixed. Backbone RMSD may not exceed 0.25 Å, no atom may move more than 1.50 Å, and bond deviation may not exceed 0.02 Å. A state exceeding any bound fails; relaxation cannot manufacture a pass.

Every passing geometric family is retained. There is no best-score TSL state.

## DCMB gate and families

A canonical pose has at least 70% of its heavy atoms within 4 Å of the homologous 197-sphere superpocket. Off-site poses remain in the raw inventory with an explicit rejection reason. On-site poses are clustered separately by system and enantiomer using complete-linkage direct heavy-atom RMSD at 2.0 Å. The medoid is selected geometrically; docking score does not select representatives.

Each seed must return at least eight modes. A deficient system/enantiomer is `NOT_EVALUABLE_DOCKING_FAILURE`; poses are not invented and settings are not selectively changed.

## Independent measurements

The fixed outputs are SAM compatibility, productive TSL feasibility, required receptor movement, DCMB families, direct DCMB–TSL overlap, transfer-corridor interference, accessible volume, bottleneck dimensions, contact fingerprints, ligand centroid/orientation, mutation-induced changes, and explicit failure modes.

Direct overlap uses minimum distance, pair counts below 2.0/2.5 Å, shared 1.0 Å core volume, shared 1.7 Å occupied volume, and reciprocal envelope atom counts. Corridor blockade requires a DCMB atom between TSL sulfur and the SAM methyl carbon and within 2.0 Å of that segment. Conformational-gating interference requires at least 0.5 Å³ shared swept-envelope volume; tangential distance alone is retained but cannot trigger the mechanism label.

Families are broadly interfering only when direct overlap or corridor blockade occurs for every productive state; state-dependent when it occurs for some; and an escape only when at least one state has no direct overlap, no corridor blockade, and zero shared 1.7 Å volume.

## Mutation effects

Every mutant is compared only with its same-target WT. For each metric independently, the double-mutant interaction is:

`I = (double − WT) − (position-43 single − WT) − (position-199 single − WT)`.

Seed-sampled effects use a stratified-by-enantiomer seed bootstrap. Additivity requires the 95% interval for `I` to include zero. Cooperative and antagonistic labels require exclusion of zero in the same or opposite direction as the double-mutant effect. Deterministic single-structure metrics report `I` without an additivity label because they lack replicate uncertainty.

Reciprocal mutations are allowed to be asymmetric and background dependent.

## Stop rule

Unexpected results first trigger preparation, frame, protocol, and physical-asymmetry audits. They do not authorize a new metric, mutation scan, broader docking campaign, MD, or composite score.

The complete normative specification is `protocol.json`.
