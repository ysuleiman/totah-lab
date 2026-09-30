# STO-3G phosphorus, sulfur and chlorine provenance

The resource `src/main/resources/totah/lab/aether/basis/sto-3g-spcl.csv` contains
45 primitive rows copied without coefficient/exponent modifications from PySCF
**2.10.0**, `gto/basis/sto-3g.dat`. The accompanying `.manifest.json` retains the
upstream header, versioned source URL, original-file SHA-256, generator SHA-256,
resource SHA-256, convention and shell order.

Resource SHA-256:
`db8e15f19d4b5a859b52dcc9c4b62392f510e10df7f396d8c7ab97df27b29586`.

Regenerate explicitly with `scripts/generate_spcl_basis.py` in the pinned PySCF
validation environment. The generator is not part of Maven's lifecycle. Basis
numbers are not adjusted to fit Aether output. The runtime loader checks the
compiled expected SHA-256 before parsing and preserves checked IOException for
missing, corrupt or invalid resource input.

Each element contributes nine Cartesian functions in upstream shell order:

```text
1s, 2s, 3s, 2px, 2py, 2pz, 3px, 3py, 3pz
```

There are five shells, each containing three primitives. The loader derives the
shell list from the validated data, so no element-specific normalization or integral
formula is introduced. All terms multiply normalized primitives and each contraction
uses the existing independent normalization. The existing s/p Hermite recurrence,
Boys functions, packed ERIs, J/K contractions and RHF equations are unchanged.
No d functions are required by these STO-3G definitions and none are enabled.

C/N/O data and hydrogen data remain byte-identical. The shared loader now parses
both C/N/O and P/S/Cl resources through one private helper. The only new public API
member is `Sto3gBasis.SPCL_RESOURCE_SHA256`; existing constructors and method
signatures are retained. QuantumSystem's allowed nuclear charges expand to
1, 6, 7, 8, 15, 16 and 17. Existing charge/electron-count logic already computes
`sum(Z)-molecularCharge`, so no change to that arithmetic or the closed-shell
occupation policy was necessary.

Basis identities already bind all exponents, coefficients, centers, angular labels
and AO ordering. New-element SCF receipts additionally identify the expanded basis
scope and this resource hash. Prior-element receipt protocols are unchanged.
Numerical status and scientific validation remain separate: every runtime result
is SCREENING_ONLY, including algorithmically converged states.

The explicit geometry generator `scripts/spcl_benchmarks.py` defines idealized
fixed structures in Angstrom and exports bohr using the recorded conversion factor
1.8897261254578281. It performs no optimization, protein extraction or chemistry
perception. Atom coordinates, order, charges and chemical-model labels are frozen
in the reference CSV/manifest. The fixtures cover thiol, thioether, trialkyl
sulfonium, aryl chloride and amine chemistry; none is a binding calculation.
