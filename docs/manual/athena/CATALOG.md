# Master capability catalog

Generated from [accepted ledger](../../../software/qualification/chemistry-geometry-foundation-20261005/CAPABILITY_LEDGER.json), SHA256 `0f770dcff005fc67237c854261c9b7a1755950f73cae3d399f44874198f00618`.

Disposition is copied without upgrading partial capability coverage.

| ID | Capability | Priority | Disposition | Evidence |
|---|---|---|---|---|
| P01 | SMARTS/SMIRKS and reusable atom/group queries | P0 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [b01-functional-group-20261005](../../../software/qualification/b01-functional-group-20261005/CHECKPOINT.txt), [foundation-groups-20261005](../../../software/qualification/foundation-groups-20261005/CHECKPOINT.txt), [ocl-query-b00-corrected-20261005](../../../software/qualification/ocl-query-b00-corrected-20261005/CHECKPOINT.txt) |
| P02 | Valence/aromaticity/conjugation/hybridization/ring perception | P0 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [foundation-vocabulary-20261005](../../../software/qualification/foundation-vocabulary-20261005/CHECKPOINT.txt), [aromatic-systems-20261005](../../../software/qualification/aromatic-systems-20261005/CHECKPOINT.txt) |
| P03 | Donor and acceptor features | P0 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [chemical-role-perception-20261005](../../../software/qualification/chemical-role-perception-20261005/CHECKPOINT.txt), [p03-role-closure-20261005](../../../software/qualification/p03-role-closure-20261005/CHECKPOINT.txt) |
| P04 | Positive/negative ionizable features versus formal charge | P0 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [charge-nonpolar-perception-20261005](../../../software/qualification/charge-nonpolar-perception-20261005/CHECKPOINT.txt), [charge-groups-20261005](../../../software/qualification/charge-groups-20261005/CHECKPOINT.txt) |
| P05 | Hydrophobe and lumped hydrophobe features | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [charge-nonpolar-perception-20261005](../../../software/qualification/charge-nonpolar-perception-20261005/CHECKPOINT.txt), [all-members-nonpolar-20261005](../../../software/qualification/all-members-nonpolar-20261005/CHECKPOINT.txt) |
| P06 | Aromatic pharmacophore/centroid features | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [foundation-vocabulary-20261005](../../../software/qualification/foundation-vocabulary-20261005/CHECKPOINT.txt), [continuous-geometry-20261005](../../../software/qualification/continuous-geometry-20261005/CHECKPOINT.txt), [aromatic-systems-20261005](../../../software/qualification/aromatic-systems-20261005/CHECKPOINT.txt), [p06-centroid-v2-20261005](../../../software/qualification/p06-centroid-v2-20261005/CHECKPOINT.txt) |
| P07 | Zinc-binding pharmacophore motifs | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED |  |
| P08 | Functional-group hierarchy and fragment features | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [b01-functional-group-20261005](../../../software/qualification/b01-functional-group-20261005/CHECKPOINT.txt), [foundation-groups-20261005](../../../software/qualification/foundation-groups-20261005/CHECKPOINT.txt), [foundation-vocabulary-20261005](../../../software/qualification/foundation-vocabulary-20261005/CHECKPOINT.txt) |
| P09 | Explicit/implicit hydrogens and state preparation | P0 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [b01-functional-group-20261005](../../../software/qualification/b01-functional-group-20261005/CHECKPOINT.txt), [foundation-groups-20261005](../../../software/qualification/foundation-groups-20261005/CHECKPOINT.txt), [ocl-query-b00-corrected-20261005](../../../software/qualification/ocl-query-b00-corrected-20261005/CHECKPOINT.txt) |
| I01 | Hydrophobic contact | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [ocl-query-b00-corrected-20261005](../../../software/qualification/ocl-query-b00-corrected-20261005/CHECKPOINT.txt) |
| I02 | Explicit-H directional hydrogen bond | P0 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [ocl-query-b00-corrected-20261005](../../../software/qualification/ocl-query-b00-corrected-20261005/CHECKPOINT.txt), [i02-approved-candidate-20261006](../../../software/qualification/i02-approved-candidate-20261006/CHECKPOINT.txt) |
| I03 | Implicit-H hydrogen-bond approximation | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [i03-i04-scientific-review-20261006](../../../software/qualification/i03-i04-scientific-review-20261006/CHECKPOINT.txt), [i03-a-implementation-20261006](../../../software/qualification/i03-a-implementation-20261006/CHECKPOINT.txt), [i03-s1-implementation-20261007](../../../software/qualification/i03-s1-implementation-20261007/CHECKPOINT.txt) |
| I04 | Weak C-H donor perception | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [i03-i04-scientific-review-20261006](../../../software/qualification/i03-i04-scientific-review-20261006/CHECKPOINT.txt) |
| I05 | Parallel/face-to-face pi stacking | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [ocl-query-b00-corrected-20261005](../../../software/qualification/ocl-query-b00-corrected-20261005/CHECKPOINT.txt) |
| I06 | T-shaped/edge-to-face pi stacking | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [ocl-query-b00-corrected-20261005](../../../software/qualification/ocl-query-b00-corrected-20261005/CHECKPOINT.txt) |
| I07 | Union pi-stacking wrapper | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [ocl-query-b00-corrected-20261005](../../../software/qualification/ocl-query-b00-corrected-20261005/CHECKPOINT.txt) |
| I08 | Cation-pi | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [ocl-query-b00-corrected-20261005](../../../software/qualification/ocl-query-b00-corrected-20261005/CHECKPOINT.txt) |
| I09 | Ionic/salt-bridge proximity | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [ocl-query-b00-corrected-20261005](../../../software/qualification/ocl-query-b00-corrected-20261005/CHECKPOINT.txt) |
| I10 | Halogen bond to atom acceptor | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED |  |
| I11 | Halogen bond to pi system | P2 | SCIENTIFIC_REVIEW_REQUIRED |  |
| I12 | Single-water H-bond bridge | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [water-bridge-contract-20261006](../../../software/qualification/water-bridge-contract-20261006/CONTRACT.txt), [water-bridge-contract-20261006](../../../software/qualification/water-bridge-contract-20261006/INPUT_CONTRACT.txt), [water-bridge-contract-20261006](../../../software/qualification/water-bridge-contract-20261006/ACCEPTANCE_MATRIX.json), [water-bridge-contract-20261006](../../../software/qualification/water-bridge-contract-20261006/WATER_CANDIDATE_UNIVERSE_V1.txt), [water-bridge-contract-20261006](../../../software/qualification/water-bridge-contract-20261006/APPROVAL.json), [water-bridge-implementation-20261006](../../../software/qualification/water-bridge-implementation-20261006/CHECKPOINT.txt), [water-bridge-implementation-20261006](../../../software/qualification/water-bridge-implementation-20261006/VALIDATION.json), [water-bridge-implementation-20261006](../../../software/qualification/water-bridge-implementation-20261006/MATRIX_EXECUTION.json) |
| I13 | Multi-water bridge paths | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [water-bridge-contract-20261006](../../../software/qualification/water-bridge-contract-20261006/CONTRACT.txt), [water-bridge-contract-20261006](../../../software/qualification/water-bridge-contract-20261006/INPUT_CONTRACT.txt), [water-bridge-contract-20261006](../../../software/qualification/water-bridge-contract-20261006/ACCEPTANCE_MATRIX.json), [water-bridge-contract-20261006](../../../software/qualification/water-bridge-contract-20261006/WATER_CANDIDATE_UNIVERSE_V1.txt), [water-bridge-contract-20261006](../../../software/qualification/water-bridge-contract-20261006/APPROVAL.json), [water-bridge-implementation-20261006](../../../software/qualification/water-bridge-implementation-20261006/CHECKPOINT.txt), [water-bridge-implementation-20261006](../../../software/qualification/water-bridge-implementation-20261006/VALIDATION.json), [water-bridge-implementation-20261006](../../../software/qualification/water-bridge-implementation-20261006/MATRIX_EXECUTION.json) |
| I14 | Metal-ligand pair proximity | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED |  |
| I15 | Metal coordination geometry/complex | P2 | SCIENTIFIC_REVIEW_REQUIRED |  |
| I16 | Van der Waals contact/gap | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [continuous-geometry-20261005](../../../software/qualification/continuous-geometry-20261005/CHECKPOINT.txt), [vdw-contact-i16-20261005](../../../software/qualification/vdw-contact-i16-20261005/CHECKPOINT.txt), [vdw-contact-i16-20261005](../../../software/qualification/vdw-contact-i16-20261005/REVIEWED_DOSSIER.json) |
| I17 | Steric overlaps/clashes | P0 | SCIENTIFIC_REVIEW_REQUIRED | [continuous-geometry-20261005](../../../software/qualification/continuous-geometry-20261005/CHECKPOINT.txt) |
| I18 | Pi-hydrogen / X-H-pi geometry | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [b05-mixed-geometry-contract-20261005](../../../software/qualification/b05-mixed-geometry-contract-20261005/DESIGN.txt), [b05-mixed-geometry-v3-20261005](../../../software/qualification/b05-mixed-geometry-v3-20261005/CHECKPOINT.txt), [b05-chemical-attribution-20261005](../../../software/qualification/b05-chemical-attribution-20261005/CHECKPOINT.txt) |
| I19 | Disulfide bond versus S-S proximity | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [b06-connectivity-coverage-characterization-20261005](../../../software/qualification/b06-connectivity-coverage-characterization-20261005/REVIEW_GATE.txt), [b06-source-connectivity-20261005](../../../software/qualification/b06-source-connectivity-20261005/CHECKPOINT.txt), [direct-assessment-execution-contract-20261005](../../../software/qualification/direct-assessment-execution-contract-20261005/DESIGN.txt), [direct-assessment-current-20261005](../../../software/qualification/direct-assessment-current-20261005/CHECKPOINT.txt), [b06-cysteine-backbone-20261006](../../../software/qualification/b06-cysteine-backbone-20261006/CHECKPOINT.txt) |
| I20 | Unpaired donor/acceptor/halogen features | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [event-coverage-20261006](../../../software/qualification/event-coverage-20261006/CHECKPOINT.txt), [event-reference-identity-20261006](../../../software/qualification/event-reference-identity-20261006/CHECKPOINT.txt), [foundation-event-clean-20261006](../../../software/qualification/foundation-event-clean-20261006/CHECKPOINT.txt) |
| I21 | Interaction overlap/refinement and pruning | P0 | EXPLICIT_ARCHITECTURAL_DISPOSITION |  |
| N01 | Directional roles and atom/group attribution | P0 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [n01-role-geometry-attribution-20261005](../../../software/qualification/n01-role-geometry-attribution-20261005/CHECKPOINT.txt) |
| N02 | Binary/count interaction fingerprints | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [event-coverage-20261006](../../../software/qualification/event-coverage-20261006/CHECKPOINT.txt), [event-reference-identity-20261006](../../../software/qualification/event-reference-identity-20261006/CHECKPOINT.txt), [foundation-event-clean-20261006](../../../software/qualification/foundation-event-clean-20261006/CHECKPOINT.txt) |
| N03 | Ensemble contact frequencies/probabilistic networks | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [event-coverage-20261006](../../../software/qualification/event-coverage-20261006/CHECKPOINT.txt), [event-reference-identity-20261006](../../../software/qualification/event-reference-identity-20261006/CHECKPOINT.txt), [foundation-event-clean-20261006](../../../software/qualification/foundation-event-clean-20261006/CHECKPOINT.txt) |
| N04 | Contact correlations and co-occurrence networks | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [event-coverage-20261006](../../../software/qualification/event-coverage-20261006/CHECKPOINT.txt), [event-reference-identity-20261006](../../../software/qualification/event-reference-identity-20261006/CHECKPOINT.txt), [foundation-event-clean-20261006](../../../software/qualification/foundation-event-clean-20261006/CHECKPOINT.txt) |
| N05 | Residue representation and network edge cardinality | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [system-graph-20261004](../../../software/qualification/system-graph-20261004/CHECKPOINT.txt) |
| N06 | Network degree/paths/second shell | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [system-graph-20261004](../../../software/qualification/system-graph-20261004/CHECKPOINT.txt) |
| N07 | RMSD-based ensemble clustering | P3 | EXPLICIT_ARCHITECTURAL_DISPOSITION |  |
| N08 | 2D/3D pharmacophore fingerprints and matching | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED |  |
| V01 | Valence/sanitization and nonempty graph validation | P0 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [v01-validation-characterization-20261005](../../../software/qualification/v01-validation-characterization-20261005/REVIEW_GATE.txt), [validation-dimensions-20261005](../../../software/qualification/validation-dimensions-20261005/CHECKPOINT.txt), [v02-disconnected-comparison-20261005](../../../software/qualification/v02-disconnected-comparison-20261005/REVIEW_GATE.txt), [disconnected-validation-repair-20261005](../../../software/qualification/disconnected-validation-repair-20261005/CHECKPOINT.txt) |
| V02 | Fragment/salt/solvent and neutral-charge checks | P0 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [v01-validation-characterization-20261005](../../../software/qualification/v01-validation-characterization-20261005/REVIEW_GATE.txt), [validation-dimensions-20261005](../../../software/qualification/validation-dimensions-20261005/CHECKPOINT.txt), [v02-disconnected-comparison-20261005](../../../software/qualification/v02-disconnected-comparison-20261005/REVIEW_GATE.txt), [disconnected-validation-repair-20261005](../../../software/qualification/disconnected-validation-repair-20261005/CHECKPOINT.txt) |
| V03 | Isotopes, allowed/disallowed elements and radicals | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [v03-representation-boundary-20261006](../../../software/qualification/v03-representation-boundary-20261006/CHECKPOINT.txt), [v03-explicit-radical-20261006](../../../software/qualification/v03-explicit-radical-20261006/CHECKPOINT.txt) |
| V04 | Representation/query/dummy/enhanced-stereo validation | P0 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED |  |
| V05 | Stereo syntax and authoritative stereo validation | P0 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [v01-validation-characterization-20261005](../../../software/qualification/v01-validation-characterization-20261005/REVIEW_GATE.txt), [validation-dimensions-20261005](../../../software/qualification/validation-dimensions-20261005/CHECKPOINT.txt), [v02-disconnected-comparison-20261005](../../../software/qualification/v02-disconnected-comparison-20261005/REVIEW_GATE.txt), [disconnected-validation-repair-20261005](../../../software/qualification/disconnected-validation-repair-20261005/CHECKPOINT.txt) |
| V06 | 2D layout/dimensionality checks | P3 | EXPLICIT_ARCHITECTURAL_DISPOSITION |  |
| V07 | Bond-length and bond-angle validation | P2 | REQUIRES_EXTERNAL_REFERENCE_DATA |  |
| V08 | Chirality/planarity validation in coordinates | P1 | REQUIRES_EXTERNAL_REFERENCE_DATA |  |
| V09 | Protein Ramachandran validation | P2 | REQUIRES_EXTERNAL_REFERENCE_DATA |  |
| V10 | Side-chain rotamer validation | P2 | REQUIRES_EXTERNAL_REFERENCE_DATA |  |
| V11 | C-beta deviation | P2 | REQUIRES_EXTERNAL_REFERENCE_DATA |  |
| V12 | Cis/trans/twisted peptide geometry | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [v12-peptide-source-geometry-20261006](../../../software/qualification/v12-peptide-source-geometry-20261006/CHECKPOINT.txt) |
| V13 | CaBLAM backbone validation | P3 | REQUIRES_EXTERNAL_REFERENCE_DATA |  |
| V14 | RNA sugar pucker and backbone suite validation | P3 | REQUIRES_EXTERNAL_REFERENCE_DATA |  |
| V15 | Hydrogen optimization and Asn/Gln/His flip suggestions | P3 | EXPLICIT_ARCHITECTURAL_DISPOSITION |  |
| V16 | Global clashscore/percentiles/composite MolProbity score | P3 | EXPLICIT_ARCHITECTURAL_DISPOSITION |  |
| V17 | Ligand geometry / Mogul empirical distributions | P2 | REQUIRES_EXTERNAL_REFERENCE_DATA |  |
| V18 | Composition, missing atoms, alternate conformations, occupancy/B factors, linkage | P0 | SCIENTIFIC_REVIEW_REQUIRED |  |
| V19 | X-ray experimental-data and refinement validation | P3 | REQUIRES_EXTERNAL_REFERENCE_DATA | [foundation-closure-execution-20261006](../../../software/qualification/foundation-closure-execution-20261006/CHECKPOINT.txt) |
| V20 | X-ray local map/model fit | P2 | REQUIRES_EXTERNAL_REFERENCE_DATA | [foundation-closure-execution-20261006](../../../software/qualification/foundation-closure-execution-20261006/CHECKPOINT.txt) |
| V21 | NMR ensemble/shift/constraint validation | P3 | REQUIRES_EXTERNAL_REFERENCE_DATA | [foundation-closure-execution-20261006](../../../software/qualification/foundation-closure-execution-20261006/CHECKPOINT.txt) |
| V22 | EM map validation and map-model fit | P3 | REQUIRES_EXTERNAL_REFERENCE_DATA | [foundation-closure-execution-20261006](../../../software/qualification/foundation-closure-execution-20261006/CHECKPOINT.txt) |
| A01 | SMIRNOFF hierarchical parameter precedence and typed serialization | P0 | EXPLICIT_ARCHITECTURAL_DISPOSITION | [a01-disposition-20261005](../../../software/qualification/a01-disposition-20261005/CHECKPOINT.txt) |
| A02 | Constraints and bond/angle/proper/improper torsion parameter families | P3 | EXPLICIT_ARCHITECTURAL_DISPOSITION |  |
| A03 | vdW and electrostatics parameter families | P3 | EXPLICIT_ARCHITECTURAL_DISPOSITION |  |
| A04 | Library/AM1-BCC/charge-increment/NAGL charge assignment | P3 | EXPLICIT_ARCHITECTURAL_DISPOSITION |  |
| A05 | GBSA implicit solvent parameterization | P3 | EXPLICIT_ARCHITECTURAL_DISPOSITION |  |
| A06 | Virtual-site charge geometry | P3 | EXPLICIT_ARCHITECTURAL_DISPOSITION |  |
| A07 | Chemical alerts and substructure filter catalogs | P3 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [a07-embedded-catalog-characterization-20261006](../../../software/qualification/a07-embedded-catalog-characterization-20261006/CHECKPOINT.txt) |
| A08 | Tautomers, normalization, fragment parents and stereochemical identity | P0 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED |  |
| A09 | Molecular descriptors/fingerprints/shape and torsion resources | P3 | EXPLICIT_ARCHITECTURAL_DISPOSITION |  |
| G01 | Methyl environment | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [continuous-geometry-20261005](../../../software/qualification/continuous-geometry-20261005/CHECKPOINT.txt), [b05-chemical-attribution-20261005](../../../software/qualification/b05-chemical-attribution-20261005/CHECKPOINT.txt) |
| G02 | Vicinal-disulfide-compatible geometry | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [continuous-geometry-20261005](../../../software/qualification/continuous-geometry-20261005/CHECKPOINT.txt), [b06-cysteine-identity-contract-20261006](../../../software/qualification/b06-cysteine-identity-contract-20261006/DESIGN.txt), [b06-cysteine-backbone-20261006](../../../software/qualification/b06-cysteine-backbone-20261006/CHECKPOINT.txt) |
| G03 | Sulfur-pi and chalcogen-O | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [continuous-geometry-20261005](../../../software/qualification/continuous-geometry-20261005/CHECKPOINT.txt) |
| G04 | Cysteine environment | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [continuous-geometry-20261005](../../../software/qualification/continuous-geometry-20261005/CHECKPOINT.txt), [b06-cysteine-identity-contract-20261006](../../../software/qualification/b06-cysteine-identity-contract-20261006/DESIGN.txt), [b06-cysteine-backbone-20261006](../../../software/qualification/b06-cysteine-backbone-20261006/CHECKPOINT.txt) |
| G05 | SAM sulfonium/methyl/aromatic environment | P2 | SCIENTIFIC_REVIEW_REQUIRED | [continuous-geometry-20261005](../../../software/qualification/continuous-geometry-20261005/CHECKPOINT.txt) |
| G06 | SAM methyl-transfer geometry | P2 | SCIENTIFIC_REVIEW_REQUIRED | [continuous-geometry-20261005](../../../software/qualification/continuous-geometry-20261005/CHECKPOINT.txt) |
| G07 | n-to-pi-star motif | P3 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [continuous-geometry-20261005](../../../software/qualification/continuous-geometry-20261005/CHECKPOINT.txt), [v12-peptide-source-geometry-20261006](../../../software/qualification/v12-peptide-source-geometry-20261006/CHECKPOINT.txt) |
| G08 | Typed H-bond/charge/cofactor/second-shell networks | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [event-coverage-20261006](../../../software/qualification/event-coverage-20261006/CHECKPOINT.txt), [event-reference-identity-20261006](../../../software/qualification/event-reference-identity-20261006/CHECKPOINT.txt), [foundation-event-clean-20261006](../../../software/qualification/foundation-event-clean-20261006/CHECKPOINT.txt) |
| F01 | Functional group: Acids/carboxylates | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [b01-functional-group-20261005](../../../software/qualification/b01-functional-group-20261005/CHECKPOINT.txt), [foundation-groups-20261005](../../../software/qualification/foundation-groups-20261005/CHECKPOINT.txt) |
| F02 | Functional group: Amides and methyl amides | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [foundation-vocabulary-20261005](../../../software/qualification/foundation-vocabulary-20261005/CHECKPOINT.txt) |
| F03 | Functional group: Esters/acyl linkages | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [b01-functional-group-20261005](../../../software/qualification/b01-functional-group-20261005/CHECKPOINT.txt) |
| F04 | Functional group: Aldehydes/ketones/carbonyls | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [foundation-vocabulary-20261005](../../../software/qualification/foundation-vocabulary-20261005/CHECKPOINT.txt) |
| F05 | Functional group: Acid/sulfonyl chlorides | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [f05-acyl-sulfonyl-chloride-review-20261006](../../../software/qualification/f05-acyl-sulfonyl-chloride-review-20261006/CHECKPOINT.txt), [i02-approved-candidate-20261006](../../../software/qualification/i02-approved-candidate-20261006/CHECKPOINT.txt) |
| F06 | Functional group: Amine subclasses | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [b01-functional-group-20261005](../../../software/qualification/b01-functional-group-20261005/CHECKPOINT.txt), [foundation-groups-20261005](../../../software/qualification/foundation-groups-20261005/CHECKPOINT.txt), [foundation-vocabulary-20261005](../../../software/qualification/foundation-vocabulary-20261005/CHECKPOINT.txt) |
| F07 | Functional group: Imines/oximes/nitroso | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [f07-source-identity-review-20261006](../../../software/qualification/f07-source-identity-review-20261006/REVIEW.txt), [f07-source-identity-20261006](../../../software/qualification/f07-source-identity-20261006/CHECKPOINT.txt), [foundation-post-f07-clean-source-20261006](../../../software/qualification/foundation-post-f07-clean-source-20261006/CHECKPOINT.txt), [foundation-closure-execution-20261006](../../../software/qualification/foundation-closure-execution-20261006/CHECKPOINT.txt) |
| F08 | Functional group: Azo/hydrazine/diazo/azide | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [foundation-closure-execution-20261006](../../../software/qualification/foundation-closure-execution-20261006/CHECKPOINT.txt) |
| F09 | Functional group: Nitriles | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [foundation-vocabulary-20261005](../../../software/qualification/foundation-vocabulary-20261005/CHECKPOINT.txt) |
| F10 | Functional group: Nitro groups | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [foundation-vocabulary-20261005](../../../software/qualification/foundation-vocabulary-20261005/CHECKPOINT.txt) |
| F11 | Functional group: Sulfonamides | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [foundation-vocabulary-20261005](../../../software/qualification/foundation-vocabulary-20261005/CHECKPOINT.txt) |
| F12 | Functional group: Sulfonic acid/sulfonate ester/sulfone | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [foundation-vocabulary-20261005](../../../software/qualification/foundation-vocabulary-20261005/CHECKPOINT.txt) |
| F13 | Functional group: Sulfoxide/thioether/thiol/thiocarbonyl | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [b01-functional-group-20261005](../../../software/qualification/b01-functional-group-20261005/CHECKPOINT.txt), [foundation-groups-20261005](../../../software/qualification/foundation-groups-20261005/CHECKPOINT.txt) |
| F14 | Functional group: Isocyanate/isothiocyanate | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [f14-isocyanate-review-20261006](../../../software/qualification/f14-isocyanate-review-20261006/CHECKPOINT.txt), [i02-approved-candidate-20261006](../../../software/qualification/i02-approved-candidate-20261006/CHECKPOINT.txt) |
| F15 | Functional group: Boron motifs | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [foundation-closure-execution-20261006](../../../software/qualification/foundation-closure-execution-20261006/CHECKPOINT.txt) |
| F16 | Functional group: Alcohol/phenol/ether motifs | P1 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [b01-functional-group-20261005](../../../software/qualification/b01-functional-group-20261005/CHECKPOINT.txt), [foundation-groups-20261005](../../../software/qualification/foundation-groups-20261005/CHECKPOINT.txt) |
| F17 | Functional group: Halogenated motifs | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [foundation-groups-20261005](../../../software/qualification/foundation-groups-20261005/CHECKPOINT.txt) |
| F18 | Functional group: Terminal alkyne | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [foundation-vocabulary-20261005](../../../software/qualification/foundation-vocabulary-20261005/CHECKPOINT.txt), [f18-terminal-alkyne-contract-20261006](../../../software/qualification/f18-terminal-alkyne-contract-20261006/CHECKPOINT.txt), [i02-approved-candidate-20261006](../../../software/qualification/i02-approved-candidate-20261006/CHECKPOINT.txt) |
| F19 | Functional group: Branched alkyl/cyclopropyl | P2 | BOUNDED_SUPPORTED_DOMAIN_QUALIFIED | [foundation-closure-execution-20261006](../../../software/qualification/foundation-closure-execution-20261006/CHECKPOINT.txt) |

