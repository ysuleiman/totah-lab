# Matrix-Free SR Design Review

The explicit implementation costs `O(NP^2 + P^3)` time and `O(P^2)` covariance
storage. The streamed operator costs `O(NP)` per Krylov application and stores
`O(P)` vectors, `O(BP)` transient state output, and optionally
`O(sum block_size^2)` preconditioner data. It trades repeated deterministic
passes for bounded memory.

Conventional PCG is mathematically appropriate because `(S+lambda I)` is
symmetric positive definite for positive lambda and every preregistered
preconditioner is fixed and symmetric positive definite. Flexible PCG would add
complexity without a changing preconditioner. Low-rank/Kronecker methods remain
review candidates for future architectures with independently demonstrated
factorization; they are not justified by this 20-parameter linear geometry
encoder.

The one-evaluation principle is applied within each streamed pass: one state
bundle produces the complete O-vector. The initial scientific-statistics pass
also produces energy, RHS, means, diagonal, and the small block moments.
Operator applications intentionally revisit the immutable sample stream rather
than retain `N x P`; those revisits are visible and counted, not hidden
recomputation.
