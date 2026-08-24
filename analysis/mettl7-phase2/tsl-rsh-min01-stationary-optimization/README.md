# MIN01 stationary-point optimization and qualification

This frozen A100 package is authorized by the immutable, checksum-verified level-5→6 closure
result. It optimizes only historical MIN01 at the qualified level-5 PBE-D3(BJ)/def2-SVP protocol.
Every optimization calculation constructs a GPU4PySCF gradient object explicitly, sets
`grid_response=True`, verifies the setting before and after execution, and atomically persists
geometry, component energies, component gradients, force, SCF diagnostics and checksums.

After convergence, nine preregistered Cartesian components spanning C10, S26 and H56 are audited
against symmetric energy differences at `h=0.001` and `0.0005` bohr. A failed audit stops before
the Hessian. A passing audit permits one component-complete electronic+D3 total Hessian, signed
frequency/mode construction using the exact persisted isotope-average mass vector, and corrected
stationary-point classification.

No MIN02/MIN04 calculation, model fit, GPU60 recomputation or CURVATURE76 recomputation exists.
This package is prepared and checksummed but has not been executed.
