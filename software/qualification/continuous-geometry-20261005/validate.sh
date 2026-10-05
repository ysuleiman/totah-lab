#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?New output directory required}
test ! -e "$out"
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
basecp=$(cat software/qualification/chemical-role-perception-20261005/qualified-run/classpath.txt)
cp="$out/classes:$basecp"
"$j/javac" -proc:none --release 21 -cp "$basecp" -d "$out/classes" software/modules/gaia/src/main/java/totah/lab/gaia/geometry/Plane3D.java software/modules/athena/src/main/java/totah/lab/athena/system/rules/{ContinuousGeometryRules,RuleRegistry,RuleAnalyzers}.java software/modules/daedalus/src/test/java/totah/lab/daedalus/system/ContinuousGeometryAcceptanceTest.java software/modules/gaia/src/test/java/totah/lab/geometry/{Plane3DTest,DihedralTest}.java
printf '%s' "$cp" > "$out/classpath.txt"
"$j/java" -Xmx384m -jar /Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar execute --class-path "$cp" --select-class totah.lab.daedalus.system.ContinuousGeometryAcceptanceTest --select-class totah.lab.geometry.Plane3DTest --select-class totah.lab.geometry.DihedralTest --select-class totah.lab.daedalus.system.ChemicalRoleAcceptanceTest --select-class totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest --select-class totah.lab.daedalus.system.FoundationVocabularyAcceptanceTest --select-class totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest --select-class totah.lab.daedalus.system.GroupContextAcceptanceTest --select-class totah.lab.athena.design.backend.ocl.OclQuerySemanticsQualificationTest --select-class totah.lab.athena.design.backend.ocl.OclCorrectedOccurrenceTest --select-class totah.lab.daedalus.system.ResearchGatePipelineAcceptanceTest --select-class totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest --details summary --disable-ansi-colors --reports-dir "$out/junit" > "$out/tests.log" 2>&1
mkdir -p "$out/journal-one" "$out/journal-two"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.ContinuousGeometryAcceptanceTest "$out/one.json" "$out/journal-one"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.ContinuousGeometryAcceptanceTest "$out/two.json" "$out/journal-two"
cmp "$out/one.json" "$out/two.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest "$out/scientific-replay"
diff -qr "$out/scientific-replay" software/qualification/ocl-query-b00-corrected-20261005/final-run/scientific-replay-one > "$out/scientific-diff.txt"
cat "$out/tests.log"

diff -qr "$out/journal-one" "$out/journal-two" > "$out/journal-diff.txt"
"$j/javac" -proc:none --release 21 -cp "$basecp" -d "$out/classes" software/qualification/continuous-geometry-20261005/PlaneReplay.java
"$j/java" -cp "$cp" PlaneReplay > "$out/plane-replay.txt"
cmp "$out/plane-replay.txt" software/qualification/continuous-geometry-20261005/legacy-replay/before.txt
"$j/java" -Xmx256m -cp "$cp" totah.lab.athena.design.backend.ocl.OclCorrectedOccurrenceTest "$out/b00-replay.json"
cmp "$out/b00-replay.json" software/qualification/ocl-query-b00-corrected-20261005/final-run/replay-one.json

"$j/javac" -proc:none --release 21 -cp "$cp" -d "$out/classes" software/qualification/continuous-geometry-20261005/ManifestCheck.java
"$j/java" -cp "$cp" ManifestCheck > "$out/manifest-check.txt"
