#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?New output directory}
test ! -e "$out"
mkdir -p "$out/classes" "$out/catalog"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
basecp=$(cat software/qualification/b06-source-connectivity-20261005/final-run/classpath.txt)
cp="$out/classes:$basecp"
"$j/javac" -proc:none --release 21 -cp "$basecp" -d "$out/classes" software/qualification/direct-assessment-execution-contract-20261005/DirectAssessmentExecutionCharacterizationTest.java
"$j/java" -Xmx256m -jar /Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar execute --class-path "$cp" --select-class totah.lab.daedalus.system.DirectAssessmentExecutionCharacterizationTest --details summary --disable-ansi-colors > "$out/tests.log" 2>&1
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.DirectAssessmentExecutionCharacterizationTest "$out/observed.json" "$out/catalog"
cat "$out/tests.log"
