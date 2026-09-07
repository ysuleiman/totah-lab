# Regression tests

The Hermes parser suite covers one, eight, and nine models; explicit final `ENDMDL`; EOF immediately after the final atom block; trailing and absent trailing newline; duplicate docking scores; and distinct models with identical atom content.

The METTL7 postprocessor suite proves fail-closed behavior for receipt/model disagreement and proves that nine valid models produce nine rows even when the final model has zero receptor interactions and other models have duplicate scores/fingerprints. These tests would fail under the old status/hash-only completeness logic.

Command:

`mvn -pl hermes,mettl7 -am -Dtest=PdbqtReaderTest,Mettl7IncrementalPosePostProcessorTest -Dsurefire.failIfNoSpecifiedTests=false test`
