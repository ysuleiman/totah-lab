# HISTORICAL AC-ZV IMPLEMENTATION RETIRED

`totah.lab.prometheus.variational.force.AssarafCaffarelZvForceEstimator`
(Qian et al. 2022, JCP 157, 164104, "printed Eq. 11") has been removed from
live production source. It must not remain as a live alternative now that it
has been demonstrated to be mathematically defective. The scientific evidence
it produced is preserved in
`analysis/prometheus/java-neural-nuclear-force-estimator-study/` and in git
history.

## Reason 1: sign-inconsistent contraction

The implemented force followed the printed Eq. 11 final expression
`F_A = F_A,aa - div Q_A . grad psi/psi` (nn - G). That printed expression is
sign-inconsistent with the paper's own Eq. 6, from which AC-ZVZB is derived:
`F_A - (H - E_L)(Q_A psi)/psi = F_A,aa + F_A,ae - (F_A,ae - G) = F_A,aa + G`,
because `(H - E_L)(Q psi)/psi = -1/2 laplacian(Q) - div Q . grad psi/psi` and
`-1/2 laplacian(Q_A) = F_A,ae` exactly.

Correct Eq.-6-consistent formulation (now canonical):

`F_A^ZV = F_A,nn + div Q_A . grad log|Psi|`  (nn + G)

Cross-check: the existing, validated ZVZB implementation computes its
zero-variance part as `bareForce - operatorRatio = nn + G`, i.e. with the
consistent sign, and is accurate on the frozen H2 panel; the retired ZV class
with the printed sign was not.

## Reason 2: auxiliary omitted the nuclear charge

The historical auxiliary was `Q_A = sum_i (r_i - R_A)/|r_i - R_A|`. Qian
Eqs. 9-10 define `Q_A = Z_A sum_i (r_i - R_A)/|r_i - R_A|`. The omission is
invisible for hydrogen only.

## Why the tests missed both defects

- H2 has Z = 1, so the missing Z_A prefactor changes nothing.
- The ZV fixture used a constant wavefunction, whose electron gradient is
  identically zero; the contraction term vanishes and no constant-wavefunction
  test can distinguish nn - G from nn + G.

## Numerical evidence (frozen H2 panel, 72000 samples, R = 1.0 / 1.4 / 3.0 bohr)

- historical (nn - G) signed errors: +1.3605, +1.0525, +0.3443 hartree/bohr
- corrected (nn + G) signed errors:  -0.0849, -0.0504, -0.0003 hartree/bohr
- panel references: 0.3621964426997232, 0.009120324827245340,
  -0.06087135209218764 hartree/bohr

The retired panel values are locked as constants in
`AcZvFermiNetForceEstimatorTest.frozenH2PanelReportsHistoricalAndConsistentFormulations`
alongside the corrected regression values.

## Canonical implementation

The only live AC-ZV implementation is the canonical FermiNet pipeline:

```
FermiNetH2oForceQualificationDriver
  -> FermiNetNuclearForcePipeline
    -> NuclearForceEstimatorType.AC_ZV
      -> AcZvFermiNetForceEstimator
        -> NuclearForceResult
```

Shared support retained for the valid ZVZB estimator:
`AssarafCaffarelSupport` (terms/localEnergy/vector; the ZV-only `Moments`
accumulator was removed with the retired class) and
`AssarafCaffarelForceStatistics`.
