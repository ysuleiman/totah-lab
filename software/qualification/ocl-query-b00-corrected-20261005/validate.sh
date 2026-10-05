#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
q=software/qualification/ocl-query-b00-corrected-20261005
out=${1:?Supply a NEW output directory; checkpoint outputs must not be overwritten}
test ! -e "$out"
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
junit=/Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
cp="software/qualification/ocl-query-b00-20261005/classes:software/qualification/athena-scientific-rules-20261004/classes:software/modules/athena/src/main/resources:software/modules/athena-openchemlib/src/test/resources:$(cat software/qualification/fragment-region-fit-20261004/runtime-classpath.txt)"
{
 printf '%s\n' software/modules/athena-openchemlib/src/main/java/totah/lab/athena/design/backend/ocl/{OclMolecularBackend,OclGraphMapper,OclOccurrenceMatcher}.java
 printf '%s\n' software/modules/athena/src/main/java/totah/lab/athena/system/rules/{AthenaScientificRules,RuleRegistry}.java
 printf '%s\n' software/modules/athena-openchemlib/src/test/java/totah/lab/athena/design/backend/ocl/{OclMolecularBackendAcceptanceTest,OclQuerySemanticsQualificationTest,OclCorrectedOccurrenceTest}.java
 rg --files software/modules/daedalus/src/test/java/totah/lab/daedalus/system -g '*.java'
 printf '%s\n' software/qualification/ocl-query-b00-consumer-gate-20261005/ConsumerImpactProbe.java
} > "$out/sources.txt"
"$j/javac" -proc:none --release 21 -cp "$cp" -d "$out/classes" @"$out/sources.txt"
cp="$out/classes:$cp"
printf '%s' "$cp" > "$out/classpath.txt"
args=(--select-package totah.lab.daedalus.system --select-class totah.lab.athena.design.backend.ocl.OclQuerySemanticsQualificationTest --select-class totah.lab.athena.design.backend.ocl.OclCorrectedOccurrenceTest --select-class totah.lab.athena.design.backend.ocl.RequiredContractProbe --select-class totah.lab.athena.design.backend.ocl.PocketFragmentReplacementAcceptanceTest --select-class totah.lab.athena.design.backend.ocl.FragmentRegionFitAcceptanceTest --select-class totah.lab.athena.design.backend.ocl.HypothesisDirectedReplacementAcceptanceTest --select-class totah.lab.athena.design.backend.GraphEditTransactionEngineTest --select-class totah.lab.athena.design.backend.TopologyEditTransactionEngineTest)
for method in canonicalIdentityAndSanitizationAreStableForAromaticAndChargedGraphs smartsReturnsStableProjectAtomIds unsupportedStereoDescriptorFailsExplicitly explicitParitySurvivesSanitizationRoundTrip correspondenceRepairsStableIdsAcrossDifferentAtomOrders symmetryAndTetrahedralParityAreProvedByTheBackend unsupportedHydrogenCountsAndBondStereoFailClosed chargedProductsRetainMultipleReceiptedDerivationsThroughOcl positiveHydrogenCountsMustMatchInferredHydrogensAndArePreserved existingNetChargeValidationLimitationIsExplicit; do
 args+=(--select-method "totah.lab.athena.design.backend.ocl.OclMolecularBackendAcceptanceTest#$method")
done
"$j/java" -Xmx384m -jar "$junit" execute --class-path "$cp" "${args[@]}" --reports-dir "$out/junit" --details summary --disable-ansi-colors > "$out/tests.log" 2>&1
"$j/java" -Xmx256m -cp "$cp" totah.lab.athena.design.backend.ocl.OclCorrectedOccurrenceTest "$out/replay-one.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.athena.design.backend.ocl.OclCorrectedOccurrenceTest "$out/replay-two.json"
cmp "$out/replay-one.json" "$out/replay-two.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest "$out/scientific-replay-one"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest "$out/scientific-replay-two"
diff -qr "$out/scientific-replay-one" "$out/scientific-replay-two" > "$out/scientific-replay-diff.txt"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.ConsumerImpactProbe "$out/glyoxal-journal" > "$out/glyoxal-result.txt"
cat "$out/tests.log"
