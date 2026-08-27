# INSTANCE_TYPING_PILOT preregistration (design only)

This is not C4 and is not authorized for execution. It uses the existing 56 QM labels only.

## Frozen chemical splits

Split `LOCAL_TYPE_17` into `CHI_C8_SIDE_S_H` and `CHI_C10_SIDE_S_H`; retain `LOCAL_TYPE_30` frozen. Split `LOCAL_TYPE_12` into the five chemically defined PHI subclasses in `PROPOSED_TORSION_SUBCLASSES.csv`. Split `LOCAL_TYPE_2` into `PSI_C2_C9`, `PSI_C6_C9`, and `PSI_C6_HC`. Keep `LOCAL_TYPE_1` and `LOCAL_TYPE_7` tied within their graph-symmetry groups.

Exactly ten subclass amplitudes descending from heterogeneous parents would be independently adjustable. All phases and periodicities remain their C1 values. `LOCAL_TYPE_1`, `LOCAL_TYPE_7`, and `LOCAL_TYPE_30` remain frozen at C1. No new Fourier periodicities are introduced.

Primary endpoint: equal-axis mean squared residual over QM <=10 kcal/mol, with the existing 56 labels. Cross-surface stop: reject any change that worsens another axis's QM<=10 RMSE beyond the already frozen numerical materiality/acceptance rules. Whole-profile, barrier, closure, topology, and 1-4 gates remain unchanged.

Every adjustable subclass inherits the exact C1 amplitude bound `[0.0, 2.0] kcal/mol`, justified only by the frozen C1 nonnegative Amber barrier convention and per-instance ceiling. Initialization is its C1 parent amplitude. The frozen C1 regularizer is retained: `0.01*((k-k_parent)/0.5)^2` per subclass, centered at the C1 parent. Bounds, priors, or groupings may not be selected using resulting performance.

Topology invariants: parent-identity reproduction when all subclasses equal their C1 parent, exactly one 1-4-defining entry per physical quartet, unchanged charges/LJ/bonds/angles/impropers/SCEE/SCNB/unrelated torsions, symmetry ties, and serialized read-back identity. This pilot changes assignments among existing n/phase forms; it adds no Fourier continuation term.

Stop conditions: any invariant failure; any unconverged minimization; non-identifiable subclass sensitivity; symmetry-tie violation; or failure of the locked thermal/cross-surface gates. Do not proceed automatically to multidimensional QM or another model.
