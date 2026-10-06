# Source peptide geometry: V12 and G07

These are separately named operational screens over source-attributed peptide roles. `PeptideGeometryRules` composes the existing group `/2` verifier (opt-in source-H implementation4) and continuous geometry V3. It adds no molecular representation, matcher, geometry solver, public Java API or payload schema. Raw `athena-continuous-geometry/1` reports survive independently of classification.

Exact definitions and embedded dependency manifests are in `peptide-geometry-v1`, rendered by the Scientific Rule Reference. [Qualification](../../../../software/qualification/v12-peptide-source-geometry-20261006/CHECKPOINT.txt) records execution; this document explains the scientific basis. [Delegated adoption](../../../../software/qualification/foundation-closure-execution-20261006/REQUEST.txt) does not fabricate a human review signature, expiry or production receipt.

## V12: selected source peptide omega

The pinned [wwPDB X-ray validation guide](../../../../software/qualification/scientific-rule-knowledge-audit-20261004/reference/wwpdb-xray.txt), chirality/planarity section (snapshot lines1954–1958), defines peptide planarity deviation as omega differing by more than30° from cis0° or trans180°. Source URL/version/retrieval and hash remain in the five-pillar source map; the exact snapshot digest is in the manifest.

**ADOPT:** this explicit operational window, inclusive at30° from either ideal. Report the signed CA1–C1–N2–CA2 omega and nearest-ideal deviation. `SUPPORTED_PRESENT` means outside both windows; `ABSENT_FALSE` means the selected evaluable tuple is inside either window. Neither status declares biological correctness. Cis-proline is not automatically rejected; no cis/trans preference or energy is assigned.

**MODIFY:** verify the supplied alpha-backbone linkage with B00/group evidence, rather than assigning roles from residue names or sequence adjacency. Glycine, substituted alpha carbons, proline-like ring substitution and cyclic peptides are permitted. All component correspondence alternatives must support the selected tuple; no arbitrary map is promoted to truth.

**UNSUPPORTED:** non-alpha backbones or distinct charged/resonance peptide-bond representations without an exact corresponding role definition. Unknown H/state, absent source roles, undefined torsion or unqualified frame is inconclusive, not an absence of unusual peptide geometry.

## G07: adjacent-backbone n→π* geometric candidate

Primary source: Gail J. Bartlett, Amit Choudhary, Ronald T. Raines and Derek N. Woolfson, “n→π* Interactions in Proteins,” *Nature Chemical Biology*6,615–620(2010), DOI **10.1038/nchembio.406**, PMCID **PMC2921280**. The pinned [full-text artifact L20](../../../../software/qualification/advanced-rule-research-design-20261004/reference/L20.html) and its source metadata are retained in the existing advanced-rule dossier/source map.

Figure2a and Methods define the operational structural screen for consecutive backbone carbonyls: O(previous)–C(next) distance≤3.2Å, and O(previous)–C(next)–O(next) angle99–119° inclusive. The paper analyzed1731 nonredundant high-resolution structures and also performed electronic calculations. Athena adopts the stated geometric screen; it has not rerun or reconstructed that empirical survey or its quantum calculations. No empirical probability distribution is claimed here. The paper explicitly discusses interactions outside its geometric window, so failure of the screen cannot establish absence of orbital donation.

**ADOPT:** the exact distance/angle window as a candidate predicate for supplied adjacent backbone amides. The acceptor must be an amide carbonyl with an explicit next nitrogen, not merely any carbonyl O/C pair. Preserve distance, angle and acceptor carbon displacement from its O/CA/N substituent plane as separate measured quantities.

**MODIFY:** source-role attribution is graph-backed and independently checked. A cyclic dipeptide aliases N3 to N1; it has its own exact source pattern so an injective nine-atom query does not accidentally exclude its eight-atom backbone. Larger cyclic paths and proline-like substitution are also allowed. This inclusion establishes the same source-topology/geometry proposition, not a new experimentally calibrated cyclic-peptide energy claim. Symmetry-related role alternatives coexist.

**REJECT:** extrapolation of this backbone screen to arbitrary ligand carbonyls, sulfur donors, all n-donors, affinity, stabilization energy or causality. L21's sulfur mechanisms remain separate. No orbital population, NBO stabilization or reactivity is inferred.

**Negative coverage:** both selected source amides and their exact O1/C2/O2/CA2/N3 tuple must be established under every applicable component correspondence; complete finite common-frame distance and angle are required. These are selected-tuple negatives, not whole-protein absence. Optional plane degeneracy remains explicitly inconclusive even if the independent distance/angle candidate passes. An unavailable plane is never fabricated or upgraded by candidate status.

## Implementation and provenance

The shared adapter verifies one explicitly selected immutable group report and one explicit existing geometry plan, then reuses the continuous geometry collector. Evaluation checks the exact raw report/method against recomputation and retains its digest, source group reference/digest and definition digest. Parameters are declared in manifests, checked against the adopted definition and read from those manifests during classification. Historical rules and geometry versions are untouched. All inputs and resulting Findings use existing evidence/qualification transport; no pseudo-measurement or production receipt is invented.

The synthetic fixtures cover threshold neighborhoods and boundaries, sign, explicit and unknown H, source constitutional near misses, proline/cycles, cyclic-role aliasing, permutation, rigid transformation, degenerate vectors/planes, input ambiguity, tampering, nonactivation and durable pipeline read-back. Early fixture/adapter mistakes are preserved as characterization logs. No real protein or METTL7 evidence is interpreted by this qualification.

## I14: existing explicit metal/partner pair geometry

The same qualification batch verifies I14's raw descriptor using existing continuous geometry V3, without adding a calculator or interaction rule. Ten supplied metal elements/states (Zn, Mg, Ca, Fe, Cu, Co, Mn, Ni, Na, K) are synthetic witnesses for stable atom identity, coordinates and explicit pair scope. Distance remains continuous at30Å; no contact cutoff or coordination label is inferred. No radius is silently acquired, so radius-dependent gaps stay unavailable. Incomplete traversal and unqualified frame remain inconclusive while observations survive. Supplied atomic formal charge is not promoted to an inferred oxidation/coordination state.

This supports the **raw pair geometry** portion of I14 only. Donor typing, element/oxidation-specific proximity classes and coordination inference remain explicitly unqualified. P07 and I15 are unchanged. The source dossier's demand for justified chemical/coordination classification is not satisfied merely by measuring a distance; the catalog retains that remaining dependency.