## Disposition scope and qualification limits

Bounded qualification denotes the linked implementation checkpoint only. It is not a current-policy receipt.
SCIENTIFIC_REVIEW_REQUIRED records an explicit review disposition, not implemented behavior.

### P01 — SMARTS/SMIRKS and reusable atom/group queries

Corrected OCL query dialect and distinct-target occurrence mapping only; no universal SMARTS/SMIRKS or cross-engine parity.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** SubstructureMatcher + OclMolecularBackend.match

**Supporting source:** RDKit book; OCL backend; OpenFF ParameterHandler; ProLIF base

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### P02 — Valence/aromaticity/conjugation/hybridization/ring perception

Attributed reviewed aromatic 5/6 cycles and fused membership; dimensional valence validation. No universal hybridization/conjugation classifier or arbitrary ring model.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** MolecularGraph; OclGraphMapper; AromaticRingPerception

**Supporting source:** RDKit book; OCL source; PLIP find_rings; RING paper

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.AROMATIC.json) · SHA256 `423614c70fe4e5c9b5947b1074a71ba316e63b9c622cd632776929c2788df192`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.HETEROAROMATIC.json) · SHA256 `7f0b888233e03de41632181792ed2f3816d125d924c3f1f50ade1ae1700de5dd`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.PERCEPTION.AROMATIC.json) · SHA256 `bac24710a81ebfe95e3a9a81bfe7df8125e731901fe6e2b1a1d461dde0d38b04`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### P03 — Donor and acceptor features

