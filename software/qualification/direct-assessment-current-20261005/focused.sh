#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?New output directory}
test ! -e "$out"
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
basecp=$(cat software/qualification/b06-source-connectivity-20261005/final-run/classpath.txt)
cp="$out/classes:$basecp"
"$j/javac" -proc:none --release 21 -cp "$basecp" -d "$out/classes" \
software/modules/athena/src/main/java/totah/lab/athena/system/rules/{SourceSulfurConnectivityRules,RuleRegistry,RuleAnalyzers}.java \
software/modules/daedalus/src/test/java/totah/lab/daedalus/system/SourceSulfurConnectivityAcceptanceTest.java \
software/qualification/b06-connectivity-coverage-characterization-20261005/ConnectivityCoverageCharacterizationTest.java \
software/modules/daedalus/src/main/java/totah/lab/daedalus/system/{CurrentRuleExecution,RuleExecutionPipeline,DirectAssessmentInputs}.java \
software/modules/daedalus/src/test/java/totah/lab/athena/system/rules/research/DirectAssessmentResearchFixture.java \
software/modules/daedalus/src/test/java/totah/lab/daedalus/system/DirectAssessmentExecutionAcceptanceTest.java
printf '%s' "$cp" > "$out/classpath.txt"
"$j/java" -Xmx384m -jar /Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar execute --class-path "$cp" --select-class totah.lab.daedalus.system.DirectAssessmentExecutionAcceptanceTest --details tree --disable-ansi-colors --reports-dir "$out/junit" > "$out/tests.log" 2>&1
cat "$out/tests.log"
