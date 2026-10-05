#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
q=software/qualification/athena-scientific-rules-20261004
java_bin=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
classpath="$(cat "$q/runtime-classpath.txt")"
output="${1:-$q/qualified-checkpoint}"
mkdir -p "$output"
for run in 1 2; do
 "$java_bin/java" -Xmx512m -cp "$classpath" totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest "$output/replay-$run"
done
diff -rq "$output/replay-1" "$output/replay-2"
"$java_bin/java" -Xmx512m -cp "$classpath:software/qualification/rule-registry-20261004/legacy-classes" totah.lab.athena.interaction.LegacyProfiles "$output/legacy-detector-output.json"
cmp software/qualification/rule-registry-20261004/legacy-detector-output.json "$output/legacy-detector-output.json"
printf '%s\n' 'PASS: five-family independent-JVM catalogs and certificates byte-identical; 70 legacy outputs byte-identical.'

"$java_bin/javac" --release 21 -proc:none -cp "$classpath" -d "$q/classes" "$q/LegacyManifestDigests.java"
"$java_bin/java" -Xmx256m -cp "$(cat software/qualification/rule-registry-20261004/runtime-classpath.txt):$q/classes" LegacyManifestDigests "$output/old-manifest-digests.json"
"$java_bin/java" -Xmx256m -cp "$classpath" LegacyManifestDigests "$output/new-manifest-digests.json"
cmp "$output/old-manifest-digests.json" "$output/new-manifest-digests.json"
"$java_bin/java" -Xmx256m -cp "$classpath" totah.lab.daedalus.system.RuleRegistryAcceptanceTest "$output/legacy-pipeline"
diff -rq software/qualification/rule-registry-20261004/qualified-replay/synthetic-replay-1 "$output/legacy-pipeline"
printf '%s\n' 'PASS: all 26 historical manifest digests and the complete historical /1 pipeline catalog/certificate unchanged.'