17 reviewed donor/acceptor role identities and bounded composition; not directional interactions or all possible chemistry.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** OclLigandFeaturePerceiver; AthenaScientificRules

**Supporting source:** RDKit BaseFeatures; ProLIF HBAcceptor; OCL PharmacophoreCalculator

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.ALCOHOL.json) · SHA256 `44c4f5095343264c2b3e1a934c1ca406e03f7205b58ee41bfa363a0191f71715`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.AMINE.json) · SHA256 `4fe20a026c3a571b5bc8070de3fb21052adf5fad2546ac7d9a400f41ffb0a25b`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.ETHER.json) · SHA256 `8c5f9b0cffa82555ebefb57c293cb5496f82838744bcc4a34e0d155c578d9888`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.PHENOL.json) · SHA256 `1868fd7684192688ee5c58b117f29eef3f6330c906246c221edbb872f69be5b6`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.PERCEPTION.ACCEPTOR.json) · SHA256 `418069591b794f0c68285d91c124a46d8a3462a49b0f8c72a579deaaa86a2feb`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.PERCEPTION.DONOR.json) · SHA256 `f33531ba78b0ce7d0e8474349ffdb8a308d0082f018c4b8cdedb6a1ab41799d2`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### P04 — Positive/negative ionizable features versus formal charge

8 atomic charge/nonpolar features plus reviewed carboxylate/ammonium exact group formal-charge sums; no pKa/ionization inference.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** FormalChargeAssignments; ChargedGroupPerception

**Supporting source:** BaseFeatures; OCL IonizableGroupDetector; PLIP find_charged

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.CARBOXYLATE.json) · SHA256 `120a1f195039521a18e292001b45d0be993291edace2cb4d96a50e07bf160a0d`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.CARBOXYLIC_ACID.json) · SHA256 `083a7bc55823e8462abe7f0985926ca9e7c8ffa7db7ca8b6231db6757bc34148`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.PERCEPTION.FORMAL_CHARGE.json) · SHA256 `8b21fd289cd9f462be66fe4c1b71d402e50bd606a44b15bd705e93837fac51c8`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.PERCEPTION.IONIZABLE_MOTIF.json) · SHA256 `e0a767b6d402eae297a4ba53a6b28ca61d45b1cb1b879849841a8617428ba875`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### P05 — Hydrophobe and lumped hydrophobe features

Reviewed atom predicates and all-members aromatic-carbocycle predicate; no hydrophobic free energy or maximal-subgraph grouping.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** OclLigandFeaturePerceiver; HydrophobicAtomPerception

**Supporting source:** RDKit BaseFeatures; ProLIF Hydrophobic; PLIP hydrophobic_atoms

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.PERCEPTION.NONPOLAR.json) · SHA256 `da87aab516fda062ddde0c4d0dd496d92416eceaf07a449f61594a68bcad013d`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### P06 — Aromatic pharmacophore/centroid features

Qualified ring attribution and independent centroid/plane V2; broader pharmacophore interpretation excluded.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** AromaticRingPerception; LigandFeature.AROMATIC_RING/PI_FEATURE

