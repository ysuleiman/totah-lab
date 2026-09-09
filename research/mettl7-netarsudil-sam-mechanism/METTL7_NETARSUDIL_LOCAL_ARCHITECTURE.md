# METTL7 netarsudil/SAM local-architecture analysis

**Run key:** `METTL7_NETARSUDIL_LOCAL_ARCHITECTURE_2026_08_30`  
**Scope:** matched structural analysis and bounded neutral-state sensitivity tests around residues 190–210. No broad docking, MD, affinity calculation, or composite score was performed.

## Classification

`NETARSUDIL_7B_LOCAL_SELECTIVITY_MECHANISM = DISTRIBUTED_196_207`

`C203_CAUSAL_SUPPORT = NOT_SUPPORTED`

The classification identifies a distributed **structural-context** difference, not a set of causally proven biochemical selectivity residues. C203 is a persistent outside-pocket contact, but exchanging C203/N203 does not explain the 7A/7B difference. The 7B side-chain identities at 196–199 and 208 are also individually insufficient: every clash-qualified reciprocal probe remains structurally admissible in the 7B backbone.

## Evidence summary

The accepted 7B pose uses a continuous extension contact network: K196, D200, G201, C203, T205, R206 and E207. The corrected lowest-strain 7A pose remains SAM-adjacent and clash-free but uses H196, L197, D200 and G201, while positions 203 and 205–207 are no longer contacts. Thus 7A avoids the native transferred-pose bottleneck by adopting a different placement that abandons most of the extension network and retains 19.80 kcal/mol MMFF94s strain.

The gross local free-volume proxy does not explain the difference: corrected 7A has 2312.6 Å³ versus 2298.1 Å³ for the accepted 7B representative. Instead, the extension is more solvent-exposed and less enclosed in 7A. Examples of starting residue SASA are N203/C203 30.0/17.8 Å², D200 51.2/34.0 Å², R206 49.8/38.2 Å² and E207 67.0/30.9 Å² for 7A/7B. These are geometric exposure estimates, not thermodynamic solvation energies.

The matched Hephaestus-charge electrostatic potential proxy averaged over ligand heavy atoms is -1.630 for corrected 7A and -2.072 for accepted 7B in arbitrary but internally matched units. This supports a different local electrostatic environment but is not an interaction energy or affinity estimate.

## Residue identities and native geometry

| Position | METTL7A | METTL7B | Structural observation |
|---:|---|---|---|
| 190 | V | V | distant from both placements |
| 191 | L | F | solvent-exposed upstream difference; >10 Å from ligand |
| 192 | D | E | 7B is closer to accepted ligand (4.49 Å versus 8.81 Å in corrected 7A) |
| 193 | P | P | conserved |
| 194 | A | T | not a direct contact |
| 195 | W | W | conserved aromatic architecture; not a direct contact |
| 196 | H | K | direct contact in both final poses; native 7A H rotamer blocks exact 7B-pose transfer |
| 197 | L | H | direct only in corrected 7A placement |
| 198 | L | I | no direct contact |
| 199 | F | G | no direct contact in either representative; known ligand-corridor landmark |
| 200 | D | D | conserved productive-pocket wall and direct contact |
| 201 | G | G | conserved productive-pocket wall and direct contact |
| 202 | C | C | conserved productive-pocket wall, no direct netarsudil contact |
| 203 | N | C | 7B outside-pocket direct contact; absent from corrected 7A placement |
| 204 | L | L | buried, no direct contact |
| 205 | T | T | direct only in accepted 7B extension network |
| 206 | R | R | direct only in accepted 7B extension network |
| 207 | E | E | direct only in accepted 7B extension network |
| 208 | S | T | not a direct contact; reciprocal T208S is tolerated in 7B |
| 209 | W | W | conserved, outside direct-contact range |
| 210 | K | K | conserved, outside direct-contact range |

After the global 244-Cα fit, backbone RMSDs across 190–210 range from 0.37 to 0.92 Å. Position 203 is the largest local backbone difference at 0.92 Å, but the differences are distributed rather than a discrete backbone rearrangement.

Native side-chain orientations also differ. Representative χ1/χ2 values include H196 in 7A at -178.6°/82.2° versus K196 in 7B at -76.8°/170.6°; L197 in 7A at -167.7°/63.9° versus H197 in 7B at -65.5°/-44.5°; and conserved R206 at -157.9°/76.2° in 7A versus -166.4°/165.5° in 7B. These values describe the static starting models and do not establish solution rotamer populations.

## Corrected 7A versus accepted 7B

| Metric | Corrected 7A lowest-strain pose | Accepted 7B representative |
|---|---:|---:|
| MMFF94s ligand strain | 19.798 kcal/mol | 14.037 kcal/mol |
| minimum ligand–SAM distance | 3.391 Å | 3.683 Å |
| burial reduction | 69.91% | 61.56% |
| local free-volume proxy | 2312.6 Å³ | 2298.1 Å³ |
| regional electrostatic proxy | -1.630 | -2.072 |
| 196–207 direct contacts | H196, L197, D200, G201 | K196, D200, G201, C203, T205, R206, E207 |

The corrected 7A pose is neither under-buried nor SAM-clashing. Its higher strain therefore cannot be attributed to a simple lack of space or cofactor overlap.

## Transfer and local-refinement behavior

The exact accepted 7B geometry transferred to native 7A begins approximately 1.02 Å from native H196 and is therefore locally incompatible without rearrangement. During bounded refinement it migrates 6.80 Å by centroid and 7.79 Å by symmetry-aware ligand RMSD, loses six of nine starting contacts, moves to 6.63 Å from SAM and finishes at 15.56 kcal/mol strain.

