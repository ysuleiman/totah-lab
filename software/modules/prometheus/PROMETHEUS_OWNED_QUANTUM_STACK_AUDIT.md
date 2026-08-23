# Prometheus-owned quantum stack architecture audit

Status: `ARCHITECTURAL_DIRECTION_ADOPTED`

Scope: `prometheus`, `gaia`, and `euclid`. This is an architecture and source
audit only. It launches no QM calculation and implements no unqualified
scientific kernel.

## 1. Existing reusable abstractions

### Euclid

Euclid is the natural home for the Prometheus-owned math API because it is an
internal peer module rather than a third-party numerical implementation.

Existing reusable pieces:

- `linear.Matrix`: minimal square-matrix read/multiply contract.
- `linear.Vector`: immutable defensive-copy vector.
- `linear.SparseMatrix`: sparse row storage and matrix-vector multiply.
- `linear.LinearSolver`: linear-system solver contract.
- `SparsePCGSolver`, Jacobi, incomplete-Cholesky, and block-Jacobi
  preconditioners.
- `DenseDirectSolver` and `HybridSolver` as preliminary solver implementations.
- `RigidSuperposition`, RMSD clustering, and spatial geometry utilities.

These are useful precedents, not yet a QM-grade math layer:

- `Matrix` only represents square matrices and returns raw arrays.
- `SparseMatrix` is mutable and exposes public internal storage.
- `LinearSolver` depends on concrete `SparseMatrix`, not a `LinearOperator`.
- there is no dense matrix, symmetric matrix, tensor, sparse-block matrix,
  decomposition, eigensolver, result diagnostics, workspace, or kernel backend.
- `DenseDirectSolver.svdSolve` is a regularized normal-equation solve, not an SVD;
  it must not be treated as a robust quantum-chemistry decomposition.
- Prometheus currently has no Maven dependency on Euclid.

### Gaia

Reusable domain components include `Element`, formal charge and bond types,
`Point3D`, `Vector3D`, molecular structures, and geometry utilities. They are
nuclear/molecular domain objects. They must not become storage containers for
wavefunctions, basis functions, matrices, or electronic-state tensors.

### Prometheus

Reusable architecture already present:

- immutable `CalculationSpecification` and evidence identity/provenance;
- `QuantumExecutionRequest`, capability-based `QuantumBackend`, immutable
  `QuantumResult`, and backend-neutral scientific identity;
- `QuantumState`, differentiable state, Hamiltonian, variational functional,
  collocation, optimizer, and variational-result boundaries;
- `NumericalComputationGraph`, state identity, and explicit intermediate reuse
  policies;
- synchronous generated-evidence persistence and reuse;
- authoritative energy/gradient/Hessian/ESP readers and validation.

These are orchestration, evidence, and mathematical contracts. They do not yet
implement Gaussian electronic structure.

## 2. Missing foundational components

### Math foundation

- rectangular `Matrix`, mutable workspace and immutable value views;
- `SymmetricMatrix`, `SparseBlockMatrix`, `Tensor` and strided tensor views;
- `LinearOperator` independent of materialized storage;
- explicit row/column dimensions, layouts, strides, ownership and alias rules;
- `KernelBackend` and capability/precision descriptors;
- BLAS-like vector/matrix operations behind Prometheus interfaces;
- eigensolvers, generalized eigensolvers and linear solvers with typed outcomes;
- LU, QR, Cholesky, symmetric eigendecomposition and genuine SVD;
- convergence, condition, residual, rank and numerical-failure reporting;
- deterministic reduction policy and compensated summation where required;
- pooled workspaces and memory-budget accounting;
- profiling hooks at the Prometheus API boundary.

### Conventional electronic structure

- Gaussian basis parser/library and immutable basis provenance;
- primitive and contracted Gaussian normalization;
- shells, angular momentum ordering and spherical/Cartesian transforms;
- basis evaluation and first nuclear/spatial derivatives;
- overlap, kinetic, nuclear-attraction and electron-repulsion integrals;
- recurrence engine, shell-pair bounds and Schwarz screening;
- density-fitting auxiliary basis, metric and three-index contractions;
- restricted/unrestricted SCF, occupations and electronic-state handling;
- DIIS and fallback convergence strategies;
- core, Coulomb, exchange and Fock builders;
- Kohn-Sham grid generation, atom partitioning and pruning;
- density/gradient evaluation on grids;
- PBE exchange/correlation energy and potential;
- D3(BJ) energy and analytic derivatives;
- analytic nuclear gradients sharing the converged energy computation;
- checkpoint/restart and finite-difference qualification machinery.