**Supporting source:** BaseFeatures; OCL PharmacophoreCalculator; ProLIF BasePiStacking

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.HETEROAROMATIC.json) · SHA256 `7f0b888233e03de41632181792ed2f3816d125d924c3f1f50ade1ae1700de5dd`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.PERCEPTION.AROMATIC.json) · SHA256 `bac24710a81ebfe95e3a9a81bfe7df8125e731901fe6e2b1a1d461dde0d38b04`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### P07 — Zinc-binding pharmacophore motifs

Six literal RDKit ZnBinder source-feature identities, seven explicit OCL transfer branches, in one complete neutral heavy C/N/O/S/P source component. OR union retains all correspondences; no actual zinc-binding/coordination assertion.

**Qualification:** 2471 fresh committed-source tests +3 isolation;65 focused P07;21 JVM pairs,20 prior hashes unchanged;65 historical files and25 pins exact.

**Existing implementation:** Opt-in athena.zn-source-features/1 and seven declarative group/4 branches; existing matcher, source-H consistency, scope and Research Gate unchanged.

**Supporting source:** Pinned RDKit BaseFeatures.fdef and explicitly reviewed transferred queries;34 synthetic reference cases. Advisory literal feature identity only; no full-toolkit parity or chemical binding authority.

No explicit-H targets, charged components, incomplete/conflicting source state, nonordinary connections, weighted centers, inferred H or actual zinc binding. Feature negatives require all original OR branches and independent current source/feature authority.

### P08 — Functional-group hierarchy and fragment features

45 declarative group definitions with overlap and correspondence alternatives; no unlimited functional-group catalog.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** OCL matcher; LigandFeature.CUSTOM; lineage mapping

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.ALKYL.json) · SHA256 `ec6bd407031c2ef7227148de29dacf2351e972ae5ea3f8b7966f6331f6506af7`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### P09 — Explicit/implicit hydrogens and state preparation

Authoritative source-H consistency and explicit/implicit/unknown separation in reviewed graph domains; no H placement or protonation preparation.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** SystemStateView.protonationQualified; source graph

**Supporting source:** PLIP preparation; RING usage; MolProbity guide; RDKit book

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.PERCEPTION.ACCEPTOR.json) · SHA256 `418069591b794f0c68285d91c124a46d8a3462a49b0f8c72a579deaaa86a2feb`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.PERCEPTION.DONOR.json) · SHA256 `f33531ba78b0ce7d0e8474349ffdb8a308d0082f018c4b8cdedb6a1ab41799d2`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### I01 — Hydrophobic contact

Existing neutral-carbon bounded contact definition unchanged.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** ATHENA.HYDROPHOBIC.CONTACT

**Supporting source:** PLIP detection/refinement; ProLIF Hydrophobic; Athena manifest

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### I02 — Explicit-H directional hydrogen bond

Separately versioned I02.EXPLICIT_H_DIRECTIONAL_CANDIDATE/1: 100 reviewed explicit-H class pairs at D-A <=3.5 angstrom and DHA >=130 degrees; ten pyridinium pairs remain unsupported. Historical alcohol/oxygen rule remains unchanged.

**Qualification:** 949 focused/regression checks, independent replay and preserved historical evidence; implementation-qualified within reviewed domain, no production receipt.

**Existing implementation:** athena.hbond-candidate/1 + verified role reports + athena.geometry/1

**Supporting source:** PLIP hbonds; ProLIF HBAcceptor/HBDonor; Probe; RING paper

[Class-pair review package](../../../software/qualification/i02-expansion-review-20261006/CLASS_PAIR_PROPOSAL.json)

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### I03 — Implicit-H hydrogen-bond approximation

Approved I03-A six neutral amine donor/acceptor pairs; heavy-atom minimum-over-neighbors directional proxy with independently qualified bounded S1 source assignments. No observed/inferred H coordinates or physical hydrogen-bond assertion.

**Qualification:** 2226 fresh committed-source tests +3 isolation;137 S1/composition tests, including16 separately governed composition checks;94 S1 matrix cases;16 independent JVM pairs;13 legacy hashes unchanged;65 historical files exact;25 pins intact. No production authority.

**Existing implementation:** Opt-in athena.i03-n-sp3-s1/1 source producer and athena.implicit-h-proxy-s1/1 composition; historical I03-A version1.0.0 remains blocked and unchanged.

**Supporting source:** User-approved I03-A contract and bounded S1 source-graph model; source differences and external-only RDKit/OCL probes preserved in linked review. No toolkit is an assignment authority.

No I03-B/C, inferred-H coordinates, SP/SP2 proxy, full ProLIF compatibility, new chemistry roles or universal hybridization. S1 exclusions remain outside this model. Real execution requires independent exact source-scope, S1 producer and composition qualification under current authority.

### I04 — Weak C-H donor perception

Exact selected [GLY CA,one explicit bonded H,GLY carbonyl O,C] in distinct finite N-acylated/C-amidated source components. dHO<3.5 AND(angle>120 OR(dHO<3.0 AND angle>90)); two explicit donor CA H independently required, tuples distinct.

**Qualification:** 2594 committed-source tests +3 isolation;62 focused;23 JVM pairs,22 prior hashes unchanged;65 historical files and25 pins exact.

**Existing implementation:** Opt-in athena.glycine-h-carbonyl/1 leaf; unchanged carbonyl role, source scope, geometry and Research Gate.

**Supporting source:** Explicitly approved Option B adaptation of Senes2001 Results window; exact finite source glycine context. No energetic or survey-parity claim.

Option A protocol, universal weak donors, I03/inferred H, C-H-pi and N+-C-H-O proposals, charged/modified/non-GLY context, isotope-labelled donor H and same-component tuples. No whole-system absence or biological inference.

### I05 — Parallel/face-to-face pi stacking

Existing isolated neutral six-carbon ring pi-stacking geometry; no energetic claim or widened ring domain.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** ATHENA.PI_STACKING.GEOMETRY

**Supporting source:** ProLIF FaceToFace/BasePiStacking; PLIP pistacking

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### I06 — T-shaped/edge-to-face pi stacking

Existing T-shaped mode within the same bounded pi-stacking implementation; no independent qualification count.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** ATHENA.PI_STACKING.GEOMETRY

**Supporting source:** ProLIF EdgeToFace; PLIP pistacking

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### I07 — Union pi-stacking wrapper

Existing union of pi-stacking modes; wrapper is not another chemical observation.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** Single Athena stacking family with geometry class

**Supporting source:** ProLIF PiStacking.detect

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.PI_STACKING.GEOMETRY.json) · SHA256 `a35e5eb11552c39d97868a0a13f4173755f1f276aa75780cae1fdae85978b125`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### I08 — Cation-pi

Existing bounded ammonium/neutral carbocycle cation-pi geometry only.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** ATHENA.PI_CATION.GEOMETRY

**Supporting source:** ProLIF CationPi/PiCation; PLIP pication; RING paper

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### I09 — Ionic/salt-bridge proximity

Existing ammonium/carboxylate proximity definition, not electrostatic energy.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** ATHENA.SALT_BRIDGE.PROXIMITY

**Supporting source:** PLIP saltbridge; ProLIF Cationic/Anionic; RING paper

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### I10 — Halogen bond to atom acceptor

Exact selected carbon-bound neutral Cl/Br/I–neutral-carbonyl tuple under the explicitly adopted ProLIF numeric window; separate element and carbon aromaticity retained. Structural candidate only, not physical bond or energy.

**Qualification:** 2271 fresh committed-source tests +3 isolation;45 focused I10;17 JVM pairs,16 prior hashes unchanged;65 historical files and25 pins exact.

**Existing implementation:** Opt-in athena.halogen-carbonyl/1; legacy HalogenBondDetector and INT.HALOGEN.001 unchanged.

**Supporting source:** Exact pinned ProLIF DoubleAngle window3.5A/130..180/80..140; restricted unchanged source roles. PLIP and survey definitions preserved separately in CONTRACT.txt. Adopted under REQUEST.txt authorization.

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.HALOGENATED.json) · SHA256 `27c940e949327cf2f5c38b4a2a7ec856c1a5588b5175b6922bc313a090fca0ae`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.HALOGEN_BOND.DIRECTIONAL.json) · SHA256 `c992114e366e8ade1eca44434144bec0415bc4ac7a6377e3a7c6b2dedffbb6d6`

No fluorine/At/noncarbon donor/other acceptor/pi/same-component/charged or radical core; no whole-system negative, energy, biological assertion or full ProLIF/PLIP parity. Independent current source-scope and I10 authority required.

### I11 — Halogen bond to pi system

Define aromatic-face acceptance, halogen axis and element-specific geometry separately from atom-acceptor rules.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** No qualified equivalent

**Supporting source:** RING4 primary paper non-covalent bond section

Aromatic-face halogen interaction requires explicit face/axis/angular/radius definition separate from atom-acceptor I10; ring and mixed geometry exist. Do not silently substitute atom distance thresholds.

### I12 — Single-water H-bond bridge

Explicit-source-H structural candidate with exactly one neutral internal water; all directional legs coexist in one immutable supplied state.

**Qualification:** 2027 full committed-source tests +3 source-identical supplemental matrix tests +3 isolation;98 family tests;50 matrix cases;11 independent JVM pairs;65 historical files exact;25 pins intact. No production receipt.

**Existing implementation:** WaterBridgeInputs/WaterIdentity/WaterBridgeLegs/WaterBridgeRules compose existing matcher, source roles, geometry, coverage, Research Gate and unchanged EventPaths.

**Supporting source:** Pinned source definitions compared explicitly in water-bridge-contract-20261006/CONTRACT.txt and SOURCE_PINS.json; direct user approval with two normative clarifications.

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.WATER_BRIDGE.PATH.json) · SHA256 `bd504e9bd3ece084ed54b8bb1f97c491070fc85a1f5a14d9d2f44a3d263ed21b`

No implicit-H orientation, inferred waters, PLIP heuristic/pruning, occupancy/energy interpretation, extra donor/acceptor chemistry or physical solvent completeness.

### I13 — Multi-water bridge paths

Explicit-source-H structural candidate with 2..K distinct neutral internal waters, explicitly bounded K; all directional legs coexist in one immutable supplied state.

**Qualification:** 2027 full committed-source tests +3 source-identical supplemental matrix tests +3 isolation;98 family tests;50 matrix cases;11 independent JVM pairs;65 historical files exact;25 pins intact. No production receipt.

