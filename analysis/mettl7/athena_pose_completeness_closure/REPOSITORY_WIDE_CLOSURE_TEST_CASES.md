# Repository-wide closure test cases — frozen before implementation

These acceptance cases are frozen before repairing the four remaining work areas. Scientific thresholds and docking inputs are out of scope.

## Historical receipt recomputation

1. **Coordinate models are authoritative:** given a Vina log with nine score rows and a PDBQT with eight models, the versioned receipt must record eight expected/parsed poses.
2. **Exact stage accounting:** for every run, `expected == parsed == profiled == emitted`; any inequality fails closed.
3. **Zero-interaction preservation:** a valid final model with no Athena interactions still produces one output row.
4. **Metadata-only correction:** old receipt count differs, while PDBQT/model and postprocessed rows agree; classify `METADATA_CORRECTED`.
5. **Unchanged evidence:** old receipt, PDBQT and emitted rows already agree; classify `UNCHANGED_EVIDENCE`.
6. **Reprocessing required:** PDBQT/model count and postprocessed rows differ, or required artifacts/hashes are unavailable; classify `REQUIRES_REPROCESSING`.
7. **History preservation:** old receipt remains byte-identical at a superseded path; new receipt has a new version and a provenance link.
8. **No redocking:** recomputation never invokes Vina and never changes PDBQT bytes or hashes.

## Stale checksum lifecycle

9. **Failed rerun after prior success:** an existing `FINAL_SHA256SUMS` cannot remain success-authoritative after validation fails.
10. **Explicit failure receipt:** failed validation writes a versioned failure receipt containing reasons and prior-success disposition.
11. **Atomic success:** a successful rerun emits the final checksum only after all checks pass.
12. **Interrupted validation:** temporary output cannot replace the last complete version and cannot masquerade as success.

## Daedalus unit/integration separation

13. **Default lifecycle exclusion:** `mvn test` must not execute classes tagged `integration` or `docking`.
14. **Explicit inclusion:** the integration profile must discover and execute the excluded docking tests.
15. **No external process in unit suite:** a build check fails if ordinary unit tests invoke Vina, fpocket, subprocess runners, or external executable discovery.
16. **Profile is opt-in:** absence of the explicit profile/property cannot trigger integration tests.

## BioJava hermeticity

17. **No download provider in ordinary tests:** default tests must not instantiate `DownloadChemCompProvider`.
18. **Offline deterministic fixture:** ligand preparation and PDB parsing tests pass with a local/stub `ChemCompProvider` and deliberately unavailable network.
19. **Optional online test isolation:** any network-enabled BioJava test is tagged integration and excluded by default.
20. **No hidden fallback dependence:** unit assertions are identical whether DNS/network is available or unavailable.

## Final repository acceptance

21. **Historical coverage:** the recomputation manifest accounts for exactly the frozen 66 affected unique runs without duplicates.
22. **Checksums:** all new receipts, superseded-receipt copies, manifests and reports verify.
23. **Clean default suite:** complete default reactor `mvn test` passes without docking or network access.
24. **Closure receipt:** the final receipt can be true only when all 23 preceding checks pass and wider-audit open count is zero.
