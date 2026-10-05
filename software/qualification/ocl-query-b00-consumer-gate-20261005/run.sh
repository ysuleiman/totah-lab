#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
q=software/qualification/ocl-query-b00-consumer-gate-20261005
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
cp="software/qualification/ocl-query-b00-20261005/classes:software/qualification/athena-scientific-rules-20261004/classes:software/modules/athena/src/main/resources:$(cat software/qualification/fragment-region-fit-20261004/runtime-classpath.txt)"
mkdir -p "$q/probe-classes" "$q/candidate-classes"
"$j/javac" -proc:none --release 21 -cp "$cp" -d "$q/probe-classes" "$q/ConsumerImpactProbe.java"
"$j/javac" -proc:none --release 21 -cp "$cp" -d "$q/candidate-classes" "$q/candidate-src/OclMolecularBackend.java"
"$j/java" -Xmx256m -cp "$q/probe-classes:$cp" totah.lab.daedalus.system.ConsumerImpactProbe "$q/old-journal" > "$q/old.txt"
"$j/java" -Xmx256m -cp "$q/candidate-classes:$q/probe-classes:$cp" totah.lab.daedalus.system.ConsumerImpactProbe "$q/candidate-journal" > "$q/candidate.txt"
cat "$q/old.txt" "$q/candidate.txt"
