# Canonical-domain ML exporter audit

The exporter is an adapter, not a molecular model. Protein graph semantics are owned by Gaia; experimental annotations by Athena; ligand topology/chemistry by Hephaestus and Gaia; cavity geometry by Gaia `Pocket`/`AlphaSphereSet`.

| Required channel | Canonical source | Audit result | Export behavior |
|---|---|---|---|
| Residue nodes and source coordinates | `gaia.structure.Structure`, `gaia.graph.ResidueNode` | Present | Reference source residue identity and serialize canonical node fields |
| Sequence adjacency | `ResidueGraph.sequenceEdges()` | Present, with connectivity provenance | Serialize unchanged |
| Spatial/contact adjacency | `ResidueGraph.withinDistance()` / `atomProximities()` | Present, cutoff-parameterized | Serialize query parameters and returned canonical edges |
| Distances | `ResidueDistance`, `AtomPairDistance` | Present: minimum, CA, centroid, establishing atom pairs | Preserve independently |
| Site-local induced view | `ResidueGraph.view(Collection<ResidueId>)` | Present, immutable and deterministic | Athena site membership supplies selected IDs |
| Residue chemistry | `ResidueNode.chemistry()` | Present with availability status | Preserve categories and status |
| Experimental contacts/site grammar | Athena evidence/grammar and persisted canonical site tables | Present | Annotation layer keyed by `ResidueId`/UniProt mapping; no Gaia mutation |
| Structural variability | Athena `StructuralVariabilityEvidence` | Present with evaluation states | Annotation layer, null plus reason when unavailable |
| Ligand/cofactor atom topology | Hephaestus `LigandTopology`, Gaia `ChemicalBond`, deposited Gaia atoms | Present for CCD bonds, charge and aromaticity | Serialize canonical atoms/bonds; SAM/SAH/SFG remain distinct |
| Donor/acceptor state | No general canonical ligand-domain field | Genuinely missing | `NOT_AVAILABLE_CANONICAL_MODEL`; do not infer in exporter |
| Cavity/void geometry | Gaia `Pocket.alphaSphereSet()` / `AlphaSphere` | Present | Serialize every sphere center/radius and pocket membership |
| Cavity topology | No canonical alpha-sphere adjacency object | Derivable, not a missing molecular abstraction | Adapter derives radius-aware surface-gap edges; raw spheres remain authoritative |
| Solvent accessibility | No canonical corpus-wide observation | Missing data | Explicit unevaluated state |

Gaia's 134 module tests pass, including all ten `ResidueGraph` tests. No general structural API extension is required for this export. A future reusable alpha-sphere adjacency abstraction should be added to Gaia only if a second non-ML consumer needs identical topology; the first exporter preserves raw spheres and declares its derived edge rule.
