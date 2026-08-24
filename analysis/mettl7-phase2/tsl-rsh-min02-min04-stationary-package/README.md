# MIN02 + MIN04 stationary-point qualification package

This package executes only MIN02 and MIN04 using the exact sealed MIN01 level-5 stationary-point
protocol core. It verifies the core and authoritative starting-geometry SHA-256 identities before
execution. Each structure has an independent optimization, endpoint derivative-audit gate, and
component-complete Hessian/frequency qualification path. Failure of one endpoint audit stops that
structure before its Hessian without relaxing any gate.

After both endpoints qualify, the wrapper compares sealed MIN01, MIN02 and MIN04 with the existing
complete-linkage basin identity conjunction from `STAGE1_COMPLETION_GATES_LOCKED.md`. Convergence
lineage is retained even when endpoints are deduplicated.

In Colab, unzip the archive and run:

```bash
python analysis/mettl7-phase2/tsl-rsh-min02-min04-stationary-package/run_min02_min04_stationary_a100.py
```

The ZIP is preparation-only and contains no precomputed MIN02/MIN04 result directory.
