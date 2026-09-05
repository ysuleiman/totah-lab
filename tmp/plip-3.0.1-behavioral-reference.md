# PLIP 3.0.1 Behavioral Reference (for independent Athena reimplementation)

Source: pharmai/plip @ master, `__version__ = '3.0.1'`. Files: `plip/structure/detection.py` (D), `plip/structure/preparation.py` (P), `plip/basic/config.py` (C), `plip/basic/supplemental.py` (S). Line citations as D:nn / P:nn / C:nn / S:nn.

PLIP is the external behavioral reference and validation oracle only. Athena implementations must be independent — no copied or mechanically ported code.

## Shared constants (config.py:44-65)

`MIN_DIST=0.5`, `BS_DIST=7.5`, `HYDROPH_DIST_MAX=4.0`, `HBOND_DIST_MAX=4.1`, `HBOND_DON_ANGLE_MIN=100`, `PISTACK_DIST_MAX=5.5`, `PISTACK_ANG_DEV=30`, `PISTACK_OFFSET_MAX=2.0`, `PICATION_DIST_MAX=6.0`, `SALTBRIDGE_DIST_MAX=5.5`, `HALOGEN_DIST_MAX=4.0`, `HALOGEN_ACC_ANGLE=120`, `HALOGEN_DON_ANGLE=165`, `HALOGEN_ANGLE_DEV=30`, `AROMATIC_PLANARITY=5.0`.

All distance/angle bounds are **exclusive** (`MIN_DIST < x < MAX`) unless noted. `vecangle` = arccos in degrees, returns exactly 0.0 for identical vectors (S:105-117). `projection(normal, plane_point, target)` flips the normal toward the target before projecting (S:137-153).

## 0. Detection pipeline order and precedence (PLInteraction.__init__, P:791-839)

1. Salt bridges first (both directions) (P:802-803).
2. H-bonds raw both directions, then `refine_hbonds_*` consumes salt bridges (P:805-813) → salt bridges suppress H-bonds.
3. Pi-stacking (P:815).
4. Pi-cation raw, then `refine_pication` consumes pi-stacking (P:817-821) → stacking suppresses HIS pi-cations.
5. Hydrophobic raw, then `refine_hydrophobic` consumes pi-stacking (P:823-825) → stacking suppresses hydrophobic ring contacts.
6. Halogen bonds, no refinement (P:826).
7. Water bridges (consumes refined H-bonds) — NOT reimplemented.
8. Metal complexation — NOT reimplemented.

`filter_contacts` (D:14-40): no-op unless intra-chain mode; standard protein–ligand runs unaffected.

Binding-site preselection, two passes (P:1632-1668): cutoff = ligand max-dist-to-centroid + 7.5; pass 1 residue-centroid coarse filter (strict <), pass 2 keep bs atom iff distance to any ligand atom ≤ 7.5 (inclusive).

Hydrogens: default `AddPolarHydrogens()` (polar only) on the whole complex AFTER ligand extraction (P:1553-1568). Ligand donors are re-perceived on the protonated complex (P:1273-1293). Donor perception requires explicit H neighbors; pre-protonated PDBQT input ≈ `--nohydro`.

## 1. Hydrophobic contacts

- Candidates (P:553-565): carbon atoms whose bonded neighbors ⊆ {C, H}; independently for binding site and ligand; altconf atoms excluded.
- Test (D:44-64): all BS×ligand pairs; keep iff 0.5 < d < 4.0 (exclusive).
- Refinement (P:913-991), in order:
  1. Pi-stacking exclusion: drop pair if both atoms are members of the two rings of any detected pi-stack.
  2. Per-(ligand atom, residue-number) keep closest contact (first-seen wins ties; key uses residue number only, no chain).
  3. Small-molecule mode: group by BS atom; singletons kept; for BS atoms with >1 contact, build bonded-pair tuples among contacting ligand atoms, merge into connected clusters ("hydrophobic patches", cluster_doubles S:156-194), keep only the single closest contact per cluster.
- Edge cases: a contacting ligand atom with no bonded neighbor among other contacting ligand atoms is silently dropped in the cluster branch; cluster representative may not be the globally closest contact; no directionality.

## 2. Hydrogen bonds

- Acceptors (P:567-578): non-halogen atoms passing OpenBabel `IsHbondAcceptor()`.
- Donors (P:580-597): `IsHbondDonor()` atoms paired with each bonded `IsHbondDonorH()` neighbor; one (donor,H) pair per H. 'Weak' C–H donor class is perceived but never used (dead code).
- Tests (D:67-111): distance on heavy atoms only, 0.5 < d(D,A) < 4.1; A…H distance computed but never gated. Angle vertex at the hydrogen: vecangle(H→D, H→A) > 100 (strict).
- Refinement (P:993-1045), both directions symmetric:
  1. Salt-bridge exclusion: drop if donor atom ∈ ligand-side atoms AND acceptor atom ∈ protein-side atoms of any salt bridge (both bridge directions).
  2. One H-bond per donor heavy atom: keep candidate with largest angle-at-H; first-seen wins ties. No acceptor-side dedup.
