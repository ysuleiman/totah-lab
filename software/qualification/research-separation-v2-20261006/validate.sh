#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?new output directory}
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
cp=$(cat software/qualification/v03-explicit-radical-20261006/regression/classpath.txt)
"$j/javac" -proc:none --release 21 -cp "$cp" -d "$out/classes" software/modules/athena/src/main/java/totah/lab/athena/system/rules/research/*.java software/modules/daedalus/src/main/java/totah/lab/daedalus/system/CurrentRuleExecution.java software/modules/daedalus/src/test/java/totah/lab/athena/system/rules/research/{ResearchGateAcceptanceTest,ResearchSeparationCharacterizationTest,ResearchV2Fixtures,ResearchV2AcceptanceTest,DirectAssessmentResearchFixture}.java software/modules/daedalus/src/test/java/totah/lab/daedalus/system/{ResearchV2PipelineAcceptanceTest,ResearchGatePipelineAcceptanceTest,DirectAssessmentExecutionAcceptanceTest}.java
printf '%s' "$out/classes:$cp" > "$out/classpath.txt"
"$j/java" -Xmx384m -jar /Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar execute --class-path "$out/classes:$cp" --select-class totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest --select-class totah.lab.athena.system.rules.research.ResearchSeparationCharacterizationTest --select-class totah.lab.athena.system.rules.research.ResearchV2AcceptanceTest --select-class totah.lab.daedalus.system.ResearchV2PipelineAcceptanceTest --select-class totah.lab.daedalus.system.ResearchGatePipelineAcceptanceTest --select-class totah.lab.daedalus.system.DirectAssessmentExecutionAcceptanceTest --details summary --disable-ansi-colors --reports-dir "$out/junit" > "$out/tests.log" 2>&1
cat "$out/tests.log"
"$j/java" -cp "$out/classes:$cp" totah.lab.daedalus.system.ResearchV2PipelineAcceptanceTest "$out/one" "$out/one.json"
"$j/java" -cp "$out/classes:$cp" totah.lab.daedalus.system.ResearchV2PipelineAcceptanceTest "$out/two" "$out/two.json"
cmp "$out/one.json" "$out/two.json"
diff -qr "$out/one" "$out/two" > "$out/replay.diff"
"$j/java" -cp "$out/classes:$cp" totah.lab.daedalus.system.ResearchGatePipelineAcceptanceTest "$out/legacy-current" "$out/legacy-current.json"
cmp "$out/legacy-current.json" software/qualification/research-gate-20261005/final-run/gate-one.json
"$j/java" -cp "$out/classes:$cp" totah.lab.daedalus.system.DirectAssessmentExecutionAcceptanceTest "$out/legacy-direct" "$out/legacy-direct.json"
cmp "$out/legacy-direct.json" software/qualification/direct-assessment-current-20261005/final-run/direct-one.json
