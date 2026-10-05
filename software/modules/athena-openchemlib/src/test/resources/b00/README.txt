B00 synthetic qualification corpus, version1,2026-10-05.
Handwritten element/bond/charge target graphs are constructed directly as MolecularGraph;
no target SMILES parser or RDKit-generated expected output is used. Coordinates are
synthetic preservation markers, not chemical conformers or fitted structures.

query-cases.tsv records expected OBSERVED OCL unique-mode behavior, reviewed against
pinned OCL source and explicit structural witnesses. It includes limitations and
counterexamples, not only successful chemical predicates. The agent performed this
fixture/source review; no separate user approval of each fixture is asserted.
RequiredContractProbe.java (in the qualification checkpoint) independently expresses
four B00 requirements and FAILS on the unchanged adapter. Do not mistake the passing
characterization suite for qualification of those requirements.

Every row retains literal pattern text, fixture identity, exact expected target-ID
correspondences in compiled-query-index order and scope. These patterns are synthetic
query challenges, not RDKit FDef group truth or production ATHENA.GROUP rules.
The replay harness writes query SHA-256, original graph SHA-256, original graph and
complete result evidence. JSON sorting is test-artifact canonicalization only.
Cross-engine agreement/disagreement was NOT executed; no RDKit/OpenFF/ProLIF parity.
