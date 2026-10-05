#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?New output directory}
test ! -e "$out"
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
basecp=$(cat software/qualification/p03-role-closure-20261005/run-1/classpath.txt)
cp="$out/classes:$basecp"
"$j/javac" -proc:none --release 21 -cp "$basecp" -d "$out/classes" \
software/modules/athena/src/main/java/totah/lab/athena/design/backend/MolecularValidationService.java \
software/modules/athena/src/main/java/totah/lab/athena/system/SystemGraphValidation.java \
software/modules/athena-openchemlib/src/main/java/totah/lab/athena/design/backend/ocl/OclValidationDimensions.java \
software/modules/athena-openchemlib/src/main/java/totah/lab/athena/design/backend/ocl/OclMolecularBackend.java \
software/modules/daedalus/src/main/java/totah/lab/daedalus/system/SystemQualificationPipeline.java \
software/modules/daedalus/src/test/java/totah/lab/daedalus/system/DimensionalValidationAcceptanceTest.java \
software/modules/daedalus/src/test/java/totah/lab/daedalus/system/DisconnectedValidationAcceptanceTest.java \
software/qualification/validation-dimensions-20261005/Corrected*Test.java
printf '%s' "$cp" > "$out/classpath.txt"
"$j/java" -Xmx384m -jar /Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar execute --class-path "$cp" \
--select-class totah.lab.daedalus.system.DimensionalValidationAcceptanceTest \
--select-class totah.lab.daedalus.system.DisconnectedValidationAcceptanceTest \
--select-class totah.lab.daedalus.system.SystemQualificationAcceptanceTest \
--select-class totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest \
--select-class totah.lab.athena.design.backend.ocl.OclCorrectedOccurrenceTest \
--select-class totah.lab.athena.design.backend.ocl.OclBenchmarkChemistryRegressionTest \
--select-class totah.lab.athena.design.backend.ocl.PocketFragmentReplacementAcceptanceTest \
--select-class totah.lab.athena.design.backend.ocl.HypothesisDirectedReplacementAcceptanceTest \
--select-class totah.lab.athena.design.backend.ocl.CorrectedPocketFragmentReplacementAcceptanceTest \
--select-class totah.lab.athena.design.backend.ocl.CorrectedHypothesisDirectedReplacementAcceptanceTest \
--select-class totah.lab.athena.design.backend.ocl.CorrectedOclBenchmarkChemistryRegressionTest \
--select-method totah.lab.athena.design.backend.ocl.CorrectedBackendConsumerTest#canonicalIdentityAndSanitizationAreStableForAromaticAndChargedGraphs \
--select-method totah.lab.athena.design.backend.ocl.CorrectedBackendConsumerTest#explicitParitySurvivesSanitizationRoundTrip \
--select-method totah.lab.athena.design.backend.ocl.CorrectedBackendConsumerTest#positiveHydrogenCountsMustMatchInferredHydrogensAndArePreserved \
--details summary --disable-ansi-colors --reports-dir "$out/junit" > "$out/tests.log" 2>&1
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.DimensionalValidationAcceptanceTest "$out/one.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.DimensionalValidationAcceptanceTest "$out/two.json"
cmp "$out/one.json" "$out/two.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest "$out/scientific-replay"
diff -qr "$out/scientific-replay" software/qualification/ocl-query-b00-corrected-20261005/final-run/scientific-replay-one > "$out/scientific-diff.txt"
cat "$out/tests.log"

"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.DisconnectedValidationAcceptanceTest "$out/disconnected-one.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.DisconnectedValidationAcceptanceTest "$out/disconnected-two.json"
cmp "$out/disconnected-one.json" "$out/disconnected-two.json"
