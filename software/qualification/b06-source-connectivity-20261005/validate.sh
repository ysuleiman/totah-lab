#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?New output directory}
test ! -e "$out"
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
basecp=$(cat software/qualification/b05-mixed-geometry-v3-20261005/run-1/classpath.txt)
cp="$out/classes:$basecp"
"$j/javac" -proc:none --release 21 -cp "$basecp" -d "$out/classes" \
software/modules/athena/src/main/java/totah/lab/athena/system/rules/{SourceSulfurConnectivityRules,RuleRegistry,RuleAnalyzers}.java \
software/modules/daedalus/src/test/java/totah/lab/daedalus/system/SourceSulfurConnectivityAcceptanceTest.java \
software/qualification/b06-connectivity-coverage-characterization-20261005/ConnectivityCoverageCharacterizationTest.java
printf '%s' "$cp" > "$out/classpath.txt"
"$j/java" -Xmx384m -jar /Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar execute --class-path "$cp" \
--select-class totah.lab.daedalus.system.SourceSulfurConnectivityAcceptanceTest \
--select-class totah.lab.daedalus.system.ConnectivityCoverageCharacterizationTest \
--select-class totah.lab.daedalus.system.ContinuousGeometryV3AcceptanceTest \
--select-class totah.lab.gaia.geometry.Plane3DCentroidTest \
--select-class totah.lab.geometry.Plane3DTest \
--select-class totah.lab.athena.interaction.PiStackingDetectorTest \
--select-class totah.lab.athena.interaction.PiCationDetectorTest \
--select-class totah.lab.daedalus.system.ContinuousGeometryV2AcceptanceTest \
--select-class totah.lab.daedalus.system.CentroidCharacterizationTest \
--select-class totah.lab.daedalus.system.AllMembersNonpolarAcceptanceTest \
--select-class totah.lab.daedalus.system.ChargeGroupAcceptanceTest \
--select-class totah.lab.athena.system.rules.ChargeGroupSumTest \
--select-class totah.lab.daedalus.system.AromaticSystemAcceptanceTest \
--select-class totah.lab.daedalus.system.ChargeNonpolarAcceptanceTest \
--select-class totah.lab.daedalus.system.DimensionalValidationAcceptanceTest \
--select-class totah.lab.daedalus.system.DisconnectedValidationAcceptanceTest \
--select-class totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest \
--select-class totah.lab.daedalus.system.FoundationVocabularyAcceptanceTest \
--select-class totah.lab.daedalus.system.RuleRegistryAcceptanceTest \
--select-class totah.lab.daedalus.system.ContinuousGeometryAcceptanceTest \
--select-class totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest \
--select-class totah.lab.athena.design.backend.ocl.OclCorrectedOccurrenceTest \
--details summary --disable-ansi-colors --reports-dir "$out/junit" > "$out/tests.log" 2>&1
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.SourceSulfurConnectivityAcceptanceTest "$out/replay-one.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.SourceSulfurConnectivityAcceptanceTest "$out/replay-two.json"
cmp "$out/replay-one.json" "$out/replay-two.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest "$out/scientific-replay"
diff -qr "$out/scientific-replay" software/qualification/ocl-query-b00-corrected-20261005/final-run/scientific-replay-one > "$out/scientific-diff.txt"
"$j/java" -cp "$cp" totah.lab.daedalus.system.CentroidCharacterizationTest "$out/observed-v1.json"
cmp "$out/observed-v1.json" software/qualification/p06-centroid-characterization-20261005/run/observed-v1.json
cat "$out/tests.log"
"$j/java" -cp "$cp" totah.lab.daedalus.system.ContinuousGeometryAcceptanceTest "$out/full-v1.json"
cmp "$out/full-v1.json" software/qualification/continuous-geometry-20261005/qualified-run/one.json
"$j/javac" -proc:none --release 21 -cp "$cp" -d "$out/classes" software/qualification/continuous-geometry-20261005/PlaneReplay.java
"$j/java" -cp "$cp" PlaneReplay > "$out/plane-replay.txt"
cmp "$out/plane-replay.txt" software/qualification/continuous-geometry-20261005/legacy-replay/before.txt

"$j/java" -cp "$cp" totah.lab.daedalus.system.ContinuousGeometryV2AcceptanceTest "$out/v2.json"
cmp "$out/v2.json" software/qualification/p06-centroid-v2-20261005/final-run/replay-one.json

"$j/java" -cp "$cp" totah.lab.daedalus.system.ConnectivityCoverageCharacterizationTest "$out/legacy-sulfur.json"
cmp "$out/legacy-sulfur.json" software/qualification/b06-connectivity-coverage-characterization-20261005/run/observed.json
