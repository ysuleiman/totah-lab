#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
"$j/java" -Xmx256m -jar /Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar execute --class-path "$(cat software/qualification/disconnected-validation-repair-20261005/qualified-run/classpath.txt)" --select-method totah.lab.daedalus.system.RuleRegistryAcceptanceTest#identityVersionDigestConflictNeverMutatesRegistry --select-method totah.lab.daedalus.system.RuleRegistryAcceptanceTest#versionChangeRetirementAndReplayPreserveOriginalMeasurements --select-method totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest#overlappingIdentitiesAndSymmetryRolesSurvive --select-method totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest#overlapAndContradictionCoexistInExistingJournal --details summary --disable-ansi-colors