This does not make H196 identity causal. When H196 is installed in the 7B backbone using a preregistered clash-free rotamer, the ligand remains in place and passes all gates. The observation instead implicates the combined backbone/rotamer/contact topology of the native 7A extension.

## Bounded mutation probes

All values are medians across three deterministic seed-labelled confirmations.

| System | Gate | Final strain | Ligand RMSD | Centroid shift | SAM distance | Burial | Retained contacts |
|---|---|---:|---:|---:|---:|---:|---:|
| 7B WT | PASS 3/3 | 14.03 | 0.12 Å | 0.08 Å | 3.79 Å | 60.63% | 10/10 |
| 7B C203N | PASS 3/3 | 13.71 | 0.73 Å | 0.63 Å | 4.05 Å | 58.69% | 10/10 |
| 7B K196H | PASS 3/3 | 13.81 | 0.18 Å | 0.13 Å | 3.83 Å | 59.37% | 10/10 |
| 7B H197L/I198L | PASS 3/3 | 13.76 | 0.10 Å | 0.06 Å | 3.76 Å | 60.16% | 10/10 |
| 7B G199F | PASS 3/3 | 13.82 | 0.13 Å | 0.06 Å | 3.86 Å | 60.84% | 10/10 |
| 7B T208S | PASS 3/3 | 13.42 | 0.13 Å | 0.05 Å | 3.87 Å | 60.67% | 10/10 |
| 7B reciprocal 196–199 | PASS 3/3 | 13.57 | 0.22 Å | 0.18 Å | 3.75 Å | 59.40% | 10/10 |
| 7B reciprocal 196–199 + C203N | PASS 3/3 | 13.22 | 0.74 Å | 0.66 Å | 3.98 Å | 55.75% | 10/10 |
| 7A WT transfer | FAIL 0/3 | 15.56 | 7.79 Å | 6.80 Å | 6.63 Å | 51.48% | 3/9 |
| 7A N203C transfer | FAIL 0/3 | 21.78 | 7.99 Å | 6.93 Å | 6.90 Å | 49.95% | 3/8 |

The initial K196H transplant and K196-containing block outputs were excluded because the transplanted histidine began 0.73 Å from netarsudil. They were replaced by a frozen discrete rotamer correction: χ1 -60°, χ2 0°, minimum protein distance 2.24 Å and minimum ligand distance 3.77 Å. Only the corrected outputs enter the table and classification.

## C203/N203 causal test

`C203_N203_DIRECT_EFFECT = NOT_SUPPORTED`

- C203N does not disrupt 7B; it slightly lowers strain while retaining all ten contacts.
- N203C does not rescue 7A; migration remains and final strain increases relative to the WT transfer.
- C203 remains a reproducible contact in WT 7B, but contact persistence is not equivalent to causal selectivity.

`SURROUNDING_196_207_PACKING_EFFECT = NOT_SUPPORTED_AS_SIDECHAIN_IDENTITY_EFFECT`

Every tested reciprocal identity or block is tolerated in the 7B backbone when initialized without severe clashes. Therefore none of 196, 197–198, 199 or 208 is sufficient to reproduce the 7A phenotype in this model.

`DISTRIBUTED_LOCAL_EXTENSION_EFFECT = SUPPORTED_AT_STRUCTURAL_CONTEXT_LEVEL`

The supported explanation is the collective native geometry: sub-ångström backbone differences, different native rotamers, altered solvent enclosure/electrostatics, and loss of the 203/205–207 extension contacts in corrected 7A. This is a structural-context conclusion, not residue-level causality.

## Ligand-strain localization

The strain difference cannot be assigned uniquely to one ligand torsion. Relative to one MMFF94s minimized reference, corrected 7A shows larger deviations than accepted 7B at torsion 4 (111.6° versus 3.4°), torsion 7 (172.4° versus 133.1°) and torsion 8 (149.1° versus 124.0°), while 7B has a larger torsion-1 deviation. Because MMFF94s energy is coupled across bonded and nonbonded terms, these deviations are diagnostic only; no per-torsion energy claim is made.

## Confidence and limits

Confidence is **moderate** that C203 is not dominant within this bounded model, because the result is reciprocal and reproduced across all technical attempts. Confidence is **moderate-low** in the distributed 196–207 mechanism because Vina `local_only` is deterministic, the backbone is fixed, mutation side chains require modeled rotamers, and the accepted 7B strain remains close to the 15 kcal/mol gate.

The analysis supports structural compatibility only. It does not establish affinity, catalytic activation, allostery, biochemical selectivity, or simultaneous RNA engagement.

## Reproduction and artifacts

- Frozen primary protocol: `local-architecture/frozen_protocol.json`
- Minimal-probe addendum: `local-architecture/minimal_probe_protocol.json`
- K196 rotamer correction: `local-architecture/k196_rotamer_correction_protocol.json`
- Preparation manifest: `local-architecture/preparation_manifest.json`
- Run manifests: `local-architecture/run_manifest.json`, `minimal_probe_run_manifest.json`, `k196_rotamer_correction_run_manifest.json`
- Replicate metrics: `local-architecture/analysis/replicate_metrics.csv`
- Residue metrics: `local-architecture/analysis/residue_190_210_metrics.csv`
- Corrected 7A/accepted 7B comparison: `local-architecture/analysis/corrected_7a_vs_accepted_7b_residues.csv`
- Ligand torsion diagnostics: `local-architecture/analysis/corrected_7a_vs_accepted_7b_ligand_torsions.csv`
- Machine-readable classification: `local-architecture/analysis/classification.json`
- Reproduction scripts: `local-architecture/scripts/`
