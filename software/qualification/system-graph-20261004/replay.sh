#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
q=software/qualification/system-graph-20261004
java_bin=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
out=${1:?Provide a new output directory}
mkdir "$out"
classpath=$(cat "$q/runtime-classpath.txt")
"$java_bin/javac" -proc:none --release 21 -cp "$classpath" -d "$q/classes" software/qualification/fragment-real-control-20261004/RealFragmentControl.java "$q/FrozenSystemAcceptance.java"
for iteration in 1 2; do
 "$java_bin/java" -Xmx512m -cp "$classpath" totah.lab.daedalus.system.SystemQualificationAcceptanceTest "$out/synthetic-$iteration"
 "$java_bin/java" -Xmx768m -cp "$classpath" totah.lab.athena.design.backend.ocl.FrozenSystemAcceptance "$out/frozen-$iteration"
done
cmp "$out/synthetic-1/certificate.json" "$out/synthetic-2/certificate.json"
cmp "$out/frozen-1/certificate.json" "$out/frozen-2/certificate.json"
cmp "$out/frozen-1/acceptance.json" "$out/frozen-2/acceptance.json"
for first in "$out/frozen-1/catalog/"*.snapshot.json; do cmp "$first" "$out/frozen-2/catalog/$(basename "$first")"; done
for first in "$out/synthetic-1/"*.snapshot.json; do cmp "$first" "$out/synthetic-2/$(basename "$first")"; done
printf '%s\n' 'PASS: independent JVM synthetic/frozen certificates and complete catalogs match exactly; frozen engineering reports match exactly.'