**Existing implementation:** WaterBridgeInputs/WaterIdentity/WaterBridgeLegs/WaterBridgeRules compose existing matcher, source roles, geometry, coverage, Research Gate and unchanged EventPaths.

**Supporting source:** Pinned source definitions compared explicitly in water-bridge-contract-20261006/CONTRACT.txt and SOURCE_PINS.json; direct user approval with two normative clarifications.

No implicit-H orientation, inferred waters, PLIP heuristic/pruning, occupancy/energy interpretation, extra donor/acceptor chemistry or physical solvent completeness.

### I14 — Metal-ligand pair proximity

Exact selected source monatomic Zn2+ and unchanged neutral carbonyl-O in separate components, 0 < distance <= 2.8 angstrom; source-structural proximity only, not coordination or energy.

**Qualification:** 2310 fresh committed-source tests +3 isolation;39 focused I14;18 JVM pairs,17 prior hashes unchanged;65 historical files and25 pins exact.

**Existing implementation:** Opt-in athena.zinc-carbonyl/1; source metal and neutral carbonyl coverage remain independent.

**Supporting source:** Exact pinned ProLIF MetalDonor distance window 2.8 angstrom, restricted to source monatomic Zn2+ and unchanged carbonyl role; PLIP coordination definitions remain separate. Adopted under REQUEST.txt authorization.

No other metals/charges/bonded or H-bearing Zn/other acceptors/same-component/nonordinary or unresolved source state; no whole-system negative, coordination sphere, energy or full-engine parity. Independent current source-scope and I14 authority required.

### I15 — Metal coordination geometry/complex

Specify coordination number/geometry, ligand typing, waters and complete candidate sphere.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** Reference registration only

**Supporting source:** PLIP metal_complexation and find_metal_binding

Coordination number/geometry requires complete candidate sphere, donor/water typing, treatment of competing geometries and metal state. Generic centroid/angle machinery is available; no universal coordination template is selected.

### I16 — Van der Waals contact/gap

Attributed continuous d-(r1+r2) descriptor only; no clash or favorable-contact classification.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** StericClashAnalysis; Element radii; proximity measurements

**Supporting source:** ProLIF VdWContact; Probe atomprops/probe.c; RING docs

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.VAL.VDW_CONTACT.json) · SHA256 `3b9b8361b5f088c2538742b85d1bd8ec046e625a301a3a462aa86335ce8fd9cb`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### I17 — Steric overlaps/clashes

Select radii/H/bond/altloc/water protocol and justified overlap classes; I16 gap alone is not a clash criterion.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** StericClashAnalysis; VAL.CLASH.001

**Supporting source:** Probe source; MolProbity clashes; wwPDB guide

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.VAL.CLASH.json) · SHA256 `843ecafe29a2dfb8ed34aef9bec617f688e91a940ca05b19a22c7bcca81ae055`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.VAL.VDW_CONTACT.json) · SHA256 `3b9b8361b5f088c2538742b85d1bd8ec046e625a301a3a462aa86335ce8fd9cb`

**Remaining scientific requirement:** Probe2 source and reference practice pinned; no new benchmark run. Serious-overlap reference0.4A is protocol-specific. No Probe parity effort; select Athena radius/H protocol and qualify categories against curated fixtures before new profile.

**Remaining scientific requirement:** Word1999 abstract and current Probe sources support protocol separation. Historical0.05A criterion remains unverified; current0.4A not transplanted.

I16 retains attributed continuous surface gaps. A serious-clash class needs the exact radii/H/bond-exclusion/altloc/water protocol and empirical validation; Probe 0.4 A belongs to its own protocol, not all radius models.

### I18 — Pi-hydrogen / X-H-pi geometry

Explicit methyl/ring-to-V3 raw geometry attribution; no CH-pi favorable-interaction classifier.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** Proposed ATHENA.INT.CH_PI.GEOMETRY

**Supporting source:** RING4 primary paper; research task

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.INT.CH_PI.GEOMETRY.json) · SHA256 `478f3a987a4cd69e50bb8860ad8f5915e555f6817c70b8383a2e49d43b2cb401`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### I19 — Disulfide bond versus S-S proximity

Versioned authoritative source S-S connectivity with complete-negative coverage and bounded backbone attribution; no oxidation-state or redox classifier.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** SULF.SS.001; sulfur measurements

**Supporting source:** RING3/4 papers; RuleAnalyzers.sulfur

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.SULF.DISULFIDE.json) · SHA256 `d23e37734c02d2fdb115a8366ff8a3eb43aabedb8495af24ea78cf6dca1ecd35`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### I20 — Unpaired donor/acceptor/halogen features

Profile-relative unpaired predicate over an explicitly supplied feature and complete partner-event universe; no automatic feature perception or energetic unsatisfaction.

**Qualification:** Bounded approved operational definition;1932+3 clean-source tests,56 focused tests,9 independent replay comparisons and65 historical files exact. No production scientific receipt.

**Existing implementation:** EventAnalysisRules/EventInputs/EventCounts/EventPaths + governed evaluateCurrent

**Supporting source:** PLIP find_unpaired_ligand

Explicit feature/profile/partner-universe predicate only; no inferred role identities, automatic chemical pairing or energetic unsatisfaction.

### I21 — Interaction overlap/refinement and pruning

Reject destructive pruning as evidence policy; retain optional display/filter provenance

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** Immutable raw measurements; separate assessments

**Supporting source:** PLIP refine_*; RING usage

### N01 — Directional roles and atom/group attribution

Exact bounded role/group-to-continuous-geometry attribution with state/correspondence preservation.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** SystemStateView; InteractionMeasurements; evidence subjects

**Supporting source:** ProLIF invert_role/metadata; PLIP Mapper; Athena sources

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### N02 — Binary/count interaction fingerprints

Explicit finite distinct-key counts and per-state assessment masks; legacy fingerprints remain positive-only and unchanged.

**Qualification:** Bounded approved operational definition;1932+3 clean-source tests,56 focused tests,9 independent replay comparisons and65 historical files exact. No production scientific receipt.

**Existing implementation:** EventAnalysisRules/EventInputs/EventCounts/EventPaths + governed evaluateCurrent

**Supporting source:** ProLIF fingerprint.py

No automatic legacy conversion or inferred negative bits; producers supply exact attributed event/occurrence identity.

### N03 — Ensemble contact frequencies/probabilistic networks

Explicit immutable ensemble descriptive frequencies with eligible present+absent denominator; zero eligible is undefined, not probability.

**Qualification:** Bounded approved operational definition;1932+3 clean-source tests,56 focused tests,9 independent replay comparisons and65 historical files exact. No production scientific receipt.

**Existing implementation:** EventAnalysisRules/EventInputs/EventCounts/EventPaths + governed evaluateCurrent

**Supporting source:** RING4/RING-MD; ProLIF fingerprints

Descriptive frequencies only; no probability or implicit ensemble selection.

### N04 — Contact correlations and co-occurrence networks

Exact contingency/cooccurrence counts over explicitly corresponding common states only; no independent pairing, Pearson or causality.

**Qualification:** Bounded approved operational definition;1932+3 clean-source tests,56 focused tests,9 independent replay comparisons and65 historical files exact. No production scientific receipt.

**Existing implementation:** EventAnalysisRules/EventInputs/EventCounts/EventPaths + governed evaluateCurrent

**Supporting source:** RING-PyMOL pinned README

Exact common-state counts only; no correlation coefficient, causal interpretation or independent-ensemble pairing.

### N05 — Residue representation and network edge cardinality

Authoritative full ResidueGraph plus exact atom/residue attribution and separate spatial/covalent/interaction views; no inferred chemical edge.

**Qualification:** Existing accepted system-graph implementation/checkpoint and SystemQualificationAcceptanceTest; re-exercised in clean-source suite. No current-policy scientific receipt.

**Existing implementation:** ResidueGraph authoritative + SystemStateView derived spatial traversal

**Supporting source:** RING usage; Athena graph contract

Typed chemical-network rules remain under separate scientific review; geometric reachability is not chemical coupling.

### N06 — Network degree/paths/second shell

Existing bounded SystemStateView.neighborhood spatial traversal, cycle handling, hop/budget completeness and second-shell attribution; no physical coupling or unreviewed typed-network composition.

**Qualification:** Existing accepted system-graph implementation/checkpoint and SystemQualificationAcceptanceTest; re-exercised in clean-source suite. No current-policy scientific receipt.

**Existing implementation:** SystemStateView neighborhood/path operations

**Supporting source:** RING4 output; Athena GEO.PATH/GEO.SHELL

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.NETWORK.SECOND_SHELL.json) · SHA256 `84abfd54600e531c970bcf5a4af331c26d3e987459ff3ca8a6ac8a27d306892d`

Typed chemical-network rules remain under separate scientific review; geometric reachability is not chemical coupling.

### N07 — RMSD-based ensemble clustering

Consciously defer separate ensemble-analysis tool; no new clustering here

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** Existing pose-comparison infrastructure; not interaction classifier

**Supporting source:** RING-PyMOL README

### N08 — 2D/3D pharmacophore fingerprints and matching

One explicitly selected ordered correspondence of three neutral carbonyl O features in one complete source component and three attributed query points; existing rigid RMSD <= supplied reviewed bound. No default tolerance or global search.

**Qualification:** 2368 fresh committed-source tests +3 isolation;58 focused N08;19 JVM pairs,18 prior hashes unchanged;65 historical files and25 pins exact.

**Existing implementation:** Opt-in athena.selected-pharmacophore/1; existing carbonyl identities and FeatureTemplateAlignmentEvaluator unchanged; query geometry remains distinct from observed source coordinates.

**Supporting source:** Exact user-approved N08 contract and additive attributed query payload; exact query SHA-256 bound into independently reviewed manifest parameters; unchanged mathematical alignment. No empirical tolerance invented.

No other roles, partial correspondence, collinear/coincident triplets, search, source-state mixing, inferred geometry, default tolerance or whole-molecule negative. Exact query and source-scope current authority independently required.

### V01 — Valence/sanitization and nonempty graph validation

Dimensional topology/valence and independent source-state checks; no repair or universal chemical validity.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** MolecularSanitizer; SystemGraphValidation

**Supporting source:** RDKit Validate; OCL backend

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### V02 — Fragment/salt/solvent and neutral-charge checks

