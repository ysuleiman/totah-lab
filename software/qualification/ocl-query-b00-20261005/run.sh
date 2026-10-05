#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
q=software/qualification/ocl-query-b00-20261005
junit=/Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
cp="software/qualification/athena-scientific-rules-20261004/classes:$(cat software/qualification/fragment-region-fit-20261004/runtime-classpath.txt)"
mkdir -p "$q/classes"
sources=(
software/modules/athena/src/main/java/totah/lab/athena/design/backend/SubstructureMatcher.java
software/modules/athena/src/main/java/totah/lab/athena/design/backend/MolecularGraph.java
software/modules/athena/src/main/java/totah/lab/athena/design/backend/BackendEvidence.java
software/modules/athena/src/main/java/totah/lab/athena/design/backend/MolecularBackendException.java
software/modules/athena-openchemlib/src/main/java/totah/lab/athena/design/backend/ocl/OclGraphMapper.java
software/modules/athena-openchemlib/src/main/java/totah/lab/athena/design/backend/ocl/OclMolecularBackend.java
)
javac -proc:none --release 21 -cp "$cp:$junit" -d "$q/classes" "${sources[@]}" software/modules/athena-openchemlib/src/test/java/totah/lab/athena/design/backend/ocl/OclQuerySemanticsQualificationTest.java software/modules/athena-openchemlib/src/test/java/totah/lab/athena/design/backend/ocl/OclMolecularBackendAcceptanceTest.java
cp="$q/classes:software/modules/athena-openchemlib/src/test/resources:$cp"
java -Xmx256m -jar "$junit" execute --class-path "$cp" --select-class totah.lab.athena.design.backend.ocl.OclQuerySemanticsQualificationTest --select-method totah.lab.athena.design.backend.ocl.OclMolecularBackendAcceptanceTest#smartsReturnsStableProjectAtomIds --select-method totah.lab.athena.design.backend.ocl.OclMolecularBackendAcceptanceTest#positiveHydrogenCountsMustMatchInferredHydrogensAndArePreserved --select-method totah.lab.athena.design.backend.ocl.OclMolecularBackendAcceptanceTest#unsupportedStereoDescriptorFailsExplicitly --reports-dir "$q/junit" --details summary --disable-ansi-colors
java -Xmx256m -cp "$cp" totah.lab.athena.design.backend.ocl.OclQuerySemanticsQualificationTest "$q/replay-one.json"
java -Xmx256m -cp "$cp" totah.lab.athena.design.backend.ocl.OclQuerySemanticsQualificationTest "$q/replay-two.json"
cmp "$q/replay-one.json" "$q/replay-two.json"
