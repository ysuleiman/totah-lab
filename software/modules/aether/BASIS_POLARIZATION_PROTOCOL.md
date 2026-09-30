# Milestone 13 basis and polarization protocol

All calculations are `SCREENING_ONLY`. `STO-3G DFT IS NOT A PRODUCTION INTERACTION-ENERGY METHOD`. Neither the STO-3G nor def2-SVP interaction results constitute final binding energies. This milestone adds no functional, dispersion correction, or integration with other modules.

## Basis source and representation

`Def2SvpBasis` loads the pinned `def2-svp.csv` resource for H, C, N, O, P, S and Cl. `generate_def2_basis.py` exports the installed PySCF 2.10.0 definitions directly, without coefficient transcription. The accompanying JSON retains each element's ordered shells, angular momenta, primitive exponents and coefficients, the upstream file hash, resource SHA-256, source URL and normalization convention. Loading fails on a resource hash mismatch.

The basis is the Weigend–Ahlrichs def2-SVP family ([original paper](https://pubs.rsc.org/en/content/articlelanding/2005/cp/b508541a)). The PySCF upstream file identifies its EMSL Basis Set Exchange origin. `PinnedBasisData` shares resource parsing and shell construction with STO-3G; hydrogen's frozen STO-3G implementation remains in place.

Aether uses **Cartesian**, individually unit-normalized contracted AOs. Shell order follows PySCF, with descending x power and then descending y power:

- s: s
- p: px, py, pz
- d: dxx, dxy, dxz, dyy, dyz, dzz

H has 5 AOs; C/N/O have 15; P/S/Cl have 19. Cartesian def2-SVP has six d functions, whereas the commonly used spherical representation has five. All external calculations here explicitly set `cart=True`; they must not be compared to spherical def2-SVP energies.

For powers (lx, ly, lz), primitive normalization is

`Ns(alpha) * (4 alpha)^((lx+ly+lz)/2) / sqrt((2lx-1)!! (2ly-1)!! (2lz-1)!!)`, with `(-1)!! = 1`.

The existing contraction normalization is then applied. The frozen s/p arithmetic is retained exactly. No element-specific integral equation is introduced.

## Reference AO coordinates

[PySCF/libcint Cartesian normalization](https://pyscf.org/_modules/pyscf/gto/mole.html) normalizes s and p but uses a shared angular factor for d. Therefore `cart=True` alone does not establish equal matrix coordinates. Define `Dii = 1/sqrt(Slibcint_ii)` and `chi_Aether = chi_libcint D`.

Reference transformations are explicit:

- `S_A = D S_lib D`, likewise T, V, Hcore, F and Vxc.
- `ERI_A(i,j,k,l) = Di Dj Dk Dl ERI_lib(i,j,k,l)`.
- `AO_A = AO_lib D`.
- `P_A = D^-1 P_lib D^-1`, `C_A = D^-1 C_lib`.

Energies and the represented electronic state are invariant to this change of AO coordinates. The integral manifest freezes AO labels and normalization factors. Every practical lower-triangular S/T/V entry is compared. ERI selection includes every shell's self quartet plus a deterministic set of mixed-center shell quartets; every Cartesian entry within those selected quartets is checked.

## Recurrences and numerical domain

Overlap, kinetic, attraction and ERI continue to use the existing McMurchie–Davidson recurrence. The angular enum and AO monomial evaluation now admit degree two. Boys orders 0 through 8 suffice for dddd quartets. F0 remains unchanged; the positive-term series below T=16 and upward recurrence at/above T=16 remain unchanged for all historical orders. Higher orders are independently checked with 80-digit incomplete-gamma values and quadrature at zero, near-zero, small, moderate, transition and large arguments.

The d-shell tests include the normalized Cartesian rotation matrix, including the sqrt(3) factors between diagonal and mixed d components. They check AO, overlap and fourth-rank ERI transformation, not merely an s-type geometric rotation.

## Provenance and existing behavior

`BasisFamily` supplies explicit native/ghost basis selection. Existing interaction and ghost overloads retain STO-3G defaults. Native SCF scope checks compare the complete multiset of pinned AO definitions at the system nuclei; matching dimensions alone are insufficient. Mixed, incomplete and incompatible bases fail closed. Ghost calculations retain real-first ordering, zero ghost charge/electrons, and donor element/coordinate identity.

New-basis protocols bind the def2-SVP resource hash and normalized Cartesian representation. Existing STO-3G protocol branches and numerical operations are retained. Observational `BasisPerformance` data do not enter scientific receipt hashes.

## Frozen SCF and grid

RHF and LDA reuse the existing SCF loop, core guess, DIIS protocol, eigensolver, doubled density, energy equations, energy threshold 1e-12 hartree, density threshold 1e-10 and cap 128. LDA remains exchange plus the original PZ81 correlation.

The primary grid remains the exact Milestone-12 120 radial × 590 angular grid: mapped Gauss–Legendre radial rule, Lebedev angular rule, three Becke cubic partition steps, no radius adjustment and no pruning. The separate 40×110, 80×302, 120×590 and 160×974 ladder measures quadrature sensitivity. Agreement with PySCF on the same finite grid does not remove quadrature error. Rotation residuals are measured separately.

## Performance interpretation

`BasisPerformance` records integral setup (S/Hcore/ERI, including canonical identity construction), AO-grid evaluation, basis size, AO primitive contributions and packed ERI counts. Existing result counters retain J/K, XC, eigensolve and total times. Primitive Gaussian counts count contributions to Cartesian AOs, so shared radial primitives appear once per angular component. `numericalArrayBytes` counts packed ERIs, AO values and grid coordinate/weight arrays; it excludes object overhead and temporary identity strings. Benchmark heap estimates sum the JVM heap-pool high-water marks and are upper estimates, not a simultaneous live-object census. Wall times are observational and can reflect concurrent validation processes.

The original ERI receipt formatter retained a tensor-sized text buffer and additional string/UTF-8 copies. Milestone 13 streams the same canonical lines to SHA-256 to bound that temporary memory. The domain, dimension/count lines, hexadecimal values, newline bytes and hashes are unchanged. Dedicated old-format equality and historical ERI/SCF receipt tests cover this adjustment. No integral screening, approximation, caching, or parallel reduction was added.
