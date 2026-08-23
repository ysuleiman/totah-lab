# TSL observable completeness revalidation

This audit re-read the immutable GPU60, CURVATURE76, MIN01, MIN02, MIN04,
frequency, and normal-mode artifacts from disk. It did not run QM, fit a model,
modify frozen evidence, or change a scientific threshold.

GPU60 passes the new observable checks for all 60 points. CURVATURE76 passes for
all 76 points. In both datasets, nested checksums verify; electronic and D3
energies and gradients are finite and compose to the totals; forces are exactly
the negative total gradients within the predeclared serialized-value tolerance;
and each D3 component is nonzero and bound to per-point geometry, protocol,
software, and checksummed result evidence.

The three historical minimum Hessian arrays remain preserved evidence of the
electronic PBE analytic calculation only. They are not complete PBE-D3(BJ)
Hessians. After conversion from PySCF tensor order
`[atom_i, atom_j, axis_i, axis_j]` to canonical Cartesian matrix order
`[atom_i, axis_i, atom_j, axis_j]`, their maximum raw asymmetries are
`1.2078136392701389e-6` (MIN01), `9.658514965593668e-8` (MIN02), and
`1.58109189740685e-7` Ha/bohr² (MIN04), all above the locked `1e-8`
completeness diagnostic. No raw artifact was altered or symmetrized.

The frequency and mass-weighted normal-mode artifacts have valid historical
checksums and expected dimensions, but they were generated from the PBE-only
Hessian. They lack a standalone binding to the exact source-Hessian checksum and
must not be advertised as PBE-D3(BJ) vibrational observables.

The attempted D3 Hessian completion is invalidated. Its producer loaded the
PySCF `[56,56,3,3]` tensor and called `.reshape(168,168)` without first applying
the required axis permutation. Consequently its electronic and D3 matrices use
different Cartesian index topologies, and its persisted totals do not represent
`H_total = H_electronic + H_dispersion` componentwise in one coordinate basis.
Downstream total-Hessian analysis and the historical Hessian-bonded V3 lineage
are not trusted inputs to the current model-form decision.

Machine-readable paths, checksums, classifications, and invalidations are in
`TSL_OBSERVABLE_COMPLETENESS_REVALIDATION.json`.
