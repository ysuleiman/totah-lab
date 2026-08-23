# TSL-RSH Curvature Campaign Analysis

Immutable ingestion and execution of the analysis preregistered at `d3781170965d4fdb4b671035ced30b2fbb52448d`.
No QM or model fitting was run.

## Verification

- Archive SHA-256: `732963b8682b966539cb2eadbe55f4fca9f181611364dc788698da220ac09cbf`
- Expected/verified points: 76/76
- SCF convergence: 76/76
- Geometry/protocol/nested checksums: PASS

## Results

- Panels analyzed: 19 (six unique pairs; three anchors plus one half-scale panel)
- Common resolved energy/gradient estimates: 0
- Applicable classification counts: `{"MIXED_CURVATURE_NOT_RESOLVED": 19, "SCALE_DEPENDENT_NONLINEAR": 19}`
- Scale dependence: `True`
- Dataset-level decision: `INSUFFICIENT_SUPPORT`
- Rationale: No panel established a common nonzero energy/gradient mixed curvature under the frozen uncertainty rule.

## Conditioning limitation

Maximum six-coordinate Cartesian-gradient projection residual fraction: 0.83714578.
This diagnostic is retained point-by-point; the six monitored internal coordinates cannot represent every Cartesian force component.

Full equations, component-separated estimates, intervals, Jacobian singular values, projection residuals, and point identities are in `CURVATURE_ANALYSIS_RESULTS.json`.

## Total-curvature panel

Values use hartree divided by the product of the two named coordinate units.

| Anchor | Pair | Scale | Energy curvature | Gradient curvature | Classification |
|---|---|---:|---:|---:|---|
| MIN01 | S_C__C_S_H | 1.0 | 0.3044984074 | 0.3038524085 | SCALE_DEPENDENT_NONLINEAR |
| MIN01 | S_C__C_S_H | 0.5 | 0.3046177132 | 0.3040320053 | SCALE_DEPENDENT_NONLINEAR |
| MIN02 | S_C__C_S_H | 1.0 | 0.2505322435 | 0.2491042145 | SCALE_DEPENDENT_NONLINEAR |
| MIN04 | S_C__C_S_H | 1.0 | 0.257062235 | 0.2567157862 | SCALE_DEPENDENT_NONLINEAR |
| MIN01 | S_H__C_S_H | 1.0 | 0.04600422338 | 0.04785140029 | SCALE_DEPENDENT_NONLINEAR |
| MIN02 | S_H__C_S_H | 1.0 | 0.01658741805 | 0.01795811177 | SCALE_DEPENDENT_NONLINEAR |
| MIN04 | S_H__C_S_H | 1.0 | 0.02993913634 | 0.02931712448 | SCALE_DEPENDENT_NONLINEAR |
| MIN01 | S_C__CHI | 1.0 | -0.07562270072 | -0.07603186721 | SCALE_DEPENDENT_NONLINEAR |
| MIN02 | S_C__CHI | 1.0 | 0.01979795952 | 0.01964867115 | SCALE_DEPENDENT_NONLINEAR |
| MIN04 | S_C__CHI | 1.0 | -0.03467727581 | -0.03484618629 | SCALE_DEPENDENT_NONLINEAR |
| MIN01 | S_H__CHI | 1.0 | 0.0531705526 | 0.05243176405 | SCALE_DEPENDENT_NONLINEAR |
| MIN02 | S_H__CHI | 1.0 | -0.06281092304 | -0.06402795165 | SCALE_DEPENDENT_NONLINEAR |
| MIN04 | S_H__CHI | 1.0 | 0.05788763744 | 0.0585421267 | SCALE_DEPENDENT_NONLINEAR |
| MIN01 | C_S_H__CHI | 1.0 | -0.1825344857 | -0.1844459996 | SCALE_DEPENDENT_NONLINEAR |
| MIN02 | C_S_H__CHI | 1.0 | 0.1149942066 | 0.1123950111 | SCALE_DEPENDENT_NONLINEAR |
| MIN04 | C_S_H__CHI | 1.0 | -0.1317521208 | -0.1294389285 | SCALE_DEPENDENT_NONLINEAR |
| MIN01 | PHI__PSI | 1.0 | 0.9967875097 | 0.9842626228 | SCALE_DEPENDENT_NONLINEAR |
| MIN02 | PHI__PSI | 1.0 | 0.9119558488 | 0.9037300816 | SCALE_DEPENDENT_NONLINEAR |
| MIN04 | PHI__PSI | 1.0 | 0.7856927744 | 0.7821671026 | SCALE_DEPENDENT_NONLINEAR |

## Frozen scale comparison

- Scale 1.0: energy `0.304498407419`, gradient `0.303852408502`.
- Scale 0.5: energy `0.304617713228`, gradient `0.304032005298`.

The full/half energy intervals are incompatible: `True`; gradient intervals are incompatible: `True`.

## Mixed versus diagonal diagnostic

The dimensionless absolute mixed/geometric-diagonal ratio spans `0.024426584` to `0.71702556` (median `0.20778061`).
Because the frozen protocol defines no cutoff for this ratio and none of the energy/gradient intervals overlap, it does not uniquely establish sparse, dense, or primarily diagonal curvature.
