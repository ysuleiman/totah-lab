#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
out=${1:?new output directory}
test ! -e "$out"
mkdir -p "$out/classes"
j=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin
cp=$(cat software/qualification/p03-role-closure-20261005/run-1/classpath.txt)
"$j/javac" -proc:none --release 21 -cp "$cp" -d "$out/classes" software/qualification/v01-validation-characterization-20261005/ValidationProbe.java
"$j/java" -Xmx256m -cp "$out/classes:$cp" totah.lab.daedalus.system.ValidationProbe "$out/results.json"
cmp "$out/results.json" software/qualification/v01-validation-characterization-20261005/results-with-causes.json