Explicit neutrality policy on supplied scope, independently of structural validity; disconnected components preserved without salt/solvent interpretation.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** MolecularGraph components; sanitizer limitations

**Supporting source:** RDKit Validate; OCL sanitizer

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### V03 — Isotopes, allowed/disallowed elements and radicals

Opt-in explicit radical representation and identity round-trip with granular unsupported-operation guards; no general radical chemistry.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** MolecularGraph isotope/element fields; capability checks

**Supporting source:** RDKit Validate; MolecularGraph

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### V04 — Representation/query/dummy/enhanced-stereo validation

Approved OCL_IDCODE_QUERY/2026.7.2 native operation profile with exact original bytes, native atom-index correspondence, supported feature controls, and checked exclusion of unrepresented query/dummy/parity/ESR/target assertions. Seven exact catalog entries independently qualified.

**Qualification:** 2686 committed-source tests +3 isolation;92 focused;25 JVM pairs,23 prior hashes unchanged;65 historical files and25 pins exact.

**Existing implementation:** Approved additive native SubstructureMatcher overload; unchanged historical B00 bodies. Immutable catalog and opt-in athena.advisory-alert/1 leaf; existing source scope, provenance and Research Gate.

**Supporting source:** Explicitly approved native boundary and advisory catalog semantics; exact pinned OCL jar/original catalog queries, entry-specific engineering controls. No catalog-membership-to-biological-truth inference.

No universal query, dummy or enhanced-stereo representation engine; unsupported native features remain unsupported operations, never assertions that a physical molecule is invalid. No canonical re-encoding equality or unproven SMARTS transfer.

### V05 — Stereo syntax and authoritative stereo validation

Reviewed dimensional stereo assessment and unknown/unsupported handling; no invented stereo success from charge failure.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** StereochemistryService; OclMolecularBackend

**Supporting source:** RDKit Validate; OCL backend

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### V06 — 2D layout/dimensionality checks

Do not transplant 2D drawing thresholds into protein geometry

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** No rule equivalent required

**Supporting source:** RDKit Validate

### V07 — Bond-length and bond-angle validation

Select chemical-context reference lengths/angles, uncertainty and outlier definition.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** Geometry primitives; no qualified reference-distribution validator found

**Supporting source:** MolProbity geometry; wwPDB standard geometry

Pinned wwPDB guide specifies comparison to reference means/sigmas; neither a complete reviewed CCD/monomer release nor all context-dependent uncertainty tables are bundled. Need release/accession, atom/bond/state mapping, units, target and sigma, applicability and redistribution license. Existing raw distances/angles are implemented; no universal sigma is valid.

### V08 — Chirality/planarity validation in coordinates

Define coordinate stereo/planarity comparison against supplied chemistry and qualified reference domain.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** Graph stereo checks only; no full coordinate validator qualified

**Supporting source:** wwPDB model/ligand quality; MolProbity geometry

Raw plane diagnostics exist. Arbitrary atom ordering cannot establish chiral sign, and wwPDB sidechain planarity uses precomputed reference deviations. Need pinned component stereochemical ordering, expected configurations, group membership, normalization and applicable reference deviations. Guide thresholds alone do not supply these references.

### V09 — Protein Ramachandran validation

Review exact Ramachandran grid release, license, interpolation and residue/peptide classes.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** No qualified equivalent found in inspected rule surfaces

**Supporting source:** MolProbity Ramachandran; wwPDB torsion angles

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.VAL.RAMACHANDRAN.json) · SHA256 `3bd4ddf8439b312cf3e6332b628189f01600554878ed3a06e44de2c7d3f3a1d5`

**Remaining scientific requirement:** Pinned reference_data Top8000 README/tree; actual rama8000 grids not downloaded/calibrated. Do not substitute older rama.combined restraint tables. Grid selection/license packaging/interpolation and peptide-class handling.

Need pinned actual grid release (not README), license/redistribution terms, residue/peptide classes, axes/binning/periodicity, density values, interpolation and outlier interpretation. Older restraint tables are not a substitute.

### V10 — Side-chain rotamer validation

Review rotamer reference grid, symmetry, interpolation and complete chi/altloc coverage.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** No qualified protein rotamer validator in registry

**Supporting source:** MolProbity rotamer guide; OCL TorsionDB inventory

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.VAL.ROTAMER.json) · SHA256 `ab2869dee8a88310936e3c54d23f09b8fdd885e613e83985a8ef47201ac4bba6`

**Remaining scientific requirement:** Top8000 ultimate rotamer contour grids identified; not downloaded/reimplemented. Current cctbx 0.003/0.02 cutoffs belong to that distribution. Curated multidimensional library/interpolation and symmetry handling required.

Need exact multidimensional chi grids, residue/protonation/altloc class membership, symmetry/periodicity, interpolation, normalization and release/license. Pinned cctbx 0.003/0.02 use that distribution; constants alone are insufficient.

### V11 — C-beta deviation

Review idealization algorithm/parameters and modified/D-residue domain before classifying C-beta deviation.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** No qualified equivalent

**Supporting source:** MolProbity Cbeta guide

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.VAL.CBETA_DEVIATION.json) · SHA256 `28c726d4d444bd553ab3a8792d75e61052aaa17d25ed3942634551a5a7c1fc41`

**Remaining scientific requirement:** cctbx cbetadev implementation pinned; parameter/ideal geometry dependency closure not yet bundled. Reference idealization algorithm/parameters, residue-specific handling and applicability.

Pinned cbetadev algorithm exists; complete ideal geometry/monomer parameter closure and residue/D/modified-residue applicability are not bundled/qualified. Need immutable release/license and parameter files; do not substitute a generic ideal C-beta.

### V12 — Cis/trans/twisted peptide geometry

Source alpha-peptide omega outside cis/trans30-degree windows; exact selected tuple, cyclic/proline contexts included.

**Qualification:** 32 focused tests and1795 selected regression/consumer tests, two independent JVM replays and65 historical golden files. Fresh committed-source follow-up pending.

**Existing implementation:** No qualified equivalent

**Supporting source:** docs/manual/athena/supporting-material/PEPTIDE_GEOMETRY.md; pinned wwPDB guide and Bartlett et al.2010 Fig2a/Methods.

### V13 — CaBLAM backbone validation

Review CaBLAM empirical contours, dimensionality and complete backbone selection.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** No qualified equivalent

**Supporting source:** MolProbity CaBLAM

Need exact contour release, high-dimensional axes, residue/secondary-structure applicability, interpolation and thresholds plus license. Raw backbone dihedrals cannot reproduce empirical probability contours.

### V14 — RNA sugar pucker and backbone suite validation

Review RNA atom/topology domains and pucker/suite reference distributions.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** No qualified equivalent

**Supporting source:** MolProbity sugarpuckers/suites; wwPDB

Need exact Richardson/wwPDB suite/pucker reference release, conformer definitions, atom names-to-authoritative-graph correspondence, periodic angle/suite rules, exclusions and license. Generic torsion measurements are not RNA validation.

### V15 — Hydrogen optimization and Asn/Gln/His flip suggestions

Never change coordinates or protonation to manufacture favorable contacts

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** Source-state representation only in reviewed graph pipeline

**Supporting source:** MolProbity/wwPDB flip notes

### V16 — Global clashscore/percentiles/composite MolProbity score

May preserve external score as evidence; do not collapse Athena evidence into it

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** Separate capability assessments; no master evidence score

**Supporting source:** MolProbity summary guide; wwPDB overview

### V17 — Ligand geometry / Mogul empirical distributions

Establish licensed empirical ligand geometry distributions and sample coverage; no generic substitute.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** No qualified equivalent

**Supporting source:** wwPDB ligand geometry

Need authorized versioned distributions, fragment/state matching rules, sample counts/exclusions and outlier definition; redistribution/access license required. No generic bond geometry substitutes for Mogul empirical data.

### V18 — Composition, missing atoms, alternate conformations, occupancy/B factors, linkage

Review missing-atom/composition/altloc/occupancy/linkage propositions and uncertainty propagation per source format.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** SystemStateView identities/mapping; source fields

**Supporting source:** wwPDB composition/linkage/model quality

Expected composition/missing-atom assertion requires an external expected-component/topology model; occupancy/altloc/linkage scopes are format-dependent. Preserve source metadata, distinguish absent field from zero, and specify conflict/evaluation policy before classifying incompleteness.

### V19 — X-ray experimental-data and refinement validation

Define external diffraction/refinement report ingestion and data provenance; coordinates alone insufficient.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** No generic-rule implementation

**Supporting source:** wwPDB X-ray data/refinement guide

Need structure factors or intensities with uncertainties, R-free flags, unit cell/space group, resolution/selection, refinement protocol/model correspondence and data accession/license. Source report ingestion separately qualified; no native metric computed.

### V20 — X-ray local map/model fit

Define experimental map/state alignment and map-model metric domain.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** Evidence can preserve reports; evaluator absent

**Supporting source:** wwPDB fit-of-model-and-data

Need map coefficients/grid, origin/sampling, frame/model binding, resolution/selection, metric definition and accession/license. Coordinates alone do not establish density fit. Report ingestion separately qualified.

### V21 — NMR ensemble/shift/constraint validation

Define experimental shifts/restraints and ensemble correspondence; coordinate agreement is not experimental validation.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** Evidence storage supports opaque reports; no evaluator

**Supporting source:** wwPDB NMR guide

Need units, ambiguity/assignment policy, experimental accession/license and ensemble/model correspondence; individual restraints and unavailable observations retained. Report ingestion separately qualified.

### V22 — EM map validation and map-model fit

Define map-only vs model-fit criteria, resolutions/masks and source provenance.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** Evidence storage only; no evaluator

**Supporting source:** wwPDB EM guide

Need grid/origin/sampling/alignment, map-only versus model-fit metric, halfmaps and masks where required, resolution/protocol and accession/license. Report ingestion separately qualified.

### A01 — SMIRNOFF hierarchical parameter precedence and typed serialization

Adopt explicit ordering/versioning patterns; do not use last-wins to erase scientific disagreement

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** RuleRegistry version/hash/typed fields; OCL matcher

**Supporting source:** OpenFF ParameterHandler; prior SMIRNOFF audit

### A02 — Constraints and bond/angle/proper/improper torsion parameter families

Keep force field physics separate from classification rules

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** Existing energy modules are separate; no analyzer equivalent

