# Ligand preparation comparison: hephaestus vs Meeko

Validation of the hephaestus ligand-preparation path against the
existing Meeko (`mk_prepare_ligand.py`) reference preparations in
chemflow3, run with:

```bash
PGPASSWORD=… java -cp <daedalus classes + deps> \
    totah.lab.daedalus.cli.DaedalusCli compare-ligand-prep --count 100
```

(see `software/modules/daedalus/readme.md` for the exact classpath
invocation; report written 2026-08-07).

## Sampling

Deterministic: compounds having a `prepared_ligand` PDBQT artifact with
`artifact_metadata->>'command' LIKE '%mk_prepare_ligand.py'`, joined to
its source SDF artifact via `artifact_metadata->>'source_artifact_id'`
(exact pairing — a compound can have several SDF inputs), ordered by
compound id then prepared-artifact id, LIMIT 100. 2,399 such pairs
exist in chemflow3. Because sampling is per artifact pair, a compound
can appear more than once (the worst-mismatch list below shows this).
Read-only DB access; hephaestus outputs went to `work-*/` here.

## Methodology

- Each source SDF is re-prepared with hephaestus defaults (SDF bond
  table topology, hydrogenation, Gasteiger charges, AD4 typing,
  torsion tree, validated PDBQT export).
- Both PDBQTs are parsed (`PdbqtLigandReader`: name, coordinates,
  charge, AD4 type, TORSDOF).
- **Atom alignment is by coordinates**, not file order: both writers
  emit atoms in torsion-tree order and the trees differ, but both
  preserve the SDF coordinates at 3-decimal precision, so each heavy
  atom's position is its identity. Each Meeko heavy atom is matched to
  the nearest hephaestus heavy atom within 0.02 Å (greedy, unique).
  Hydrogens are excluded (Meeko merges non-polar Hs; hephaestus keeps
  the explicit SDF hydrogens).
- Metrics per ligand: heavy-atom counts and matched count, total
  charge delta, mean per-atom |Δcharge| (matched pairs), AD4 type
  agreement fraction, TORSDOF delta, max coordinate delta (sanity:
  should equal rounding, ≤ ~0.001 Å).
- Known limitation recorded, not fatal: the hephaestus SDF path
  requires explicit hydrogens and refuses to add them.

## Headline results (count = 100)

| Metric | Value |
|---|---|
| Sampled / compared OK | 100 / 99 |
| Failed | 1 (`missing-hydrogens`) |
| Heavy-atom count mismatches | 2 |
| Heavy atoms unmatched by coordinates | 4 (of ~2,900) |
| Max coordinate delta | mean 0.0010 Å (pure rounding — alignment sound) |
| Charge mean-abs-delta | mean 0.0573 e, median 0.0543 e, min 0.0341 e |
| AD4 type agreement | mean 64.2%, median 66.7%, min 30.0% |
| TORSDOF delta | mean 0.58, median 0 |

## Findings

1. **Coordinates and connectivity are sound**: matched atoms agree to
   rounding precision, and total charge agrees to ~0.001 e. The
   torsion tree is close (median TORSDOF delta 0, mean 0.58).
2. **Gasteiger charges diverge moderately and systematically** (mean
   per-atom |Δ| ≈ 0.057 e). Both are "Gasteiger" but the
   implementations differ (iteration/damping details, hydrogen
   bookkeeping); the spread is consistent across the sample, not a few
   outliers.
3. **AD4 typing is the weakest dimension** (mean 64% exact-match).
   Inspection of the worst cases (top of the CSV; e.g. type agreement
   0.30–0.44) shows divergence concentrated on aromatic carbons (C vs
   A), hydrogen-bond acceptor/donor distinctions (N vs NA, O vs OA),
   and sulfur typing. Likely causes: Meeko types from RDKit
   hybridization/ring aromaticity while hephaestus types from its own
   bond-table/geometry rules.
4. The single hard failure is the documented explicit-hydrogen
   limitation; the 2 count mismatches and 4 unmatched atoms are
   fragment/protonation differences worth individual inspection (see
   CSV).

Next candidates if closer parity is wanted: align the AD4 typing rules
for aromatic C (A vs C) and acceptor/donor typing with Meeko's
`atom_typer`, and diff the Gasteiger parameter iteration against
Meeko's `compute_gasteiger_charges`.

## Artifacts

- `report-20260807-064728.csv` — per-ligand rows (100).
- `work-20260807-064728/` — the 99 hephaestus-written PDBQTs compared
  in this run.
