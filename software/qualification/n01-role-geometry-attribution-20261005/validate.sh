#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?New output directory}
test ! -e "$out"
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
basecp=$(cat software/qualification/p03-role-closure-20261005/run-1/classpath.txt)
cp="$out/classes:$basecp"
"$j/javac" -proc:none --release 21 -cp "$basecp" -d "$out/classes" software/modules/daedalus/src/test/java/totah/lab/daedalus/system/RoleGeometryAttributionTest.java
printf '%s' "$cp" > "$out/classpath.txt"
"$j/java" -Xmx256m -jar /Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar execute --class-path "$cp" --select-class totah.lab.daedalus.system.RoleGeometryAttributionTest --select-class totah.lab.daedalus.system.ContinuousGeometryAcceptanceTest --select-class totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest --details summary --disable-ansi-colors --reports-dir "$out/junit" > "$out/tests.log" 2>&1
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.RoleGeometryAttributionTest "$out/one.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.RoleGeometryAttributionTest "$out/two.json"
cmp "$out/one.json" "$out/two.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest "$out/scientific-replay"
diff -qr "$out/scientific-replay" software/qualification/ocl-query-b00-corrected-20261005/final-run/scientific-replay-one > "$out/scientific-diff.txt"
cat "$out/tests.log"
