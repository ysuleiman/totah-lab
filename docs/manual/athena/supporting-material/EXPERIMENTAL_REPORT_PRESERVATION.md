# Experimental validation: preserve reports independently of native evaluation

V19 (diffraction/refinement), V20 (local X-ray map/model fit), V21 (NMR shifts/restraints/ensembles), and V22 (EM map/model validation) share an already-existing storage path: `EvidenceEnvelope` → `EvidenceExchange` → `EvidenceAdmission` → append-only `EvidenceSnapshotCatalog` → explicit-snapshot `EvidenceQueries`.

`ExperimentalReportPreservationTest` qualifies that path with four synthetic report families. Source bytes including unknown fields, NUL, Unicode and line endings survive exactly; method, artifact hash, source location and structural-state binding remain attributed. Re-admission is idempotent. Contradictory reports coexist. UNSUPPORTED/FAILED interpretations preserve the input; NOT_EVALUATED is not FALSE. Same bytes attributed to different source states are not merged. Missing or tampered external artifacts fail verification rather than becoming fabricated metrics. No new report parser, schema, validation calculator or evidence store is introduced.

## Scientific support and deliberately separate computation

Pinned wwPDB validation guides (`scientific-rule-knowledge-audit-20261004/reference/wwpdb-xray.txt`, `wwpdb-nmr-retry.txt`, `wwpdb-em-beta.txt`) and their version/retrieval digests in `FIVE_PILLAR_SOURCES.json` describe experimental validation inputs. These motivate provenance separation; they do not supply a dataset for Athena-native metric qualification.

- V19 requires actual indexed diffraction observations with uncertainties, free-set flags, crystal/unit-cell/symmetry, resolution and refinement protocol/model correspondence. A coordinate file cannot reconstruct them.
- V20 requires the relevant experimental map/coefficients and model, grid/origin/sampling, frame alignment, metric definition and source version. Global structural geometry is not local density fit.
- V21 requires attributed experimental shifts/restraints, ambiguity/assignment handling, units and ensemble/model correspondence. Similarity among modeled structures is not agreement with NMR observations.
- V22 requires identified EM maps (and independent half maps/masks where the metric requires them), sampling/origin/frame/resolution and model alignment. A model alone cannot establish map quality or overfitting.

**ADOPT:** lossless attributed report preservation, experimental input requirements, independently versioned interpretation.

**REJECT:** inferring missing metrics from coordinates, equating ingestion with scientific validation, selecting the newest contradictory result as truth.

**UNSUPPORTED without supplied data/method:** Athena-native experimental metric computation. Report ingestion is qualified by architecture and tests; this does not close native computation or authorize invention of measured numbers.

External publication/report licensing remains applicable; retain links, hashes and provenance when redistribution is inappropriate. Synthetic fixtures carry no real experimental claim.
