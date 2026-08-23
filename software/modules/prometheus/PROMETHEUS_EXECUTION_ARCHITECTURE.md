# Execution architecture

Planning occurs before execution. A frozen `CalculationSpecification` includes scientific purpose, molecule and geometry, charge/multiplicity, protocol, constraints, calculation type, required outputs, acceptance gates, dataset role, cost estimate, and checksum.

Executors receive that frozen specification and may only materialize it for their engine. They cannot change its scientific content. PySCF, ORCA, Psi4, Gaussian, AmberTools, and OpenMM adapters currently fail closed unless their real integration is configured; no skeleton emits invented results.

Expensive work requires an explicit costed plan and authorization. Raw inputs, outputs, environment, convergence state, coordinates, and checksums are archived before evidence registration.
# Conventional-DFT numerical worker boundary

For the frozen TSL-RSH force-cloud campaign only, Prometheus may invoke PySCF as
a numerical library through `TslRshForceCloudQmRunner`. Prometheus/Java owns the
immutable calculation specification, identity, frozen-input checks, acceptance,
registration, and dataset freeze. The worker is intentionally incapable of
selecting geometries, checkpoints, guesses, grids, auxiliary bases, dispersion
parameters, convergence recovery, or acceptance. A failure under the frozen
protocol is returned as a failure; it cannot trigger a scientifically different
retry. Development and sealed-holdout roles remain explicit in the frozen result
manifest, and downstream fitting is forbidden from launching QM.
