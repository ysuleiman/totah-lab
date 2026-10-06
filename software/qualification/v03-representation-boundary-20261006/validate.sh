#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?new output directory}
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
cp=$(cat software/qualification/b06-cysteine-backbone-20261006/final-identity/classpath.txt)
"$j/javac" -proc:none --release 21 -cp "$cp" -d "$out/classes" software/modules/athena-openchemlib/src/main/java/totah/lab/athena/design/backend/ocl/*.java software/modules/athena-openchemlib/src/test/java/totah/lab/athena/design/backend/ocl/OclRadicalRepresentationBoundaryTest.java software/modules/athena-openchemlib/src/test/java/totah/lab/athena/design/backend/ocl/OclMappingBoundaryAuditTest.java
"$j/java" -Xmx256m -jar /Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar execute --class-path "$out/classes:$cp" --select-class totah.lab.athena.design.backend.ocl.OclRadicalRepresentationBoundaryTest --select-class totah.lab.athena.design.backend.ocl.OclMappingBoundaryAuditTest --details summary --disable-ansi-colors --reports-dir "$out/junit" > "$out/tests.log" 2>&1
cat "$out/tests.log"
