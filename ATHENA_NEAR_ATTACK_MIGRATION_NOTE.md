# Near-Attack Geometry Migration Note (Python → athena.tmt)

Date: 2026-09-05

The repository accumulated multiple inconsistent Python implementations of SAM
methyl-transfer near-attack geometry. The authoritative implementation going
forward is Java, under
`software/modules/athena/src/main/java/totah/lab/athena/tmt/`:

- `NearAttackGeometry` — record of acceptor···C(methyl) distance,
  acceptor–C(methyl)–S(SAM) attack angle, C(methyl)–S(SAM) donor distance, and
  severe-clash count, computed via `NearAttackGeometry.from(...)`.
- `NearAttackCriteria` — caller-supplied thresholds
  (min/max attack distance, min/max attack angle, max donor-bond distance,
  max severe-clash count) plus a mandatory non-blank `provenance` string.
  There are no built-in defaults by design.
- `NearAttackAssessor` — gates a `NearAttackGeometry` against a
  `NearAttackCriteria`, yielding CLEARLY_NONPRODUCTIVE /
  GEOMETRICALLY_NEAR_PRODUCTIVE / CHEMICALLY_COMPATIBLE_CANDIDATE.
- `EnsembleNacAnalyzer` — replica-resolved NAC population summaries
  (per-replica NAC fraction, transitions, longest run; requires ≥ 2 replicas).

## Historical Python implementations and their exact gates

