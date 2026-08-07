# Ligand preparation comparison: hephaestus vs Meeko

Validation of the hephaestus ligand-preparation path against Meeko
(`mk_prepare_ligand.py`) reference preparations that **we generate
locally** — no database and no external artifact store involved. Run
with:

```bash
java -cp <daedalus classes + deps> \
    totah.lab.daedalus.cli.DaedalusCli compare-ligand-prep --count 100
```

(see `software/modules/daedalus/readme.md` for the exact classpath
invocation; latest report from 2026-08-07).

## Reference oracle (built by us, locally)

`prepare-reference.py` (this directory) builds the oracle:

1. Recursively collects the SDFs under `/Users/yazan/artifacts/ligands`
   (44 files: naphthalene, SAM, PubChem CID 439155, DCMB, and 20+20
   DiffDock conformers of DCMB under `dcmb/`). Only single-molecule
   V2000 SDFs with a conformer are kept.
2. Adds explicit hydrogens with RDKit (`AddHs`) where the SDF lacks
   them (the 40 DiffDock poses; recorded as `h_added` in the manifest)
   and strips carried-over property blocks (our SDF reader is strict
   about them).
3. Runs the locally installed Meeko `mk_prepare_ligand.py` on each SDF.

Output: `/Users/yazan/artifacts/ligands/meeko-prepared/` with
`manifest.tsv` (id, name, h_added), `<id>.sdf`, and
`<id>.meeko.pdbqt` per ligand. Environment used:
`python3 -m venv /tmp/meeko-venv`, `pip install meeko rdkit==2025.9.6
scipy numpy gemmi` (meeko 0.7.1; rdkit pinned because 2026.03 removed
`rdDetermineBonds`, which meeko imports).

Sampling is deterministic: the first `--count` manifest rows sorted by
id. The set is small and low-diversity (4 unique molecules, one of
them in 40 conformations) — it exercises the full pipeline but says
little about coverage; add SDFs to `/Users/yazan/artifacts/ligands`
and re-run `prepare-reference.py` to grow it.

## Methodology

- Each source SDF is re-prepared with hephaestus defaults (SDF bond
  table topology, hydrogenation, Gasteiger charges, AD4 typing,
  torsion tree, validated PDBQT export).
- Both PDBQTs are parsed (`PdbqtLigandReader`: name, coordinates,
  charge, AD4 type, TORSDOF, BRANCH records).
- **Atom alignment is by coordinates**, not file order: both writers
  emit atoms in torsion-tree order and the trees differ, but both
  preserve the SDF coordinates at 3-decimal precision, so each heavy
  atom's position is its identity. Each Meeko heavy atom is matched to
  the nearest hephaestus heavy atom within 0.02 Å (greedy, unique).
  Hydrogens are excluded (Meeko merges non-polar Hs; hephaestus keeps
  the explicit SDF hydrogens).
- Metrics per ligand: heavy-atom counts and matched count, total
  charge delta, mean per-atom |Δcharge| (matched pairs), AD4 type
  agreement fraction, TORSDOF delta, rotatable-bond identity sets
  (BRANCH bonds compared by endpoint coordinates, order-independent),
  max coordinate delta (sanity: rounding, ≤ ~0.001 Å).
- Known limitation recorded, not fatal: the hephaestus SDF path
  requires explicit hydrogens and refuses to add them. The oracle
  build hydrogenates upstream with RDKit, so no reference ligand
  currently hits it.

## Headline results (local oracle, 2026-08-07)

All 44 reference ligands, `report-20260807-105506.csv`: 44 compared,
0 failed. Atom counts match everywhere; every heavy atom matched by
coordinates; max coordinate delta mean 0.0008 Å (rounding only).

| Metric | Value |
|---|---|
| AD4 type agreement (mean / median / min) | 1.0000 / 1.0000 / 1.0000 |
| Charge mean-abs-delta (mean / median) | 0.0301 / 0.0287 |
| TORSDOF delta (mean / median) | 1.14 / 1 |
| Rotor-set mismatches (same count, different bonds) | 43 of 44 |

