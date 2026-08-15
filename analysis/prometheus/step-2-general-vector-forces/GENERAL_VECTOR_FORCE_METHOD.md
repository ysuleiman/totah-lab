# General Cartesian Nuclear-Force Method

## Mathematical definition

Prometheus evaluates every ordered nuclear Cartesian component with the differential space-warp coordinate transformation (SWCT) estimator. For nucleus `A`, electron `i`, and Cartesian axis `c`,

`omega_A(r_i) = |r_i-R_A|^-4 / sum_B |r_i-R_B|^-4`

and the total directional derivative moves `R_Ac` with unit velocity while moving electron coordinate `r_ic` with velocity `omega_A(r_i)`. The fused graph also carries a bare direction that moves only `R_Ac`. The logarithmic Jacobian derivative is `sum_i partial(omega_A)/partial(r_ic)`.

For local energy `E_L`, wavefunction `Psi`, and total-SWCT derivative `D_Ac`, the implementation accumulates the qualified Sorella–Capriotti form already used by Prometheus:

`O_Ac = D_Ac(Psi)/Psi + 1/2 D_Ac(log J)`

`B_Ac = -D_Ac(E_L) - 2 E_L O_Ac`

`F_Ac = <B_Ac> + 2 <E_L><O_Ac>`.

The local-energy derivative includes the directional derivative of the electronic Laplacian and all electron–nuclear, electron–electron, and nuclear–nuclear Coulomb terms. Force is emitted in hartree/bohr; the evidence gradient is its exact negative.

Scientific lineage remains the frozen Prometheus differential-SWCT lineage: Filippi–Umrigar normalized space warp and Sorella–Capriotti analytic force formulation. No new force theory was introduced.

## Fused directional AD

One `DirectionalSecondOrderJet` graph carries `6*Nn` tangents: total-SWCT and bare-nuclear directions for each of the `3*Nn` components. Each sample therefore requires one state traversal, one local-energy evaluation, and one fused directional-AD pass. The independent central-difference oracle requires `6*Nn` transformed state traversals per sample.

The implementation keeps four bounded two-dimensional weight/derivative concepts and directional jets; it does not construct or retain a dense third-order derivative tensor or a complete sample population.

Dominant live directional state scales as `O(Nn*Ne)` per scalar graph node. Slater elimination remains `O(Ns^3)` scalar operations, each carrying the fused directional/electronic-second-order payload. Streaming memory remains independent of total sample count.

## Validation boundary

The central finite-difference SWCT implementation is a reference oracle only. It is not selectable as a production force estimator. H, He, and H2 remain implementation fixtures; Step 2 makes no new molecular accuracy claim.
