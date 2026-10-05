#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?Output directory}
test ! -e "$out"
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
cp="$out/classes:$(cat software/qualification/b06-cysteine-backbone-20261006/final-run/classpath.txt)"
"$j/javac" -proc:none --release 21 -cp "$cp" -d "$out/classes" software/qualification/b06-cysteine-backbone-20261006/DirectAssessmentDiskRetryTest.java
"$j/java" -Xmx256m -jar /Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar execute --class-path "$cp" --select-class totah.lab.daedalus.system.DirectAssessmentDiskRetryTest --details summary --disable-ansi-colors --reports-dir "$out/junit" > "$out/retry.log" 2>&1
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.CysteineBackboneAcceptanceTest "$out/backbone-one.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.CysteineBackboneAcceptanceTest "$out/backbone-two.json"
cmp "$out/backbone-one.json" "$out/backbone-two.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest "$out/scientific-replay"
diff -qr "$out/scientific-replay" software/qualification/ocl-query-b00-corrected-20261005/final-run/scientific-replay-one > "$out/scientific-diff.txt"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.ConnectivityCoverageCharacterizationTest "$out/legacy-sulfur.json"
cmp "$out/legacy-sulfur.json" software/qualification/b06-connectivity-coverage-characterization-20261005/run/observed.json
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.DirectAssessmentExecutionAcceptanceTest "$out/direct" "$out/direct.json"
cmp "$out/direct.json" software/qualification/direct-assessment-current-20261005/final-run/direct-one.json
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.ResearchGatePipelineAcceptanceTest "$out/current" "$out/current.json"
cmp "$out/current.json" software/qualification/research-gate-20261005/final-run/gate-one.json
cat "$out/retry.log"