**Supporting source:** Constraints/Bonds/Angles/ProperTorsions/ImproperTorsions handlers

### A03 — vdW and electrostatics parameter families

Do not turn classifier registry into force-field engine

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** Existing energy infrastructure separate

**Supporting source:** vdW/Electrostatics handlers

### A04 — Library/AM1-BCC/charge-increment/NAGL charge assignment

No automatic charge replacement; retain attribution if external evidence supplied

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** Amber charges source of truth; FormalChargeAssignments distinct

**Supporting source:** LibraryCharges/ToolkitAM1BCC/ChargeIncrementModel/NAGLCharges

### A05 — GBSA implicit solvent parameterization

Cannot stand in for water networks or measured burial

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** No scientific contact-rule equivalent

**Supporting source:** GBSA handler

### A06 — Virtual-site charge geometry

Learn frame/provenance handling; never present virtual charge sites as measured atoms

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** No authoritative-atom substitution allowed

**Supporting source:** VirtualSiteHandler

### A07 — Chemical alerts and substructure filter catalogs

Exact pinned OCL.PAINS/2026.7.2 advisory occurrences for independently qualified entries113,159,162,169,202,207,840 in one complete eligible source component. Every890 entry classified:7 representation/execution qualified,482 executable incomplete,401 unsupported. No catalog-wide absence.

**Qualification:** 2686 committed-source tests +3 isolation;92 focused;25 JVM pairs,23 prior hashes unchanged;65 historical files and25 pins exact.

**Existing implementation:** Approved additive native SubstructureMatcher overload; unchanged historical B00 bodies. Immutable catalog and opt-in athena.advisory-alert/1 leaf; existing source scope, provenance and Research Gate.

**Supporting source:** Explicitly approved native boundary and advisory catalog semantics; exact pinned OCL jar/original catalog queries, entry-specific engineering controls. No catalog-membership-to-biological-truth inference.

Other883 entries lack independent qualification or are unsupported; all-catalog absence unavailable. No toxicity, assay interference truth, drug-likeness, rejection authority, binding failure or medicinal-chemistry score.

### A08 — Tautomers, normalization, fragment parents and stereochemical identity

Optional unique-largest eligible heavy-source component view over one complete declared state; exact source graph/atom/bond lineage and all nonselected components retained. No normalization or tie-break.

**Qualification:** 2406 fresh committed-source tests +3 isolation;38 focused A08;20 JVM pairs,19 prior hashes unchanged;65 historical files and25 pins exact.

**Existing implementation:** Opt-in athena.source-fragment-parent/1; existing immutable state snapshot, source coverage/scope, correspondence and Research Gate; no graph edits.

**Supporting source:** Pinned RDKit Fragment.cpp comparison; explicit bounded unique heavy-count source policy under REQUEST.txt. No RDKit normalization/parity, chemical validity, salt or biological inference.

No explicit-H vertices, incomplete/overlapping source universe, unresolved source chemistry, known nonordinary connection, query/dummy/unrepresented stereo, tie choice, tautomerization or neutralization. Independent current source and parent-policy authority required.

### A09 — Molecular descriptors/fingerprints/shape and torsion resources

Considered as adjacent tool families; do not duplicate QSAR/scoring pipelines

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** Existing design/recognition/energy services; not qualified by this audit

**Supporting source:** RDKit source tree/book; OCL installed JAR inventory

### G01 — Methyl environment

Qualified methyl identity and explicit methyl/ring raw geometry channels only; no burial or magic-methyl prediction.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** ATHENA.GROUP.METHYL.ENVIRONMENT

**Supporting source:** Attached advanced-rule request; existing Athena sources; no external parity asserted

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.METHYL.ENVIRONMENT.json) · SHA256 `830719f35dccbb0a94bb7d10e3139b6859ceb66ed24f30d83b00f9f2d5bba9ff`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### G02 — Vicinal-disulfide-compatible geometry

Source-linked cysteine backbone/peptide linkage and raw geometry only; no vicinal-disulfide compatibility distribution.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** ATHENA.SULF.VICINAL

**Supporting source:** Attached advanced-rule request; existing Athena sources; no external parity asserted

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.SULF.VICINAL.json) · SHA256 `ad481eaa5ce4fd1abb639310f354b65a9d46c486df1fb78301b6271ff4dbdefb`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### G03 — Sulfur-pi and chalcogen-O

Exact selected source MET/PHE pair under the finite neutral amide-context graph contract, identical ring isotope descriptors, and qualified positive SD-centroid distance <=7 A. Raw normal angle is diagnostic only. Chalcogen-O is outside this closure.

**Qualification:** 2532 fresh committed-source tests +3 isolation;61 focused G03;22 JVM pairs,21 prior hashes unchanged;65 historical files and25 pins exact.

**Existing implementation:** Opt-in athena.met-phe-survey/1 leaf and two declarative group/4 source contexts. Existing geometry/3 POINT_PAIR_GROUP, matching, source scope, correspondence and Research Gate reused unchanged.

**Supporting source:** Pinned L04 source survey convention, intentionally bounded Phe-only source graph. No energy, affinity, biological role, sulfur-state transfer or chalcogen-O inference.

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.THIOETHER.json) · SHA256 `ee158666e9f07c58ac5b01b4233a542f1f7ee8a86ba96dba327836caf434d7c3`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.SULF.CHALCOGEN_O.json) · SHA256 `3bd607ef5c36530d1025e4324ada5e6bafc1b5270c6d75d9347802528bca8376`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.SULF.PI.json) · SHA256 `dde09e189408db17c7954c0b99a5e4f092f5c53514f806930b24d33371f099e6`

Chalcogen-O, Tyr/Trp/fused/hetero rings, sulfur oxidation/charge/extra substituents, free termini, explicit-H source graphs, missing/conflicting source facts, mixed ring isotope descriptors and nonordinary connections. No whole-system absence, no energy, occupancy or biological interpretation.

### G04 — Cysteine environment

Source-linked bounded cysteine backbone/geometry attribution; no reactivity/pKa/environment classifier.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** ATHENA.SULF.CYS_ENVIRONMENT

**Supporting source:** Attached advanced-rule request; existing Athena sources; no external parity asserted

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.SULF.CYS_ENVIRONMENT.json) · SHA256 `62381b2bfa60142a311cdad33d69abd6e0f9bfa7bcd938712f32a01fb05e8c43`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### G05 — SAM sulfonium/methyl/aromatic environment

Review SAM/SAH identity and sulfonium/methyl/aromatic domains; no ammonium-pi parameter transplant.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** ATHENA.SAM.*

**Supporting source:** Attached advanced-rule request; existing Athena sources; no external parity asserted

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.SAM.AROMATIC_ENVIRONMENT.json) · SHA256 `898a9256b760ffdb08bd604d80a6e0a29355647c388a801866f08045043ae233`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.SAM.METHYL_PI.json) · SHA256 `37b774e64835664a189b06ec2547c2396679b1be21ff73f00b1e3ef93354b58c`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.SAM.SULFONIUM_PI.json) · SHA256 `7f0bc680c90c2dff8b3bafa73a7b4d5453b47ae09696d495c8dbf5d59d4fa9a6`

**Remaining scientific requirement:** No single universal SAM-aromatic structural class assumed. Dependency qualifications and cofactor identity.

**Remaining scientific requirement:** SAM orientation studies and tetrel examples constrain what geometry cannot prove. Explicit-H treatment and methyl vs sulfur motif domain review.

**Remaining scientific requirement:** Published sulfonium structural/QM studies; local environments and compound classes not universal energy calibration. SAM graph identity and state-specific angular domain review.

Source SAM/SAH identity and sulfonium/methyl/aromatic orientation must remain separate. Existing graph/groups/geometry can preserve selected measurements; no universal SAM-aromatic window or ammonium-to-sulfonium transfer is justified.

### G06 — SAM methyl-transfer geometry

Review selected nucleophile/SAM state and substrate-specific transfer geometry; linearity is not reactivity.

**Qualification:** Disposition of scope/review need; no implemented capability implied.

**Existing implementation:** ATHENA.SAM_MTASE.TRANSFER_GEOMETRY

**Supporting source:** Attached advanced-rule request; existing Athena sources; no external parity asserted

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.SAM_MTASE.TRANSFER_GEOMETRY.json) · SHA256 `6248ec4622ccb577a1f499cb3724990c92ab1cb7a272d0d46aa56d03d5a421a5`

**Remaining scientific requirement:** Linear approach can also occur in nonreactive tetrel examples; substrate-specific windows require separate empirical study. General Nu API needs separately reviewed extension, state perception and substrate-specific reference data.

Raw selected donor-methyl/nucleophile distances and approach angles are supported. General SAM/substrate identity and reaction-competent windows require exact state/role definitions; no catalysis inferred from a linear tuple.

### G07 — n-to-pi-star motif

Adjacent source backbone-amide geometric candidate, O1-C2<=3.2A and99<=O1-C2-O2<=119deg; cyclic dipeptide role alias included, no orbital/energetic claim.

**Qualification:** 32 focused tests and1795 selected regression/consumer tests, two independent JVM replays and65 historical golden files. Fresh committed-source follow-up pending.

**Existing implementation:** ATHENA.INT.N_PI_STAR

**Supporting source:** docs/manual/athena/supporting-material/PEPTIDE_GEOMETRY.md; pinned wwPDB guide and Bartlett et al.2010 Fig2a/Methods.

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.INT.N_PI_STAR.json) · SHA256 `02937d6acaa7b4439c1fa11f3466b0360d78578a384c02cb60ec6e2b4e6810d9`

### G08 — Typed H-bond/charge/cofactor/second-shell networks

Same-state qualified typed evidence edges and bounded simple paths; exhaustive coverage for no-path. No new chemistry, coupling or mechanism.

**Qualification:** Bounded approved operational definition;1932+3 clean-source tests,56 focused tests,9 independent replay comparisons and65 historical files exact. No production scientific receipt.

**Existing implementation:** EventAnalysisRules/EventInputs/EventCounts/EventPaths + governed evaluateCurrent

**Supporting source:** Attached advanced-rule request; existing Athena sources; no external parity asserted

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.NETWORK.CHARGE.json) · SHA256 `b8c2757c820122dc58dd24547ebf32565c6dd2d35d0ca4201d915f421265ddc5`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.NETWORK.COFACTOR_MEDIATED.json) · SHA256 `7cf05c7004bbe668ad853dd00cc9e7731b72db2ba17ccaffeab56f0db1fb476e`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.NETWORK.HBOND.json) · SHA256 `27208503311cff09b5839c4ca722eb791383eb5bc2079ddf23049983c877a9dc`

