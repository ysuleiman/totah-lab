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
find software/modules/athena/src/main/java/totah/lab/athena/system/rules/research -name '*.java' | sort > "$out/sources.txt"
printf '%s\n' software/modules/athena/src/main/java/totah/lab/athena/system/rules/{FunctionalGroupRules,RuleRegistry,RuleAnalyzers,RuleManifest,VerifiedScientificRule,AthenaScientificRules}.java software/modules/daedalus/src/main/java/totah/lab/daedalus/system/{RuleExecutionPipeline,CurrentRuleExecution}.java software/modules/daedalus/src/test/java/totah/lab/daedalus/system/{AthenaScientificRulesAcceptanceTest,B01FunctionalGroupAcceptanceTest,ResearchGatePipelineAcceptanceTest,FoundationGroupAcceptanceTest,GroupContextAcceptanceTest}.java software/modules/daedalus/src/test/java/totah/lab/athena/system/rules/research/ResearchGateAcceptanceTest.java >> "$out/sources.txt"
"$j/javac" -proc:none --release 21 -cp "$basecp" -d "$out/classes" @"$out/sources.txt"
printf '%s' "$cp" > "$out/classpath.txt"
args=(--select-package totah.lab.daedalus.system --select-class totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest --select-class totah.lab.athena.design.backend.ocl.OclQuerySemanticsQualificationTest --select-class totah.lab.athena.design.backend.ocl.OclCorrectedOccurrenceTest --select-class totah.lab.athena.design.backend.ocl.RequiredContractProbe --select-class totah.lab.athena.design.backend.ocl.PocketFragmentReplacementAcceptanceTest --select-class totah.lab.athena.design.backend.ocl.FragmentRegionFitAcceptanceTest --select-class totah.lab.athena.design.backend.ocl.HypothesisDirectedReplacementAcceptanceTest --select-class totah.lab.athena.design.backend.GraphEditTransactionEngineTest --select-class totah.lab.athena.design.backend.TopologyEditTransactionEngineTest)
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

"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.ResearchGatePipelineAcceptanceTest "$out/gate-journal-one" "$out/gate-one.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.ResearchGatePipelineAcceptanceTest "$out/gate-journal-two" "$out/gate-two.json"
cmp "$out/gate-one.json" "$out/gate-two.json"
diff -qr "$out/gate-journal-one" "$out/gate-journal-two" > "$out/gate-replay-diff.txt"

mkdir -p "$out/context-journal-one" "$out/context-journal-two"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.GroupContextAcceptanceTest "$out/context-one.json" "$out/context-journal-one"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.GroupContextAcceptanceTest "$out/context-two.json" "$out/context-journal-two"
cmp "$out/context-one.json" "$out/context-two.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.FoundationGroupAcceptanceTest "$out/foundation.json"
cmp "$out/foundation.json" software/qualification/foundation-groups-20261005/qualified-one.json
diff -qr "$out/context-journal-one" "$out/context-journal-two" > "$out/context-journal-diff.txt"