| Old script (path) | Old thresholds (exact) | SAM / acceptor atom convention | Authoritative Java replacement | Status |
|---|---|---|---|---|
| `analysis/dcmb/tsl_catalytic_geometry/reconstruct_tsl.py` | Placement sampled at acceptor···CE distances 2.8 / 3.0 / 3.2 Å; attack angle sampled uniformly in the 150–180° cone (30° half-angle about the CE–SD axis). Retention gate: `protein_pairs_lt_2A == 0` AND `sam_pairs_lt_2A == 0` AND `superpocket_atom_fraction >= 0.70` (angle ≥ 150 guaranteed by sampling). | SAM `SD` (sulfur), `CE` (methyl carbon). Acceptor: the single sulfur atom of TSL (7α-thiospironolactone thiol S). | `NearAttackGeometry.from` + `NearAttackAssessor.assess` with a provenance-tagged `NearAttackCriteria` | pending regression reproduction |
| `analysis/dcmb/tsl_conformational_response.py` | Inherits `reconstruct_tsl` distances (2.8/3.0/3.2 Å) and 150–180° cone. Relaxation pass gate: `protein_pairs_lt_2A == 0` AND `sam_pairs_lt_2A == 0` AND `max_bond_deviation_A <= 0.15`; response classes by `max_atom_displacement_A < 1 / < 2.5` (backbone stage additionally `backbone_rmsd_A < 1`). Product state: methyl carbon placed 1.81 Å from TSL sulfur; `product_viable` if product protein pairs < 2 Å == 0 AND SAH product pairs < 2 Å == 0. | SAM `SD`/`CE`; SAH = SAM minus `CE`. Acceptor: TSL sulfur. | `NearAttackGeometry.from` + `NearAttackAssessor.assess` (geometry/clash gates); relaxation-class logic stays campaign-specific | pending regression reproduction |
| `analysis/mettl7-closure/stage3/run_tsl_matrix.py` | Distances `rt.DISTANCES` = 2.8/3.0/3.2 Å; 150–180° angle cone. Near-miss selection: `sam_pairs_lt_2A == 0` AND `superpocket_atom_fraction >= 0.70` (ordered by `protein_pairs_lt_2A`). `response_pass`: `protein_pairs_lt_2A == 0` AND `sam_pairs_lt_2A == 0` AND `max_bond_deviation_A <= 0.02` AND `backbone_rmsd_A <= 0.25` AND `max_atom_displacement_A <= 1.50`. | SAM `SD`/`CE`. Acceptor: TSL sulfur index. | `NearAttackGeometry.from` + `NearAttackAssessor.assess`; response-pass extras remain campaign-specific | pending regression reproduction |
| `research/mettl7-bi187004-near-attack/run_near_attack.py` | `THRESHOLDS`: distance range 2.8–3.2 Å (sampled 2.8/3.0/3.2), angle sampling 150–180°, `angle_min_deg = 150.0`, protein severe clash 1.8 Å, SAM severe clash 2.0 Å, pocket containment ≥ 0.70, ligand strain ≤ 15 kcal/mol, sidechain RMS displacement ≤ 1.0 Å, sidechain max displacement ≤ 2.5 Å, sidechain bond deviation ≤ 0.15 Å, ≥ 3 independent starts. Retention gate (line 114): `2.75 <= d(N···CE) <= 3.25` AND `angle >= 150`. Valid-after-relaxation also requires protein/SAM post-relaxation clash counts == 0. | SAM `CE` (methyl), `SD` (sulfur). Acceptors: BI-187004 benzimidazole `N1` (atom index 19) / `N3` (index 21), both tautomers. | `NearAttackGeometry.from` + `NearAttackAssessor.assess`; ensemble-level retention handled per-frame with criteria | pending regression reproduction |
| `research/mettl7-bi187004-campaign/run_campaign.py` (line 190) | `near_attack_screen = PASS` if `d(acceptor···CE) <= 3.5` Å AND `angle(acceptor–CE–SD) >= 150°` (no lower distance bound). | SAM `CE`/`SD`. Acceptor: per-tautomer `acceptor_atom` from the ligand spec. | `NearAttackGeometry.from` + `NearAttackAssessor.assess` with explicit criteria (min distance must be passed explicitly; Java requires one) | pending regression reproduction |
| `research/mettl7-reaction-competence-v1/run_analysis.py` (line 110) | Netarsudil: `2.8 <= d <= 3.2` Å AND `angle >= 150°` measured in accepted poses. Captopril arm reuses the frozen `run_near_attack` machinery (2.8/3.0/3.2 Å sampled, 150–180° cone, clash 1.8/2.0 Å, containment ≥ 0.70). | SAM `CE`/`SD`. Acceptors: netarsudil `N5` (terminal primary amine) and `N6` (isoquinoline N); captopril thiol S (`THIOL_S` / `THIOLATE_S`). | `NearAttackGeometry.from` + `NearAttackAssessor.assess` | pending regression reproduction |
| `research/mettl7-selectivity-forensics/dcmb-analog-program/analyze_analog_campaign.py` (line 134) | `near_attack_geometry = CANDIDATE` if `d <= 3.5` Å AND `angle >= 150°`; else `NOT_OBSERVED`. | SAM `CE`/`SD` with fallback names `C9`/`S8`. Acceptor: closest ligand N/S/O atom to the methyl carbon. | `NearAttackGeometry.from` + `NearAttackAssessor.assess` | pending regression reproduction |
| `research/mettl7-selectivity-forensics/dcmb-analog-program/pre-sah-v1.6-snapshot/analyze_analog_campaign.py` | Identical gates to the above (snapshot copy, same line 134). | Same as above. | `NearAttackGeometry.from` + `NearAttackAssessor.assess` | pending regression reproduction |
| `research/mettl7-selectivity-forensics/dcmb-analog-program/sah-campaign-v1/analyze_sah_campaign.py` (line 178) | `methyl_geometry_interpretation = NOT_NEAR_ATTACK` if `d(amine···methyl) > 3.5` Å OR `angle(amine–methyl–S) < 150°` (SAM state only; SAH state reports distance to vacated methyl region only). | Methyl point: SAM atom named `C9`; sulfur: cofactor atom with element S (`SD`). Acceptor: DCMB amine nitrogen. | `NearAttackGeometry.from` + `NearAttackAssessor.assess` | pending regression reproduction |
| `research/mettl7-independent-audit/scripts/analyze_klf4_final_interface_validation.py` (line 20) | `ADMISSIBLE` if `2.7 <= d <= 3.5` Å AND `145 <= angle <= 180°`. Clash counts (< 1.8 Å, < 2.4 Å) reported but not gated. Bilateral decision gate: ≥ 5 admissible models and ≥ 3 admissible conformers per enzyme arm. | Anchor pseudoatoms on chain `S`: resid 1 = methyl-carbon proxy, resid 2 = sulfur proxy. Acceptor: RNA target residue 24 `N7`. | `NearAttackGeometry.from` + `NearAttackAssessor.assess`; per-arm admissibility counts via `EnsembleNacAnalyzer`-style aggregation | pending regression reproduction |
| `research/mettl7-independent-audit/scripts/analyze_haddock_rna_batch.py` (lines 54–55) | `distance_window_satisfied`: `2.8 <= d(target···CE) <= 3.4` Å; `approx_angle_window_satisfied`: `150 <= angle <= 180°`. Clash counts (< 1.8 Å, < 2.4 Å) reported, not gated. | Chain-`S` `SHA` anchor pseudoatoms: resid 1 = CE proxy, resid 2 = SD proxy. Acceptor: RNA resid 3 `N7` (KLF4/NFKBIA sites) else `N6`. | `NearAttackGeometry.from` + `NearAttackAssessor.assess` | pending regression reproduction |
| `research/mettl7-independent-audit/scripts/analyze_reciprocal_rna_selectivity_pilot.py` (line 45) | `distance_window`: `2.8 <= d <= 3.4` Å; `angle_window`: `150 <= angle <= 180°`; `both_windows` = conjunction. | Chain-`S` anchors resid 1/2 (methyl/sulfur proxies). Acceptor: RNA resid 3 `N6` (FILIP1L_GGACT) else `N7`. | `NearAttackGeometry.from` + `NearAttackAssessor.assess` | pending regression reproduction |
| `research/mettl7-independent-audit/scripts/build_catalytic_rna_models.py` (lines 193–194) | Placement grid: distances 2.8 / 3.0 / 3.2 / 3.4 Å; angles 150 / 160 / 170 / 180°. Candidate ranking by severe clashes < 1.8 Å, close pairs < 2.4 Å, contacts ≤ 4.5 Å. | SAM `SD`/`CE` (direction from `CE` away from `SD`). Acceptors: adenine `N6` or guanine `N7` per site in `MODELS`. | `NearAttackGeometry.from` + `NearAttackAssessor.assess` per candidate frame | pending regression reproduction |

