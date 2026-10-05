#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?New output directory}
test ! -e "$out"
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
basecp=$(cat software/qualification/all-members-nonpolar-20261005/run-1/classpath.txt)
"$j/javac" -proc:none --release 21 -cp "$basecp" -d "$out/classes" software/qualification/p06-centroid-characterization-20261005/CentroidCharacterizationTest.java
"$j/java" -Xmx256m -jar /Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar execute --class-path "$out/classes:$basecp" --select-class totah.lab.daedalus.system.CentroidCharacterizationTest --details summary --disable-ansi-colors > "$out/tests.log" 2>&1
"$j/java" -cp "$out/classes:$basecp" totah.lab.daedalus.system.CentroidCharacterizationTest "$out/observed-v1.json"
cat "$out/tests.log"
