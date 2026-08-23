# TSL-RSH MIN01 stationary-point recovery pilot

Status: `PREPARED_NOT_EXECUTED`.

This A100 package is limited to four MIN01 branches. It reconstructs the total
PBE+D3 Hessian using the corrected PySCF axis permutation, selects the signed
`-180.4366091924035 cm^-1` mode, and applies mass-weighted displacements of
`q = ±0.05` and `±0.025 sqrt(amu) Angstrom`.

The runner stops before optimization unless the symmetric total-energy second
difference is negative at both scales. It clusters converged endpoints using the
existing locked geometry/energy identity and calculates component-complete Hessians
only for unique endpoints.

## Colab/A100 use

1. Upload and extract `TSL_RSH_MIN01_STATIONARY_POINT_RECOVERY_PILOT.zip`.
2. Run `python run_min01_stationary_point_pilot_a100.py` from the extracted directory.
3. Download the complete directory, including `results/SHA256SUMS`.

Do not rerun into an existing `results` directory. The runner fails closed instead of
overwriting prior evidence.

Publication-quality persistence is mandatory: every step stores geometry, separate
PBE/D3/total energies and gradients, force, SCF diagnostics, execution receipt and
checksums. Each qualified endpoint additionally stores separate electronic, dispersion
and total Hessians, signed eigenvalues/frequencies, normal modes and rigid-body
projection diagnostics.

This package does not authorize MIN02, MIN04, fitting, or any campaign expansion.
