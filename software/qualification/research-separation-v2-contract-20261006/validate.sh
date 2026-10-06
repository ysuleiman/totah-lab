#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?new output directory}
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
cp=$(cat software/qualification/b06-cysteine-backbone-20261006/final-identity/classpath.txt)
"$j/javac" -proc:none --release 21 -cp "$cp" -d "$out/classes" software/modules/athena/src/main/java/totah/lab/athena/system/rules/research/*.java software/modules/daedalus/src/test/java/totah/lab/athena/system/rules/research/{ResearchGateAcceptanceTest,ResearchSeparationCharacterizationTest}.java
"$j/java" -Xmx256m -jar /Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar execute --class-path "$out/classes:$cp" --select-class totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest --select-class totah.lab.athena.system.rules.research.ResearchSeparationCharacterizationTest --details summary --disable-ansi-colors --reports-dir "$out/junit" > "$out/tests.log" 2>&1
cat "$out/tests.log"
