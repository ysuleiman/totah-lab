# Ligand Capability Contract

The ligand pipeline is generic over CCD components, but it is not universally
capable of preparing every CCD entry. `LigandCapabilityMatrixTest` records the
supported boundary as machine-readable cases with three expected outcomes:

- `PREPARES_SUCCESSFULLY`: the ordinary free-ligand pipeline completes.
- `CLASSIFICATION_EXCLUSION`: cleanup identifies a component that should not be
  selected as an ordinary docking ligand.
- `EXPLICIT_REJECTION`: preparation stops with `UnsupportedLigandException`
  and a stable `LigandUnsupportedReason`.

## Current reference categories

The offline panel covers:

- a connected two-carbon ligand that completes PDBQT preparation;
- deposited `4E1J` glycerol chemistry with missing hydrogens and rotatable
  bonds;
- standard amino-acid, water, and monoatomic-ion classification exclusions;
- incomplete CCD definitions;
- missing and extra deposited heavy atoms;
- invalid supported valence;
- unusable CCD hydrogen-reference geometry;
- charge-model parameter rejection;
- disconnected molecular graphs.

Real online `QWE` acceptance remains covered separately by the gated
`CcdLigandGraphBuilderTest`. This keeps the default suite deterministic while
still testing the production BioJava provider, cache, complete preparation
pipeline, and emitted PDBQT when online CCD testing is enabled.

## Stable preparation failures

`LigandPreparer` translates expected capability failures at its public boundary:

| Pipeline boundary | Reason |
| --- | --- |
| CCD missing atoms or bonds | `INCOMPLETE_CCD` |
| Deposited structure lacks CCD heavy atoms | `MISSING_HEAVY_ATOMS` |
| Deposited structure contains unmatched heavy atoms | `EXTRA_HEAVY_ATOMS` |
| Completed CCD bonding exceeds supported valence | `INVALID_VALENCE` |
| Missing hydrogen cannot be placed from CCD coordinates | `UNUSABLE_HYDROGEN_REFERENCE_GEOMETRY` |
| Active charge model lacks element parameters | `UNSUPPORTED_ELEMENT_FOR_CHARGE` |
| AD4 assignment rejects an element/type | `UNSUPPORTED_AD4_TYPE` |
| One connected torsion tree cannot be built | `DISCONNECTED_GRAPH` |

Internal invariant failures remain programming errors and are not mislabeled as
unsupported chemistry.

## Limitations

- The panel is a capability contract, not evidence that all components in a
  chemical family work.
- Classification answers what a component is; cleanup policy answers what to
  do with it. A component can be chemically preparable but excluded from
  ligand selection.
- The default valence validator currently supports H, C, N, O, S, and halogens.
  Phosphorus is accepted by the charge and AD4 layers but is rejected earlier
  by valence validation. ATP, ADP, nucleotides, and other phosphate-containing
  ligands are therefore not yet supported end to end.
- Metal-containing ligands generally fail valence or charge support and are not
  ordinary-ligand successes.
- `MULTI_COMPONENT` and `COVALENTLY_ATTACHED` are reserved reason codes until
  structural selection supplies explicit multi-residue and receptor-link
  context.
- Protonation/tautomer selection, missing-heavy-atom reconstruction, covalent
  ligands, and multi-residue ligands remain out of scope.