Qualified typed-edge topology only; no new water/cofactor/sulfur chemistry, energy transfer or mechanism.

### F01 — Functional group: Acids/carboxylates

Reviewed carboxylic-acid/carboxylate source identity domains; no normalization.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** Existing OCL SubstructureMatcher; proposed ATHENA.GROUP.* descriptors

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.CARBOXYLATE.json) · SHA256 `120a1f195039521a18e292001b45d0be993291edace2cb4d96a50e07bf160a0d`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.CARBOXYLIC_ACID.json) · SHA256 `083a7bc55823e8462abe7f0985926ca9e7c8ffa7db7ca8b6231db6757bc34148`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### F02 — Functional group: Amides and methyl amides

NH2/NH1/NH0 neutral amide identity domains with preserved attachments; no separately qualified methyl-amide substitution rule.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** Existing OCL SubstructureMatcher; proposed ATHENA.GROUP.* descriptors

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.AMIDE.json) · SHA256 `047c3049e8037c894797c17b6e617e5ecfc1ef50b6d416fa527cf7e83790205b`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### F03 — Functional group: Esters/acyl linkages

Original B01 ester identity and overlapping carbonyl; broader acyl transformations excluded.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** Existing OCL SubstructureMatcher; proposed ATHENA.GROUP.* descriptors

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.ESTER.json) · SHA256 `37e2506fb154d5f88c12c10b2de508dc8eef7e9bf40857b347aa3708c93279ac`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### F04 — Functional group: Aldehydes/ketones/carbonyls

Reviewed aldehyde/ketone/carbonyl graph identities, not arbitrary resonance normalization.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** Existing OCL SubstructureMatcher; proposed ATHENA.GROUP.* descriptors

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.CARBONYL.json) · SHA256 `37d81bf5565fae5d543126d8459abb27de4de5c1427eb0065e66b7484d0a8ec1`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### F05 — Functional group: Acid/sulfonyl chlorides

Exact neutral carbon-bound acyl chloride and sulfonyl chloride source identities; approved candidate representation/state exclusions unchanged.

**Qualification:** 949 focused/regression checks, independent replay and preserved historical evidence; implementation-qualified within reviewed domain, no production receipt.

**Existing implementation:** Existing B00 / athena.group/2; opt-in groups-adopted-v1 production manifests

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

### F06 — Functional group: Amine subclasses

Reviewed neutral amine and carbon-bound ammonium subclasses with explicit state coverage.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** Existing OCL SubstructureMatcher; proposed ATHENA.GROUP.* descriptors

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.AMINE.json) · SHA256 `4fe20a026c3a571b5bc8070de3fb21052adf5fad2546ac7d9a400f41ffb0a25b`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### F07 — Functional group: Imines/oximes/nitroso

Nine exact source identities; acyclic/exocyclic/endocyclic neutral imines included. Existing aromatic identities reused. No universal imine/charged-resonance or current-policy receipt claim.

**Qualification:** 33 F07 tests; 982 focused/regression checks; fresh committed-source whole foundation 1740/1740 plus 3/3 isolation; independent-JVM and historical replay, preservation.

**Existing implementation:** FunctionalGroupRules athena.group/2; B00 matcher; nine opt-in groups-f07-v1 manifests; existing heteroaromatic identities.

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

**Remaining scientific requirement:** Charged iminium/resonance, heteroatom-substituted carbon and additional N-hetero identities need separate exact membership/state definitions; existing aromatic cycle-domain boundaries remain explicit.

### F08 — Functional group: Azo/hydrazine/diazo/azide

Fourteen exact N-N source-state identities; neutral hydrazine/diazene H variants, two diazo depictions, diazonium, two azide depictions; cyclic/shared attachments included. Other charged/aromatic N-N states need separate exact definitions, not ring-engine work.

**Qualification:** 1832/1832 implemented-foundation consumer tests including92 new tests; five independent JVM comparisons. Fresh committed-source qualification follows this source commit; no production receipt.

**Existing implementation:** Existing OCL SubstructureMatcher; proposed ATHENA.GROUP.* descriptors

**Supporting source:** Pinned RDKit motifs refined into explicit source-state predicates; docs/manual/athena/supporting-material/SOURCE_CHEMISTRY_CLOSURE.md

### F09 — Functional group: Nitriles

Reviewed nitrile graph identity only.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** Existing OCL SubstructureMatcher; proposed ATHENA.GROUP.* descriptors

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### F10 — Functional group: Nitro groups

Reviewed supplied nitro representation; charge separation is not automatically a charged interaction group.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** Existing OCL SubstructureMatcher; proposed ATHENA.GROUP.* descriptors

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### F11 — Functional group: Sulfonamides

Reviewed sulfonamide graph identities; no universal donor/acceptor classification.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** Existing OCL SubstructureMatcher; proposed ATHENA.GROUP.* descriptors

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### F12 — Functional group: Sulfonic acid/sulfonate ester/sulfone

Literal neutral S(=O)2 carbon-bound sulfone identity only; sulfonic acid/sulfonate ester are not implemented by that definition.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** Existing OCL SubstructureMatcher; proposed ATHENA.GROUP.* descriptors

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### F13 — Functional group: Sulfoxide/thioether/thiol/thiocarbonyl

Reviewed thiol/thiolate/thioether/sulfoxide graph domains; no sulfur interaction, arbitrary oxidation/resonance or thiocarbonyl expansion.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** Existing OCL SubstructureMatcher; proposed ATHENA.GROUP.* descriptors

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.SULFOXIDE.json) · SHA256 `2fe6b32f45dbe5bc014f5f5e13e4c3b96a6ee757daaf74e8d2dda32f145dee24`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.THIOETHER.json) · SHA256 `ee158666e9f07c58ac5b01b4233a542f1f7ee8a86ba96dba327836caf434d7c3`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.THIOL.json) · SHA256 `bae70357e39dea24b3e9fe8228592574eacdbc3c200e6bd9bf82c8b4db3bb7c4`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.THIOLATE.json) · SHA256 `805796ea08144a329e4dde12e5c4cad143ba1a592a70b5803a17391a09127987`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### F14 — Functional group: Isocyanate/isothiocyanate

Exact neutral carbon-bound R-N=C=O and R-N=C=S source identities; approved candidate representation/state exclusions unchanged.

**Qualification:** 949 focused/regression checks, independent replay and preserved historical evidence; implementation-qualified within reviewed domain, no production receipt.

**Existing implementation:** Existing B00 / athena.group/2; opt-in groups-adopted-v1 production manifests

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

### F15 — Functional group: Boron motifs

B(OH)2/B(OH)(OR)/B(OR)2 plus separate B(-1) four-SINGLE-heavy-bond source predicate; cyclic/shared attachments included. No speciation or normalization.

**Qualification:** 1832/1832 implemented-foundation consumer tests including92 new tests; five independent JVM comparisons. Fresh committed-source qualification follows this source commit; no production receipt.

**Existing implementation:** Existing OCL SubstructureMatcher; proposed ATHENA.GROUP.* descriptors

**Supporting source:** Pinned RDKit motifs refined into explicit source-state predicates; docs/manual/athena/supporting-material/SOURCE_CHEMISTRY_CLOSURE.md

### F16 — Functional group: Alcohol/phenol/ether motifs

Reviewed alcohol/phenol/ether identities and exact state domains only.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** Existing OCL SubstructureMatcher; proposed ATHENA.GROUP.* descriptors

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.ALCOHOL.json) · SHA256 `44c4f5095343264c2b3e1a934c1ca406e03f7205b58ee41bfa363a0191f71715`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.ETHER.json) · SHA256 `8c5f9b0cffa82555ebefb57c293cb5496f82838744bcc4a34e0d155c578d9888`

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.PHENOL.json) · SHA256 `1868fd7684192688ee5c58b117f29eef3f6330c906246c221edbb872f69be5b6`

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### F17 — Functional group: Halogenated motifs

Existing qualified carbon-bound F/Cl/Br/I identity and exact attachment roles. No halogen bond, unusual valence or ionization claim.

**Qualification:** Implementation checkpoint within explicit domain only; no current-policy scientific receipt granted.

**Existing implementation:** Existing OCL SubstructureMatcher; proposed ATHENA.GROUP.* descriptors

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.HALOGENATED.json) · SHA256 `27c940e949327cf2f5c38b4a2a7ec856c1a5588b5175b6922bc313a090fca0ae`

Not qualified by this disposition; exact manifest and preserved group checkpoint define limits.

### F18 — Functional group: Terminal alkyne

Exact source C#C terminal-H identity; distinct occurrences and acetylene correspondence alternatives preserved. Generic alkyne identity remains unchanged.

**Qualification:** 949 focused/regression checks, independent replay and preserved historical evidence; implementation-qualified within reviewed domain, no production receipt.

**Existing implementation:** Existing B00 / athena.group/2; opt-in groups-adopted-v1 production manifests

**Supporting source:** FunctionalGroups.txt; Functional_Group_Hierarchy.txt; OCL AtomFunctionAnalyzer

Not qualified by this disposition. Consult pinned checkpoint limitations and linked research dossier; broader behavior requires explicit review and separate implementation qualification.

### F19 — Functional group: Branched alkyl/cyclopropyl

Carbon branch-point degree3/4, exact tert-butyl membership, carbon SINGLE-bond triangle; substituted/fused/spiro included. Maximal alkyl-fragment boundary is not inferred.

**Qualification:** 1832/1832 implemented-foundation consumer tests including92 new tests; five independent JVM comparisons. Fresh committed-source qualification follows this source commit; no production receipt.

**Existing implementation:** Existing OCL SubstructureMatcher; proposed ATHENA.GROUP.* descriptors

**Supporting source:** Pinned RDKit motifs refined into explicit source-state predicates; docs/manual/athena/supporting-material/SOURCE_CHEMISTRY_CLOSURE.md

[Research dossier](../../../software/qualification/rule-qualification-blueprint-20261005/dossiers/ATHENA.GROUP.ALKYL.json) · SHA256 `ec6bd407031c2ef7227148de29dacf2351e972ae5ea3f8b7966f6331f6506af7`
