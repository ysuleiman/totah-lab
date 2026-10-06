#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
q=software/qualification/rule-registry-20261004
java_bin=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
classpath="$(cat "$q/runtime-classpath.txt")"
output="$q/qualified-replay"
mkdir -p "$output"
"$java_bin/javac" -proc:none --release 21 -cp "$classpath" -d "$q/classes" software/qualification/fragment-real-control-20261004/RealFragmentControl.java "$q/FrozenRuleAcceptance.java"
"$java_bin/java" -Xmx512m -cp "$classpath:$q/legacy-classes" totah.lab.athena.interaction.LegacyProfiles "$output/current-detector-output.json"
cmp "$q/legacy-detector-output.json" "$output/current-detector-output.json"
for run in 1 2; do
 "$java_bin/java" -Xmx512m -cp "$classpath" totah.lab.daedalus.system.RuleRegistryAcceptanceTest "$output/synthetic-replay-$run"
 "$java_bin/java" -Xmx512m -cp "$classpath" totah.lab.athena.design.backend.ocl.FrozenRuleAcceptance "$output/frozen-replay-$run"
done
diff -rq "$output/synthetic-replay-1" "$output/synthetic-replay-2"
diff -rq "$output/frozen-replay-1" "$output/frozen-replay-2"
printf '%s\n' 'PASS: 70 legacy detector/profile/boundary outputs byte-identical; independent JVM synthetic and frozen catalogs/certificates byte-identical.'
