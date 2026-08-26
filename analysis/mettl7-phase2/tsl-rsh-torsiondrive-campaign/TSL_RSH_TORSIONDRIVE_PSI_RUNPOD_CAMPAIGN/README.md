# TSL-RSH torsion-drive A100 campaign

This immutable execution package defines three independent one-dimensional,
15-degree, three-seed relaxed torsion scans for CHI, PHI and PSI. It contains
72 possible authoritative grid cells. The three sealed minima initialize an
actual wavefront: only improved active cells spawn their two periodic neighbor
optimizations. A cell more than 0.05 Eh above the current global minimum is
preserved but cannot propagate. The populated-cell count is therefore observed,
not forced to 72.

The runner uses the included, checksum-locked level-5 PBE-D3(BJ)/def2-SVP
GPU derivative core. Every candidate and selected cell is persisted atomically.
An existing cell is reusable only after its complete receipt, scientific
identity, geometry identity and nested checksums verify. Invalid or partial
evidence causes a closed failure and is never overwritten.

The PHI/PSI values of every populated endpoint are persisted as the frozen
coupling diagnostic. Connectivity, chirality and target-dihedral realization
must pass before a candidate can enter the wavefront state.

Run `python test_package.py` before upload and `python run_torsiondrive_a100.py`
on the required A100 runtime. Package construction and tests execute no QM.
