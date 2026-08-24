# MIN01 stationary-point deterministic recovery

This recovery consumes the immutable A100 result archive produced before the rigid-body
reporting crash. It verifies the outer archive, every nested checksum manifest, and the exact
executed runner identity. It then recomputes only deterministic Hessian post-processing using
the persisted total Hessian, endpoint geometry and frozen isotope-average mass vector.

The PySCF `_get_TR` contract is treated correctly as a tuple of six flattened `3N` vectors,
which are dimension-checked and stacked into a `6 × 3N` matrix. The recovery module contains
no SCF, gradient, finite-difference, Hessian-production, optimization or D3 execution call.

`recovered-results/PUBLICATION_RECEIPT.json` binds the immutable source archive, exact executed
package and runner, recovery protocol/code, recovered artifact checksum manifest and final
qualification result. The recovery reads back the receipt and re-verifies every recovered
artifact before returning success. The original archive, including `FAILURE.json`, is retained
byte-for-byte under `immutable-evidence/`.
