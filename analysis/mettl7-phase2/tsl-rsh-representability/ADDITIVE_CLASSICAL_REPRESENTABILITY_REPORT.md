# Additive classical representability report

The tested model is the frozen AmberTools26/GAFF2/RESP baseline plus 28 local
additive intramolecular corrections: two harmonic bond coordinates, three
harmonic angles, and three independent n=1..3 proper Fourier torsions. Charges,
LJ, impropers, 1-4 scaling, and every unlisted parameter remained frozen. One
global energy-reference offset is a fitted nuisance parameter.

The objective was frozen before fitting and used training labels only:
0.5 times mean squared energy residual normalized by the training energy scale,
plus 0.5 times mean squared Cartesian force residual normalized by the training
force scale. No regularization or validation-driven choice was made.

The receipt-backed fit is `12be7612dfe58d7259b7e8be66a8e516ad7800eef7cd1b7c0a73816ae2c303cd`.
All artifact checksums and the receipt were verified before the 11 validation
labels were opened once. Reconstructed training predictions agree with the
persisted predictions to maximum absolute difference 5.821e-10 against a
declared deterministic tolerance of 1e-9. Stress-test metrics remain separate.

## Results

| Metric | Train | Validation | Stress test |
|---|---:|---:|---:|
| Energy RMS, kcal/mol | 3.703232 | 12.934837 | 17.168243 |
| Relative-energy RMS, kcal/mol | 3.684871 | 9.977987 | 12.812921 |
| Global force-component RMS, kcal/mol/A | 13.167047 | 13.715164 | 21.081223 |
| Sulfur-local force-component RMS, kcal/mol/A | 21.062992 | 22.784244 | 19.450486 |

The combined C-S-H harmonic curvature is -72.200459
kcal/mol/radian^2; positive-curvature stability is `False`. The
validation decision is therefore **ADDITIVE_CLASSICAL_INSUFFICIENT**, with dominant residual class
**ADDITIVE_FUNCTIONAL_FORM**. This conclusion concerns only the current conformational
development domain. The historical `EXTENDED_BOUND_DOMAIN` term is retained in
provenance but is not interpreted as a thermally populated bound-state limit.

No QM, neural model, cross term, charge/LJ fit, or threshold change occurred.
