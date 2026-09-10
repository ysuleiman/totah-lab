# SurfDiff 1.0.0 source audit and Athena mapping

Frozen upstream: `ch/sormanni/surfdiff` commit
`b8dceec575de43dff1b5297774affe356a183466` (audited 2026-09-10).
The source implementation, not the paper, is normative for
`SURFDIFF_COMPATIBLE`.

| Upstream source behavior | Frozen definition | Athena owner |
|---|---|---|
| `compare.SurfDiffConfig` | CA neighborhood 7 Å; scoring cutoff 5 Å; exclude N/C/O/OXT from neighborhood atom index; symmetry on; neighborhood update off; mutations-only off | `DifferentialSurfaceOptions.SURFDIFF_COMPATIBLE` |
| `fragmentation.fragmentation_neighbourhood` | CA-to-CA distance selects residue membership within 7 Å. Reported residue distance is the minimum pair distance after excluding N/C/O from both residues; CA and OXT remain eligible for the reported minimum. Self distance is 0. | `LocalResidueNeighborhood` |
| `analysis.Residue_Uniqueness_Potential_calculation` | Fixed 21×21 physicochemical matrix divided by 18. Unknown correspondence (`XXX`) is distance 1. With default `neighbourhood_update=false`, only entries whose clipped distance weight equals 1 enter RUP, normally the central residue. | `SurfDiffPhysicochemicalDifference`, `DifferentialSurfaceAnalyzer` |
| `analysis.calc_weight_distance` | `clip(1 - (x - 1)/(xMax - 1), 0, 1)` | `SurfDiffWeights.distance` |
| `analysis.calc_weight_exposure` | zero below rSASA 0.05; otherwise `1/(1+(0.5/(x+0.5))^5)` | `SurfDiffWeights.exposure` |
| `analysis.calc_weight_sasa_difference` | clipped sigmoid in [0.5,1]. Source computes this in symmetric setup but the production RUS calculation does not consume the prepared SASA-weight array. | `SurfDiffWeights.sasaDifference`; deliberately not applied to RUS |
| `sasa.relative_sasa` | Biopython 1.85 Shrake-Rupley, probe 1.4 Å, 100 points, Biopython atomic radii; chain-list first/last residues use terminal reference table, all others mid-residue table. Absolute values `<0` are zeroed; surface inclusion is strict SASA `>0` and rSASA `>0.05`. Unknown residue reference gives rSASA 0. | `SurfDiffCompatibleSasa`, reusing `ShrakeRupleySasa` with 100 points because Gaia protein-element radii match Biopython's table |
| `analysis.Residue_Uniqueness_Score_calculation` | Combine query and mapped subject neighborhoods; common member distance is the minimum; query-only and subject-only members remain; subject-only RUP=1 and subject exposure is used. Filter at distance ≤5 Å. RUS is exposure- and distance-weighted RUP mean; zero denominator gives 0. | `DifferentialSurfaceAnalyzer` |
| same | RSS = `1 - RUS` | `DifferentialResidueScore` |
| `analysis.create_result_table` | Across multiple subjects, combined RUP/RUS/RSS are column-wise minima. Query residues with rSASA ≤0.05 have score outputs forced to zero in the combined table. | `SurfDiffSubjectAggregator`; surface-zeroing remains presentation/table policy |
| `discriminate.calculate_rss_values`, `calculate_rus_values` | Minimum RSS across similar subjects and minimum RUS across different subjects | `SurfDiffSubjectAggregator.rds` |
| `discriminate.calculate_rds_values` | `clip(minSimilarRSS - (1 - minDifferentRUS), 0, 1)` | `ResidueDiscriminabilityScore` |
| vertex aggregation in `analysis` | Surface vertices within 3 Å of atoms; per-residue duplicate retains greatest inverse-square weight (smallest distance); weighted mean of residue RUP/RUS/RSS | `JAVA_CAPABILITY_GAP`: mesh/vertex projection |

## Compatibility differences and tolerances

Athena's normal SASA default uses 96 sphere points; compatible mode explicitly uses
100. For standard protein elements, Gaia and Biopython 1.85 van der Waals radii
match. Biopython builds the unit sphere in float32 and fragmentation passes
coordinates/distances through NumPy float32; Gaia retains doubles. Frozen parity
tolerances are 1e-6 Å² for SASA, 1e-6 Å for residue distance, and 1e-8 for
RUP/RUS/RSS. The observed maximum score difference in the representative fixture
is 1.983e-9.

The compatibility kernel reports local opportunity for discrimination. It is not
an energy, affinity, or delta-delta-G estimator.