## Reproducibility hazard and the V2 rule

These scripts disagree with each other on the core gates: the acceptor···methyl
distance window spans 2.7–3.5 Å depending on the script (lower bounds of 2.7,
2.75, 2.8, or no lower bound; upper bounds of 3.2, 3.25, 3.4, or 3.5 Å), the
attack-angle floor is either 145° or 150°, clash gates vary between 1.8 Å and
2.0 Å pair cutoffs, and some scripts add pocket-containment, strain, or
relaxation-displacement gates that others omit entirely. Results computed under
one script's gates are therefore not comparable with results computed under
another's, and silently "re-running the same analysis" can change verdicts.

The rule for the V2 campaign: **all near-attack geometry is computed only via
`athena.tmt` (`NearAttackGeometry`, `NearAttackAssessor`, and, for ensembles,
`EnsembleNacAnalyzer`), driven by explicit, provenance-tagged
`NearAttackCriteria` instances.** `NearAttackCriteria` has no built-in defaults
by design — each historical threshold set in the table above must be passed
explicitly as a named, provenance-tagged criteria instance (e.g.
`"reconstruct_tsl.py@2026-08-08: d in [2.8,3.2] sampled, angle in [150,180],
clash<2A==0, containment>=0.70"`), so every verdict carries an auditable record
of which historical gate set it reproduces. The historical Python numbers will
be reproduced through the Java path and the deltas recorded in
`ATHENA_INTERACTION_V2_REGRESSION_RESULTS.csv` (being produced separately)
**before any of these scripts is archived**. Until a script's row in that CSV
is reconciled, its status stays "pending regression reproduction" and it is
retained, unmodified except for its deprecation header, for historical
regression reproduction only.

## Note on path corrections vs. the original audit

- The audit listed the netarsudil N5/N6 gates at
  `research/mettl7-netarsudil-sam-mechanism/**/run_analysis.py:110`; the actual
  location is `research/mettl7-reaction-competence-v1/run_analysis.py:110`
  (the netarsudil-sam-mechanism tree contains no `run_analysis.py` with
  near-attack gates).
- `analyze_analog_campaign.py` exists twice (main copy and the
  `pre-sah-v1.6-snapshot` copy) with identical gates; both are listed and both
  carry the deprecation header.
