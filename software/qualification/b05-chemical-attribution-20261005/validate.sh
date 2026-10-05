#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?New output directory}
test ! -e "$out"
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
basecp=$(cat software/qualification/b05-mixed-geometry-v3-20261005/run-1/classpath.txt)
cp="$out/classes:$basecp"
"$j/javac" -proc:none --release 21 -cp "$basecp" -d "$out/classes" software/modules/daedalus/src/test/java/totah/lab/daedalus/system/MethylRingGeometryAttributionTest.java
printf '%s' "$cp" > "$out/classpath.txt"
"$j/java" -Xmx384m -jar /Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar execute --class-path "$cp" --select-class totah.lab.daedalus.system.MethylRingGeometryAttributionTest --select-class totah.lab.daedalus.system.ContinuousGeometryV3AcceptanceTest --details summary --disable-ansi-colors --reports-dir "$out/junit" > "$out/tests.log" 2>&1
"$j/java" -cp "$cp" totah.lab.daedalus.system.MethylRingGeometryAttributionTest "$out/one.json"
"$j/java" -cp "$cp" totah.lab.daedalus.system.MethylRingGeometryAttributionTest "$out/two.json"
cmp "$out/one.json" "$out/two.json"
cat "$out/tests.log"
