#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?New output directory}
test ! -e "$out"
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
basecp=$(cat software/qualification/direct-assessment-current-20261005/final-run/classpath.txt)
cp="$out/classes:$basecp"
"$j/javac" -proc:none --release 21 -cp "$basecp" -d "$out/classes" software/modules/athena/src/main/java/totah/lab/athena/system/rules/{FunctionalGroupRules,CysteineBackboneAttribution,RuleAnalyzers,RuleRegistry}.java software/modules/daedalus/src/test/java/totah/lab/daedalus/system/CysteineBackboneAcceptanceTest.java
printf '%s' "$cp" > "$out/classpath.txt"
"$j/java" -Xmx384m -jar /Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar execute --class-path "$cp" --select-class totah.lab.daedalus.system.CysteineBackboneAcceptanceTest --details tree --disable-ansi-colors --reports-dir "$out/junit" > "$out/tests.log" 2>&1
cat "$out/tests.log"
"$j/java" -cp "$cp" totah.lab.daedalus.system.CysteineBackboneAcceptanceTest "$out/one.json"
"$j/java" -cp "$cp" totah.lab.daedalus.system.CysteineBackboneAcceptanceTest "$out/two.json"
cmp "$out/one.json" "$out/two.json"
