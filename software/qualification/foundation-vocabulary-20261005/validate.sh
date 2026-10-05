#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?New output directory required}
test ! -e "$out"
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
basecp=$(cat software/qualification/group-context-v2-20261005/qualified-run/classpath.txt)
cp="$out/classes:$basecp"
"$j/javac" -proc:none --release 21 -cp "$basecp" -d "$out/classes" software/modules/daedalus/src/test/java/totah/lab/daedalus/system/FoundationVocabularyAcceptanceTest.java
printf '%s' "$cp" > "$out/classpath.txt"
"$j/java" -Xmx384m -jar /Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar execute --class-path "$cp" --select-class totah.lab.daedalus.system.FoundationVocabularyAcceptanceTest --select-class totah.lab.daedalus.system.GroupContextAcceptanceTest --select-class totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest --select-class totah.lab.daedalus.system.FoundationGroupAcceptanceTest --details summary --disable-ansi-colors --reports-dir "$out/junit" > "$out/tests.log" 2>&1
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.FoundationVocabularyAcceptanceTest "$out/one.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.FoundationVocabularyAcceptanceTest "$out/two.json"
cmp "$out/one.json" "$out/two.json"
"$j/java" -Xmx256m -cp "$cp" totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest "$out/scientific-replay"
diff -qr "$out/scientific-replay" software/qualification/ocl-query-b00-corrected-20261005/final-run/scientific-replay-one > "$out/scientific-diff.txt"
cat "$out/tests.log"
