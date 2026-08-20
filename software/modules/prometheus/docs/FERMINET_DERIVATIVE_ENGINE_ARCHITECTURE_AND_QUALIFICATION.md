# FermiNet Derivative Engine Architecture and Qualification

## 1. Purpose

FermiNet derivative evaluation is an explicit, pluggable runtime capability. It is used by SWCT nuclear-force evaluation and is available to other consumers that require spatial, nuclear, or directional derivatives. Selection is made through one canonical `FermiNetDerivativeConfiguration` path.

## 2. Historical failure

The original `FermiNetSpatialJet` was an immutable scalar jet carrying a value, a `double[30]` electronic gradient, and an electronic Laplacian. Directional differentiation was represented recursively, and arithmetic such as add, multiply, and affine created new jet objects and arrays.

For the canonical H2O SWCT workload, each sample required one ordinary traversal and nine independent directional traversals. A 16-sample JFR profile measured approximately 591.3 GB of transient allocation, 745 garbage collections, and about 96.7% of Java CPU in spatial-jet add/multiply/affine operations. This was an execution-architecture defect, not an inherent cost of FermiNet or SWCT.

## 3. Root cause

Two independent defects dominated runtime:

1. Scalar jet arithmetic created an object/array allocation explosion.
2. SWCT performed nine independent full directional traversals plus one ordinary traversal for every sample.

## 4. Canonical derivative architecture

```text
FermiNetDerivativeConfiguration
    |
    v
FermiNetDerivativeEngine
    |-- REFERENCE_JET
    |-- BATCHED_FORWARD
    `-- future ADJOINT
```

The configuration selects an engine and bounded sample parallelism. The engine owns derivative execution. Physics and estimator equations remain outside the engine.

## 5. REFERENCE_JET

`REFERENCE_JET` retains the validated scalar-jet calculation as the slow scientific oracle. It must not be removed merely because it is slow. Every future derivative implementation must be checked against it.

## 6. BATCHED_FORWARD

`BATCHED_FORWARD` is the production implementation. It performs one shared primal network traversal and carries all requested forward tangent lanes through that traversal. Canonical H2O SWCT carries nine nuclear Cartesian directions simultaneously.

The backend uses preallocated primitive workspaces, contiguous batched derivative storage, destination/in-place arithmetic, and workspace reuse. The primal spatial quantities from the same traversal are reused for local energy. It therefore avoids a separate ordinary traversal and does not repeat the full network for each force component.

After the single-sample kernel was qualified, bounded sample parallelism was added. A worker owns a complete sample, computes all nine force components, and writes only that sample column. Final statistics are accumulated deterministically in canonical sample order.

## 7. Scientific invariants

Derivative-engine selection must not change the FermiNet wavefunction or parameters, Hamiltonian or local-energy equations, SWCT equations, sampling or stochastic reconfiguration, determinant semantics, derivative definitions, coordinate ordering, or force-component ordering.

## 8. Parity evidence

`REFERENCE_JET` and `BATCHED_FORWARD` were compared for sign and `log|Psi|`, electronic log-gradient, `Laplacian/Psi`, nuclear log-gradient, directional log and Laplacian derivatives, local energy, all nine raw SWCT force arrays, component means and variances, chain-aware standard errors, and tail diagnostics.

The maximum raw SWCT difference on the production N=1024 H2O dataset was `1.5143e-11 Ha/bohr`. JAX runtime parity and deterministic VMC coordinate/local-energy parity also passed without relaxed tolerances.

## 9. Performance qualification

| Workload | Reference | Batched | Speedup |
|---|---:|---:|---:|
| 16 samples, single thread | 52.89 s | 13.73 s | 3.85x |
| 64 samples, single thread | 205.70 s | 49.98 s | 4.12x |
| 64 samples, six workers | 205.70 s | 10.24 s | 20.09x |
| 1024 samples, six workers | approximately 34 min projected old path | 215 s (3.58 min) | approximately 9.5x |

The N=1024 run used 1024 samples (64 chains x 16 retained), one shared primal traversal with nine tangent lanes per sample, and produced zero nonfinite force samples.

JFR evidence:

- original 16-sample transient allocation: approximately 591.3 GB;
- optimized 16-sample allocation: approximately 517 MB (greater than 1000x reduction);
- N=1024 allocation: approximately 9.67 GB total, 9.44 MB/sample;
- N=1024 garbage collections: 19;
- N=1024 GC pause: 452 ms;
- average JVM utilization: 47.1% of machine capacity, approximately 5.65 logical cores;
- peak utilization: approximately six logical cores.

The frozen production classification is `PRODUCTION_ACCEPTABLE`.

## 10. Production defaults

The canonical force driver defaults are `derivative engine = BATCHED_FORWARD` and `force parallelism = 6`. The reference oracle is selected explicitly with `--derivative-engine REFERENCE_JET --force-parallelism 1`.

## 11. Pluggability contract

Derivative consumers such as SWCT depend on `FermiNetDerivativeEngine`, never on a concrete jet backend. New implementations must plug into the same configuration and factory path. Estimator-specific derivative execution paths are prohibited.

## 12. Future ADJOINT backend

`ADJOINT` is a possible future backend for larger molecules where carrying `3M` forward tangent lanes becomes inefficient. It is not implemented, is not currently required, and blocks no production work. It should be pursued only if larger-molecule scaling evidence demonstrates a real need.

## 13. Regression requirements

Any future derivative-engine change must preserve `REFERENCE_JET` parity, official JAX/runtime parity, deterministic VMC coordinates and local energies, and raw SWCT sample parity.

## 14. Failure history and lesson

Derivative correctness and derivative execution strategy are separate concerns. The scalar jet was valuable for establishing correctness, but inappropriate as the production execution backend. Future scientific functionality must be profiled before promotion into a production path.
