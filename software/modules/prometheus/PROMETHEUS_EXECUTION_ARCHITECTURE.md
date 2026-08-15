# Execution architecture

Planning occurs before execution. A frozen `CalculationSpecification` includes scientific purpose, molecule and geometry, charge/multiplicity, protocol, constraints, calculation type, required outputs, acceptance gates, dataset role, cost estimate, and checksum.

Executors receive that frozen specification and may only materialize it for their engine. They cannot change its scientific content. PySCF, ORCA, Psi4, Gaussian, AmberTools, and OpenMM adapters currently fail closed unless their real integration is configured; no skeleton emits invented results.

Expensive work requires an explicit costed plan and authorization. Raw inputs, outputs, environment, convergence state, coordinates, and checksums are archived before evidence registration.