### Neural/variational

- tensor parameters, neural layers and activations;
- quantum-specific reverse/forward differentiation tape;
- first and second spatial derivatives/Laplacians;
- determinant values, stable updates and determinant derivatives;
- antisymmetric state construction and spin-sector enforcement;
- correlation/Jastrow factors;
- local-energy evaluation, VMC sampling and estimators;
- optimizers with reproducible state and failure diagnostics;
- normalization, boundary-condition and uncertainty validation.

## 3. Current third-party numerical dependencies

No production numerical library such as EJML, Commons Math, ND4J, netlib-java,
BLAS binding, PyTorch, TensorFlow, or JAX is currently a dependency of these three
modules.

| Module | Production dependency | Current role |
|---|---|---|
| `prometheus` | `gaia` | internal molecular domain |
| `prometheus` | Jackson databind + JSR310 | artifact serialization/parsing |
| `prometheus` | Lombok, provided | annotation dependency; no numerical role |
| `gaia` | Lombok | annotation dependency; no numerical role |
| `euclid` | none | pure Java production math today |
| tests | JUnit, AssertJ, Mockito, Spring test | testing only |

Commons Math exists elsewhere in the repository (`athena` and a viewer app), but
does not currently enter Prometheus/Gaia/Euclid scientific code.

## 4. Current external-library leakage

### Prohibited production path

Legacy classes under `prometheus.execution` directly name PySCF, launch a Python
process, and parse PySCF-shaped JSON. Campaign runners also embed PySCF software
identity and script paths. These are incompatible with the adopted owned-stack
production direction.

They must be treated as quarantined historical-evidence compatibility code:

- they may continue reading and validating authoritative historical artifacts;
- they must not be registered with the new `execution.quantum` selector;
- they must not launch new calculations;
- new owned-QM code must not import them;
- later removal requires a separately approved public-API/data-migration step.

### Infrastructure leakage to contain

Jackson appears in stores, readers, report generators, campaign runners and some
execution classes. Jackson is acceptable behind artifact adapters, but QM domain,
math, SCF, integral, DFT, gradient, neural and variational packages must not expose
Jackson types or annotations.

Raw `double[]`, `double[][]` and `List<List<Double>>` occur in historical readers,
report math and Euclid internals. New scientific code must use Prometheus/Euclid
owned vector/matrix/tensor contracts so layout, ownership and backend selection
remain controllable.

## 5. Proposed Prometheus-owned math layer

Evolve Euclid using a parallel API rather than breaking its current public API:

```text
totah.lab.euclid.math
  ScalarPrecision
  Vector / MutableVector / VectorView
  Matrix / MutableMatrix / MatrixView
  SymmetricMatrix
  Tensor / TensorView
  SparseBlockMatrix
  LinearOperator
  MemoryLayout / Ownership / Workspace

totah.lab.euclid.kernel
  KernelBackend
  KernelCapabilities
  KernelSelector
  PureJavaKernelBackend
  VectorApiKernelBackend
  EjmlKernelBackend          (optional adapter)
  NativeBlasKernelBackend    (optional adapter)

totah.lab.euclid.solver
  LinearSolver / LinearSolveRequest / LinearSolveResult
  EigenSolver / EigenSolveRequest / EigenSolveResult
  Decomposition / DecompositionResult
```

Rules:

1. QM/neural code imports only owned Euclid interfaces.
2. Third-party types never appear in those interfaces or scientific models.
3. Kernel selection is capability-based, injected and immutable; no global enum registry.
4. Layout, precision, determinism, workspace and memory budget are explicit.
5. Results report convergence, residuals, conditioning and provenance.
6. Start with correct pure-Java kernels and profile real workloads.
7. Add Vector API/EJML/native adapters only for measured bottlenecks.

## 6. Proposed QM computation DAG