- Multiple H on one donor → best-angled H wins after refinement.

## 3. Pi-stacking

- Rings (P:599-636): SSSR rings of size 5-6; qualify if OpenBabel-aromatic OR residue name ∈ {TYR,TRP,HIS,PHE} OR planar (all pairwise per-atom neighbor-cross normals within 5° or ≥175°, S:300-315). Normal from probe atoms 0→2 and 4→0 cross product (P:623-625); center = centroid.
- Tests (D:114-151): 0.5 < d(centers) < 5.5; a = min(θ, 180−θ) between normals; offset = min over both mutual center-into-plane projections (D:126-129). Parallel 'P': 0 < a < 30 AND offset < 2.0. T-shaped 'T': 60 < a < 120 (i.e. 90±30 folded to [0,90]) AND offset < 2.0 — offset enforced for T-shaped too. All strict. Angles in [30,60] yield nothing.
- Dedup: none beyond one record per ring pair.
- Edge cases: exactly identical normals → vecangle 0.0 → strict `0 < a` rejects a numerically perfect parallel stack; arbitrary residue pick if a ring spanned residues.

## 4. Pi-cation

- Charged groups: protein positive = ARG/HIS/LYS all sidechain N atoms, center = centroid of those N (HIS always positive, no protonation check, P:1151). Ligand positive = quartamine (N with 4 non-H neighbors), tertamine (sp3 N ≥3 neighbors), sulfonium (S with 3 non-H neighbors), guanidine (C with exactly 3 N neighbors, one terminal); center = central atom coords (not centroid).
- Tests (D:154-203): 0.5 < d(ring center, charge center) < 6.0 AND offset < 2.0 (charge center projected into ring plane, strict). Tertamine special case: amine_normal = cross of neighbor vectors; fold angle with ring normal; keep iff folded ≤ 30.0 (exactly 30 accepted) — ring must face the N.
- Exclusion (P:1047-1067): drop pi-cation if the charged residue is HIS and the same HIS ring pi-stacks the same counterpart ring (stacking wins).
- Edge cases: tertamine branch `break`s after the first tertamine per ring (D:192) — later charge groups never tested against that ring; residue-number-only comparison (no chain).

## 5. Salt bridges

- Groups: protein + = ARG/HIS/LYS sidechain N (centroid); protein − = ASP/GLU sidechain O (centroid); DNA/RNA − = backbone P. Ligand + = quartamine/tertamine/sulfonium (central atom), guanidine (central C); ligand − = phosphate (P, neighbors all O), sulfonic acid (S + 3 O), sulfate (S + 4 O), carboxylate (C with 2 O + 1 C; contributing atoms = the 2 O; center = centroid of the two O, P:705-714).
- Test (D:206-224): 0.5 < d(center-of-charge, center-of-charge) < 5.5, strict. Never per-atom.
- Dedup: inherent — one record per (group, group) pair; protein has at most one + and one − group per residue.
- Both directions produce separate lists; results feed H-bond and (via HIS) pi-cation suppression.

## 6. Halogen bonds

- Donors (ligand only, P:1325-1339, 780-783): X ∈ {F,Cl,Br,I} bonded to exactly one carbon. Record keeps X and that C. Unidirectional (ligand donor → protein acceptor).
- Acceptors (P:1126-1137): O/N/S with exactly one bonded neighbor ∈ {C,N,P,S} (H not counted). Backbone C=O and Ser/Thr/Tyr OH qualify; ether/ester O excluded; water O cannot accept.
- Tests (D:227-256): 0.5 < d(O,X) < 4.0. Acceptor angle at O between O→Y and O→X: 90 < angle < 150 (120±30, strict). Donor angle at X between X→O and X→C: 135 < angle ≤ 180 (165±30). Strict bounds.
- Dedup: none beyond acceptor×donor product.

## Cross-cutting ambiguity flags

- vecangle returns exactly 0.0 for equal vectors → strict `0 < a` tests reject numerically perfect parallel cases.
- Residue-number-only keys (no chain) in hydrophobic/pi-cation refinement.
- Pication tertamine `break` skips remaining charge groups for that ring.
- Hydrophobic cluster branch drops isolated single-atom contacts.
- H-bond A…H distance never gated.
- HIS unconditionally positive.
