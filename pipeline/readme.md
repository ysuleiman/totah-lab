Charge Assignment Library — User Manual
What this is
A small Java library for assigning partial atomic charges to molecules. It provides two charge models and several linear solvers, wrapped in a pluggable interface.
No external dependencies. Pure Java.
Algorithms
QEq (Rappé & Goddard, 1991)
Solves a constrained linear system to minimize an energy function of atomic charges. Uses atomic electronegativities, chemical hardnesses, and screened Coulomb integrals between atom pairs.
Accuracy: Higher than empirical iterative methods. Same math as Open Babel's obabel -p qeq.
Cost: One dense matrix solve (small systems) or sparse iterative solve (large systems).
Best for: Production docking, force field prep, any case where charge accuracy matters.
Gasteiger-Marsili
Iterative electronegativity equalization with empirical damping. Runs a fixed number of iterations (default 6).
Accuracy: Approximate. Depends on initial guess and iteration count.
Cost: O(iterations × atoms). No matrix allocation.
Best for: Quick screening, prototyping, or when memory is tight.
Quick Start
1. Basic QEq (automatic solver selection)
   java
   import totah.lab.math.charges.models.QEqModel;
   import totah.lab.math.linear.solvers.HybridSolver;
   import totah.lab.pipeline.stage.ChargeAssignmentStage;

Stage stage = new ChargeAssignmentStage(
new QEqModel(new HybridSolver(2000))
);
Systems ≤ 2000 atoms use dense direct solve. Larger systems automatically switch to sparse PCG with Jacobi preconditioning.
2. QEq with Block-Jacobi (recommended for proteins)
   java
   import totah.lab.math.linear.Preconditioner;

// Build per-residue atom index blocks
List<int[]> blocks = new ArrayList<>();
int idx = 0;
for (Residue res : residues) {
int n = res.getAtoms().size();
int[] block = new int[n];
for (int i = 0; i < n; i++) block[i] = idx++;
blocks.add(block);
}

Stage stage = new ChargeAssignmentStage(
new QEqModel(new HybridSolver(2000, Preconditioner.blockJacobi(blocks)))
);
Block-Jacobi captures intra-residue Coulomb coupling. Typically 5-10× fewer iterations than simple Jacobi on proteins.
3. Gasteiger (fast fallback)
   java
   import totah.lab.math.charges.models.GasteigerModel;

Stage stage = new ChargeAssignmentStage(new GasteigerModel(6));
4. Custom solver configuration
   java
   // Always dense direct (small ligands, deterministic)
   new QEqModel(new DenseDirectSolver());

// Always sparse iterative with tight tolerance
new QEqModel(new SparsePCGSolver(
new IncompleteCholeskyPreconditioner(H, n),
1e-8,   // residual threshold
5000    // max iterations
));

// Large system, loose tolerance, fast setup
new QEqModel(new SparsePCGSolver(
new JacobiPreconditioner(diag),
1e-4,
1000
));
Solver Selection Guide
Table
Situation	Use	Why
Ligand (< 100 atoms)	HybridSolver(2000) or DenseDirectSolver	Dense is fast enough, no iterative overhead
Small protein/peptide (< 2000 atoms)	HybridSolver(2000)	Automatic, simple
Large protein (> 2000 atoms)	HybridSolver with Block-Jacobi	Better convergence than Jacobi, cheaper setup than IC0
Very large / ill-conditioned	SparsePCGSolver with IC0	Fewest iterations, but O(nnz) setup cost
Memory constrained	GasteigerModel	No matrix allocation
Preconditioner Comparison
Table
Preconditioner	Setup	Apply	Iterations (typical)	Notes
Jacobi	O(N)	O(N)	200–2000	Cheap, weak
Block-Jacobi	O(Σ block²)	O(N × blockSize)	50–200	Needs residue blocks
Incomplete Cholesky (IC0)	O(nnz)	O(nnz)	20–80	Best convergence, setup cost
Architecture
plain
ChargeModel (interface)
├── QEqModel ──→ LinearSolver ──→ DenseDirectSolver
│                         └──→ SparsePCGSolver ──→ Preconditioner
│                                                    ├── Jacobi
│                                                    ├── BlockJacobi
│                                                    └── IncompleteCholesky
│
└── GasteigerModel (no solver needed)

ChargeAssignmentStage ──→ ChargeModel
↑
ResidueChargeSystem (adapts your Residue/Atom types to ChargeSystem)

Parameters
QEq uses published values for H, Li, C, N, O, F, Na, Si, P, S, Cl, K, Br, Rb, I, Cs. Missing elements fall back to carbon parameters with a console warning.
Gasteiger uses the original Gasteiger-Marsili parameter set (H, C, N, O, S, P, F, Cl, Br, I). Missing elements fall back to carbon.
Performance Notes
Coulomb cutoff: QEq skips long-range Coulomb integral computation beyond a distance threshold derived from the smallest Gaussian basis exponent. Default threshold: 1e-10. Lower = more sparsity, faster but less accurate tail.
Distance units: Internal math uses Bohr radii. Input coordinates are expected in Ångströms (converted automatically).
Charge normalization: All models enforce the target total formal charge exactly via constraint projection.
Limitations
QEq parameters are hardcoded for 16 elements. Others default to carbon.
No periodic boundary conditions (non-periodic molecules only).
No implicit solvent or environmental screening beyond the Gaussian Coulomb integral.
Gasteiger initial charges are heuristic (valence rules). Unusual bonding may give poor results.
File List
Table
File	Purpose
ChargeModel.java	Interface for all charge models
ChargeSystem.java	Geometry/topology DTO
QEqModel.java	QEq charge equilibration
GasteigerModel.java	Iterative Gasteiger-Marsili
SparseMatrix.java	HashMap-based sparse matrix
Preconditioner.java	Preconditioner interface + factories
*Preconditioner.java	Jacobi, Block-Jacobi, IC0 implementations
LinearSolver.java	Solver interface
DenseDirectSolver.java	Gaussian elimination with partial pivoting
SparsePCGSolver.java	Preconditioned conjugate gradient
HybridSolver.java	Auto-switch direct/iterative
ChargeAssignmentStage.java	Pipeline adapter for your Residue types