```text
MolecularSystem + Geometry + ElectronicState + LockedProtocol
  -> BasisDefinition
  -> ShellLayout
  -> ShellPairBounds
  -> OneElectronIntegrals
  -> {ERI shell blocks OR DF metric + three-index blocks}
  -> InitialDensity
  -> SCF iteration DAG
       Density
        -> Coulomb/Exchange or DF contractions
        -> XC grid blocks
             -> basis values + first derivatives
             -> rho / grad(rho)
             -> PBE epsilon + potential intermediates
        -> Fock
        -> DIIS subspace
        -> generalized eigensolve
        -> occupations
        -> next Density
  -> ConvergedScfState
  -> D3BJ pair/coordination intermediates
  -> EnergyComponents
       -> TotalEnergy
       -> AnalyticGradient
       -> Dipole / ESP / other requested observables
```

Energy and gradient are sibling consumers of the same converged state. The
gradient path consumes shared basis derivatives, density, Fock components, grid
partition/basis/PBE intermediates, and D3(BJ) intermediates rather than starting a
second calculation.

Each node key contains the complete validity state: geometry, atom order, basis
and auxiliary basis, electronic state, method, numerical grid, screening and
convergence settings, parameter versions and implementation/kernel qualification.

## 7. Candidate intermediate reuse policy

| Intermediate | Initial policy | Reason |
|---|---|---|
| parsed/normalized basis and shell layout | `MANDATORY_REUSE` | immutable and consumed broadly |
| shell-pair metadata and screening bounds | `MANDATORY_REUSE` | cheap memory, repeated integral use |
| one-electron integrals | `MANDATORY_REUSE` | reused throughout SCF and gradients |
| overlap orthogonalization/decomposition | `MANDATORY_REUSE` | shared across SCF iterations |
| converged density, orbitals, occupations | `MANDATORY_REUSE` | authoritative state for all observables |
| DIIS error/history within iteration window | `MANDATORY_REUSE` | algorithmically required, bounded history |
| XC grid definition/partition/weights | `MANDATORY_REUSE` | energy/gradient consistency |
| D3(BJ) coordination/pair data | `MANDATORY_REUSE` | shared by energy and derivative |
| basis values/derivatives by grid block | `CACHE_IF_BENEFICIAL` | high reuse but potentially large |
| DF metric factorization | `MANDATORY_REUSE` | costly and broadly reused |
| DF three-index blocks | `CACHE_IF_BENEFICIAL` | large; block retention depends on memory |
| ERI shell-quartet recurrence intermediates | `CACHE_IF_BENEFICIAL` | reuse varies by Fock/gradient strategy |
| full four-index ERI tensor | `RECOMPUTE_IF_CHEAPER` by default | usually unacceptable memory footprint |
| transient Fock contraction blocks | `RECOMPUTE_IF_CHEAPER` or bounded cache | short-lived and iteration-specific |
| neural forward activations | `MANDATORY_REUSE` during derivative evaluation | one tape feeds value/gradient/Laplacian |
| determinant factorization/inverse | `MANDATORY_REUSE` per valid configuration/state | shared by ratios and derivatives |
| VMC local-energy subterms | `CACHE_IF_BENEFICIAL` | reuse depends on estimator batch |

Policies are defaults, not permanent guesses. Profiling records actual compute time,
reuse count, allocation, retained bytes, eviction pressure and recomputation cost.
The planner selects the minimum measured total cost subject to the scientific
validity and memory budget.

## Implementation order

1. Freeze this ownership boundary and quarantine legacy execution.
2. Add Euclid v2 math contracts, shape/layout/ownership rules and pure-Java reference kernels.
3. Add solver result diagnostics and qualify basic linear algebra.
4. Implement Gaussian basis/shell metadata and normalization with published unit tests.
5. Build one-electron integrals and derivative checks.
6. Add shell-pair screening and two-electron/DF paths incrementally.
7. Implement HF/SCF/DIIS before PBE.
8. Add XC grid and PBE, then D3(BJ).
9. Design analytic energy and gradients through the same DAG.
10. Qualify the complete owned PBE-D3(BJ)/def2-SVP backend against authoritative evidence.
11. Only after conventional QM qualification, implement neural/autodiff/VMC kernels behind the same owned math API.

The 36-target campaign remains stopped until an owned backend is scientifically qualified.
# 2026-08-20 conventional-DFT campaign decision

The conclusion that Prometheus contains no suitable conventional Java DFT engine
remains true. It no longer blocks the already-frozen TSL-RSH dataset: an explicit,
narrow architecture decision permits PySCF 2.14.0 plus simple-dftd3 1.5.0 as a
numerical worker under a complete Java-owned contract. This is not a claim that
PySCF is Prometheus-owned scientific control, and it does not broaden the backend
policy beyond this campaign.
