#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?Supply a NEW output directory}
test ! -e "$out"
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
junit=/Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
basecp=$(cat software/qualification/ocl-query-b00-corrected-20261005/final-run/classpath.txt)
cp="$out/classes:$basecp"
printf '%s\n' software/modules/athena/src/main/java/totah/lab/athena/system/rules/{FunctionalGroupRules,RuleRegistry,RuleAnalyzers}.java software/modules/daedalus/src/test/java/totah/lab/daedalus/system/B01FunctionalGroupAcceptanceTest.java > "$out/sources.txt"
"$j/javac" -proc:none --release 21 -cp "$basecp" -d "$out/classes" @"$out/sources.txt"
printf '%s' "$cp" > "$out/classpath.txt"
args=(--select-package totah.lab.daedalus.system --select-class totah.lab.athena.design.backend.ocl.OclQuerySemanticsQualificationTest --select-class totah.lab.athena.design.backend.ocl.OclCorrectedOccurrenceTest --select-class totah.lab.athena.design.backend.ocl.RequiredContractProbe --select-class totah.lab.athena.design.backend.ocl.PocketFragmentReplacementAcceptanceTest --select-class totah.lab.athena.design.backend.ocl.FragmentRegionFitAcceptanceTest --select-class totah.lab.athena.design.backend.ocl.HypothesisDirectedReplacementAcceptanceTest --select-class totah.lab.athena.design.backend.GraphEditTransactionEngineTest --select-class totah.lab.athena.design.backend.TopologyEditTransactionEngineTest)
for method in canonicalIdentityAndSanitizationAreStableForAromaticAndChargedGraphs smartsReturnsStableProjectAtomIds unsupportedStereoDescriptorFailsExplicitly explicitParitySurvivesSanitizationRoundTrip correspondenceRepairsStableIdsAcrossDifferentAtomOrders symmetryAndTetrahedralParityAreProvedByTheBackend unsupportedHydrogenCountsAndBondStereoFailClosed chargedProductsRetainMultipleReceiptedDerivationsThroughOcl positiveHydrogenCountsMustMatchInferredHydrogensAndArePreserved existingNetChargeValidationLimitationIsExplicit; do
 args+=(--select-method "totah.lab.athena.design.backend.ocl.OclMolecularBackendAcceptanceTest#$method")
done
"$j/java" -Xmx384m -jar "$junit" execute --class-path "$cp" "${args[@]}" --reports-dir "$out/junit" --details summary --disable-ansi-colors > "$out/tests.log" 2>&1
mkdir -p "$out/b01-journal-one" "$out/b01-journal-two"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest "$out/b01-one.json" "$out/b01-journal-one"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest "$out/b01-two.json" "$out/b01-journal-two"
cmp "$out/b01-one.json" "$out/b01-two.json"
diff -qr "$out/b01-journal-one" "$out/b01-journal-two" > "$out/b01-journal-diff.txt"
"$j/java" -Xmx256m -cp "$cp" totah.lab.athena.design.backend.ocl.OclCorrectedOccurrenceTest "$out/b00-replay.json"
cmp "$out/b00-replay.json" software/qualification/ocl-query-b00-corrected-20261005/final-run/replay-one.json
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest "$out/scientific-replay"
diff -qr "$out/scientific-replay" software/qualification/ocl-query-b00-corrected-20261005/final-run/scientific-replay-one > "$out/scientific-diff.txt"
cat "$out/tests.log"
