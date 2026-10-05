#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
q=software/qualification/ocl-query-b00-20261005
junit=/Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
cp="$q/classes:software/modules/athena-openchemlib/src/test/resources:software/qualification/athena-scientific-rules-20261004/classes:$(cat software/qualification/fragment-region-fit-20261004/runtime-classpath.txt)"
javac -proc:none --release 21 -cp "$cp:$junit" -d "$q/classes" "$q/RequiredContractProbe.java"
set +e
java -Xmx256m -jar "$junit" execute --class-path "$cp" --select-class totah.lab.athena.design.backend.ocl.RequiredContractProbe --reports-dir "$q/junit-required-contract" --details summary --disable-ansi-colors > "$q/required-contract.log" 2>&1
b00_contract_exit=$?
printf '%s\n' "$b00_contract_exit" > "$q/required-contract-exit-code.txt"
exit "$b00_contract_exit"