The TORSDOF deltas and rotor-set mismatches are the documented
terminal-rotation rule difference below: Meeko counts terminal
rotations such as C–OH (ribose hydroxyls drive SAM's delta of 4),
hephaestus requires heavy-degree ≥ 2 on both ends.

## Root causes found and fixed (2026-08-07)

The oracle for these fixes was Meeko's own rulebook:
`meeko/data/params/ad4_types.json` (ordered SMARTS rules; later rules
override), plus RDKit's hybridization and aromaticity perception,
which Meeko inherits. (The corpus used at the time was an earlier,
larger set of Meeko preparations harvested from the chemflow3 artifact
store; that source is superseded by the local oracle above and the
chemflow3 database is no longer used.)

1. **AD4 typing — aromaticity perception (the dominant cluster,
   ~96% of mismatched atoms).** Hephaestus typed carbons aromatic only
   when the SDF carried bond type 4; Kekulé-encoded SDFs (alternating
   single/double bonds) got `C` instead of `A`. Fixed by adding Kekulé
   aromaticity perception (`KekuleAromaticity`, hephaestus
   ligand.topology): smallest 5/6-cycles through each bond, Hückel
   counting per cycle (2 e⁻ per in-cycle double, 2 per N/O/S
   lone-pair donor, fusion atoms only top up to the sextet), validated
   offline against RDKit's `GetIsAromatic()`. Deliberate divergence:
   rings >6 (tropylium, azulene) are not perceived.
2. **Nitrogen donor/acceptor.** Now Meeko's exact rules: default `NA`;
   `N` only for charged N (`[#7+]`) or neutral X3v3 N attached to an
   aromatic atom (aniline/pyrrole), a carbonyl-type carbon
   (`[#6X3v4]`), or a triazene N.
3. **Oxygen.** Meeko types every oxygen `OA` (no override rules);
   hephaestus' `formalCharge > 0 → O` branch was removed.
4. **Sulfur.** `SA` only for aliphatic two-connected sulfur (Meeko's
   `[SX2]` does not match aromatic S — thiophene stays `S`);
   everything else is `S`. (This also changed disulfides from S to
   SA, matching Meeko.)
5. **Hydrogen.** `HD` for H on N/O/F/P/S (F and P parents added).
6. **Gasteiger charges — hybridization of lone-pair heteroatoms.**
   The remaining charge offset was not iteration math (damping,
   denominators and parameters match RDKit's
   `GasteigerCharges.cpp`/`GasteigerParams.h`: DAMP 0.5, scale 0.5,
   receiver-side chi(+1) denominators) but **hybridization
   assignment**: RDKit assigns sp2 to single-bonded O/N adjacent to an
   sp2 center (anisole O: a=17.07 vs sp3 a=14.18; amide N likewise).
   hephaestus assigned sp3 unless the atom itself was aromatic or
   double-bonded. Fixed in `GasteigerModel.isSp2`.

## Rotatable-bond rule difference (documented, not changed)

Meeko keeps terminal rotations when the terminal side moves more than
one atom after non-polar-H merging — e.g. aryl–CF3 (C+3F move) and
aryl–OH (polar H stays) are rotatable; plain methyls are not (merged
Hs move with the carbon, so nothing moves). Hephaestus requires
heavy-degree ≥ 2 on both ends, excluding all terminal groups. Result:
identical TORSDOF in ~55% of ligands, Meeko higher in the rest; the
rotor identity sets differ on most ligands (per-ligand rotor columns
are in the CSV). Changing the production rule is a docking-semantics
decision and was deliberately left out of scope.

## Remaining known divergences

- Residual charge delta (~0.03 e mean) after the hybridization fix:
  sulfur `so`/`so2` parameter modes and small iteration-schedule
  differences (6 vs 12 iterations) are possible further causes.
- The terminal-rotation rule difference above (torsions).
- Rings >6 are not aromaticity-perceived (deliberate, see above).

## Artifacts

- `prepare-reference.py` — rebuilds the local Meeko oracle (see
  above).
- `report-<timestamp>.csv` — per-ligand rows.
- `work-<timestamp>/` — the hephaestus-written PDBQTs of the run.
- `ad4-diagnosis.md` — generated mismatch grouping with chemical
  context (regenerate with `diagnose-ad4-typing`).
- `report-20260807-094749.csv` — archived run over the earlier
  chemflow3-harvested corpus (2,250 of 2,399 pairs compared; type
  agreement mean 0.9959 / median 1.0000). Superseded by the local
  oracle; kept for the diversity of its corpus.
