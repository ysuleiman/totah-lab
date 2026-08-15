# Post-delivery sampling architecture research summary

This research did not alter the H2O delivery path.

## Exact Slater/DPP feasibility

For the current Prometheus state

`Psi(R)=D_alpha(R_alpha) D_beta(R_beta) exp(J(R))`,

each squared spin determinant defines a continuous projection DPP after
orthonormalization with its orbital overlap matrix. If an exact draw from the
product Slater distribution `q_Slater` is available, independence Metropolis
has the exact acceptance ratio

`min(1, exp(2[J(R')-J(R)]))`.

The determinant factors cancel exactly provided proposal and target use the
same determinants, geometry, orbital parameters, spin ordering, and no
backflow/multideterminant modification. The unresolved engineering/scientific
blocker is exact spatial sampling of the continuous DPP conditional densities
for Prometheus's multicenter nonorthogonal orbitals on unbounded R3. The DPP
algebra is established; a practical exact spatial sampler is not.

Classification:
`PROMETHEUS_CONTINUOUS_SLATER_DPP_PROPOSAL_MATHEMATICALLY_VALID_BUT_SPATIAL_SAMPLER_UNRESOLVED`.

Primary theory: Hough et al. (2006), DOI `10.1214/154957806000000078`;
continuous-DPP simulation analysis by Lavancier et al., DOI
`10.1007/s11222-023-10272-w`.

## Other proposal families

- Flow or transport independence proposals remain exact only with an MH
  correction; they may reduce but do not guarantee elimination of burn-in or
  autocorrelation.
- HMC can preserve the exact target with symplectic MH correction but requires
  multiple wavefunction-gradient evaluations and is not yet established as a
  production real-space Coulomb-molecule solution for this architecture.
- Backflow and many-electron orbitals improve ansatz capacity but are not direct
  samplers.
- Autoregressive fermionic sampling is established in discrete Fock/lattice
  spaces, not as an immediately transferable continuous all-electron molecular
  method.
- Directly sampleable equivariant fermionic flows are promising frontier work,
  not a validated molecular production architecture.

## Co-design hypothesis

The defensible version separates physical and transport parameters:

- optimize wavefunction parameters for variational energy;
- optimize an exact-MH-corrected proposal/transport using ESS per cost,
  autocorrelation, transport, and metric diagnostics.

Adding ESS or autocorrelation directly to the physical wavefunction objective
generally changes the selected quantum state and is not gauge-neutral. Exact
normalization, phase, or occupied-orbital rotations do not change the Born
distribution and therefore cannot remove its intrinsic sampling geometry.

Long-term hypothesis: an exactly sampleable DPP base plus equivariant invertible
transport could co-design antisymmetry and sampling. This remains a new
Prometheus research program and is not a sampler patch for Step 3.
