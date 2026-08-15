# Prometheus numerical test effectiveness

## Permanent tests added

- `GeneralMolecularMatrixFreeSrOptimizerTest` now reconstructs the formerly
  failing larger covariance fixture through the private statistics boundary and
  asserts every covariance element equals its transpose. This test fails under
  the historical one-pass mean/covariance bug.
- `GeneralMolecularSamplingDiagnosticsTest` exercises a bounded, three-centre,
  four-electron general molecular fixture. It locks all ESS, weight, local-energy
  tail, ordering-correlation, and deterministic-replay results bit-for-bit.
- `WaterMoleculeStep3InfrastructureTest` continues to exercise the exact
  10-electron H2O optimizer path that originally exposed the covariance defect.

## Coverage

Targeted JaCoCo 0.8.13 run, with the three numerical test classes:

- `GeneralMolecularSamplingDiagnostics`: 100% lines, 529/539 instructions,
  28/30 branches.
- `GeneralMolecularMatrixFreeSrOptimizer`: 100% lines, 456/477 instructions,
  17/24 branches.

JaCoCo reported a Java-26 instrumentation warning for a JDK-internal CLDR class;
the Prometheus classes were instrumented and reported. The raw report checksum
was `7b5cbb347d378691b1339251490e2773fe3dd4250e300eff357a6675977b6ae5`.

## Mutation analysis

PIT 1.25.3 with `pitest-junit5-plugin` 1.2.3:

- 159 mutations generated;
- 121 killed;
- mutation score: 76%;
- test strength: 77%;
- mutated-class line coverage: 14/14 (100%);
- sampling diagnostics: 71/74 killed (95.9%);
- optimizer class: 50/85 killed (58.8%).

The covariance regression killed loop-bound, increment, and covariance-arithmetic
mutations in the statistics path. Surviving optimizer mutations are retained as
an explicit limitation: the targeted suite is strong on the diagnosed covariance
mathematics but does not prove every optimizer control/formatting path. The raw
PIT report checksum was
`641fe1eeb8840535e42083d46418dc80827105a75f368403f604e64100a8a8c5`.

Commands and plugin versions are permanently available through the Maven
`numerical-quality-analysis` profile.

The final complete Prometheus regression run executed 295 tests with zero
failures, zero errors, and zero skips.
