#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
q=software/qualification/system-graph-20261004
java_bin=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
junit=/Users/yazan/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
classpath="$q/classes:software/modules/mnemosyne/src/test/resources:$(cat software/qualification/fragment-region-fit-20261004/runtime-classpath.txt)"
mkdir -p "$q/classes"
printf '%s\n' "$classpath" > "$q/runtime-classpath.txt"
{
 cat software/qualification/fragment-region-fit-20261004/sources.txt
 rg --files software/modules/mnemosyne/src/main/java software/modules/mnemosyne/src/test/java software/modules/athena/src/main/java/totah/lab/athena/system software/modules/daedalus/src/main/java/totah/lab/daedalus/system software/modules/daedalus/src/test/java/totah/lab/daedalus/system -g '*.java'
} | sort -u > "$q/sources.txt"
"$java_bin/javac" -proc:none --release 21 -cp "$classpath" -d "$q/classes" @"$q/sources.txt"
"$java_bin/java" -Dbasedir=software/modules/mnemosyne -Xmx512m -jar "$junit" execute --class-path "$classpath" --select-package totah.lab.mnemosyne --exclude-classname .*FoundationArchitectureTest --select-package totah.lab.daedalus.system --select-class totah.lab.athena.design.backend.ocl.FragmentRegionFitAcceptanceTest --select-class totah.lab.athena.design.backend.ocl.PocketFragmentReplacementAcceptanceTest --select-class totah.lab.athena.design.backend.ocl.HypothesisDirectedReplacementAcceptanceTest --select-class totah.lab.athena.design.backend.GraphEditTransactionEngineTest --select-class totah.lab.athena.design.backend.TopologyEditTransactionEngineTest --select-class totah.lab.athena.design.generation.DesignProvenanceAcceptanceTest --select-class totah.lab.athena.clash.StericClashAnalysisTest --select-class totah.lab.gaia.graph.ResidueGraphTest --select-class totah.lab.athena.interaction.InteractionProfilerTest --select-class totah.lab.athena.pocket.architecture.EscapeRouteAnalyzerTest --select-class totah.lab.athena.pocket.architecture.LigandSpaceComparatorTest --reports-dir "$q/junit" --details summary --disable-ansi-colors

# Foundation isolation is tested with only its own classes and cached JSON dependencies.
foundation="$q/foundation-classes"
mkdir -p "$foundation"
jsoncp="$junit"
IFS=: read -r -a entries <<< "$classpath"
for entry in "${entries[@]}"; do case "$entry" in *jackson*.jar) jsoncp="$jsoncp:$entry" ;; esac; done
rg --files software/modules/mnemosyne/src/main/java -g '*.java' > "$q/foundation-sources.txt"
printf '%s\n' software/modules/mnemosyne/src/test/java/totah/lab/mnemosyne/FoundationArchitectureTest.java >> "$q/foundation-sources.txt"
"$java_bin/javac" -proc:none --release 21 -cp "$jsoncp" -d "$foundation" @"$q/foundation-sources.txt"
"$java_bin/java" -Dbasedir=software/modules/mnemosyne -jar "$junit" execute --class-path "$foundation:$jsoncp" --select-class totah.lab.mnemosyne.FoundationArchitectureTest --reports-dir "$q/junit-foundation" --details summary --disable-ansi-colors
