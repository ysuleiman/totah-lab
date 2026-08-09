# DCMB anchor / exit / rebinding dynamics checkpoint

## Outcome

The four matched explicit-solvent systems were fully constructed, parameterized,
solvated, and minimized, but this CPU-only host could not complete the predefined
multi-replica unbiased trajectory matrix within the bounded task window. No DCD
production frame was completed. Consequently, exit, re-entry, recapture, state
occupancy, and recapture-contact enrichment are **unevaluated**. The previously
reported exit/re-entry observation is not treated as reproduced.

No top-100 screen, mutation, redocking, steering, or enhanced sampling was run.

## Phase 1 — audited starting states

The minimal starting set uses the strongest recurrent on-site families selected
before dynamics:

| System | Stereochemistry | Family/source | SAM state | Productive-TSL conflict |
|---|---|---|---|---|
| METTL7A–DCMB | S | family 2, seed 42 rank 1 | contacting | 5/5 states |
| METTL7A–2,4 isomer | R | family 2, seed 42 rank 1 | contacting | 5/5 states |
| METTL7B–DCMB | R | family 1, seed 42 rank 1 | contacting | 6/6 states |
| METTL7B–2,4 isomer | R | family 2, seed 42 rank 1 | contacting | 6/6 states |

All ligands retain formal charge +1. Receptor and pose SHA-256 values, centroids,
orientation axes, SAM distance, directional occupancy, mouth projection, and
static residue fingerprints are in `starting_state_audit.csv`.

## Phase 2 — state definitions frozen before trajectory inspection

`state_definitions.json` was written before propagation. Briefly:

- **INNER:** ≥70% ligand-heavy-atom homologous-superpocket containment and overlap
  with the pre-existing productive-TSL envelope.
- **ESCAPE:** ≥70% containment with zero productive-TSL-envelope occupation.
- **MOUTH:** no longer INNER/ESCAPE, but ≥25% containment or within 6 Å of a pocket
  sphere.
- **OUTSIDE:** <25% containment and more than 6 Å from every sphere.

A new state had to persist for five frames (10 ps at the planned 2 ps interval).
These definitions were not adjusted after the failed propagation attempts.

## Phase 3 — prepared systems

All four systems use Amber ff14SB protein parameters, GAFF 2.11/AM1-BCC for SAM
and ligand, TIP3P water with 1.0 nm padding, 0.15 M ions, PME, 300 K, 1 bar, and a
2 fs timestep. System sizes are 41,153–46,437 atoms. SAM and ligand begin at the
validated/docked heavy coordinates. Production was specified as fully unrestrained.

The system XML, minimized coordinates, solvated PDB, metadata, and shared GAFF
cache are preserved under `systems/`. These are prepared starting systems, not
trajectories and not evidence of dynamics.

## Phase 4 — sampling attempt

OpenMM detected CPU and OpenCL platform plugins, but OpenCL context creation failed
because this host has no compatible device. The original matched plan was three
independent 200 ps productions per system after matched equilibration. It was
stopped before the first saved production frame when CPU propagation exceeded the
bounded task window. A reduced 20 ps matrix was also stopped before its first
production frame; shortening further would not support the predefined 10 ps
persistence rule or the requested recapture question.

Incomplete runs produced no trajectory files and are excluded rather than treated
as negative observations. `sampling_attempt.json` and `trajectory_manifest.csv`
record all twelve planned replicas as `NOT_COMPLETED_CPU_LIMIT`.

## Requested answers

1. **Does DCMB exit and re-enter METTL7A?** Unevaluated; no production frames.
2. **Does DCMB escape more persistently from METTL7B?** Unevaluated.
3. **Which residues engage during METTL7A recapture?** Unevaluated. Static
   candidates remain hypotheses only.
4. **Are those anchors weakened or absent in METTL7B?** Sequence/chemistry changes
   such as F43→L43 and F199→G199 are documented, but their dynamic role is
   unevaluated.
5. **Does the 2,4 isomer show the same recapture behavior?** Unevaluated.
6. **Size/geometry, chemical anchors, or both?** The static project supports
   geometry differences; this task provides no dynamic evidence to partition the
   mechanism.
7. **Is dynamic recapture a defensible top-100 discriminator?** Not yet.

## Preserved outputs and next strategy

Machine-readable files deliberately contain empty per-frame/event/enrichment
tables rather than fabricated zeros. The next experiment should run these exact
prepared systems on a CUDA/OpenCL-capable GPU with substantially longer matched
unbiased replicas. Only if those remain transition-free should a separately
approved enhanced-sampling design use the predefined pocket-exit coordinate. No
enhanced sampling is executed here.

**INSUFFICIENT SAMPLING**
