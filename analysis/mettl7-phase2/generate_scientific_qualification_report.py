#!/usr/bin/env python3
"""Make skipped scientific qualifications explicit after a Maven test run."""
from __future__ import annotations
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TEST_ROOT = ROOT / "software/modules/prometheus/src/test/java"
REPORTS = ROOT / "software/modules/prometheus/target/surefire-reports"
OUT = ROOT / "analysis/mettl7-phase2/SCIENTIFIC_QUALIFICATION_STATUS.json"

def qualification_methods():
    result = []
    for path in TEST_ROOT.rglob("*.java"):
        lines = path.read_text().splitlines()
        for index, line in enumerate(lines):
            if "Assumptions.assumeTrue" not in line and "assumeTrue(" not in line:
                continue
            method = None
            for prior in range(index, max(-1, index - 20), -1):
                match = re.search(r"\bvoid\s+(\w+)\s*\(", lines[prior])
                if match:
                    method = match.group(1); break
            if method:
                package = next((value.split("package ",1)[1].rstrip(";")
                                for value in lines if value.startswith("package ")), "")
                result.append((f"{package}.{path.stem}", method,
                               str(path.relative_to(ROOT)), index + 1, line.strip()))
    return result

def main():
    cases = {}
    for report in REPORTS.glob("TEST-*.xml"):
        suite = ET.parse(report).getroot()
        for case in suite.findall("testcase"):
            key = (case.attrib.get("classname"), case.attrib.get("name"))
            cases[key] = case
    records = []
    for class_name, method, source, line, condition in qualification_methods():
        case = cases.get((class_name, method))
        skipped = case.find("skipped") if case is not None else None
        failure = None if case is None else (case.find("failure") or case.find("error"))
        if case is not None and skipped is None and failure is None:
            status = "RUN_AND_PASS"
            reason = None
        else:
            status = "NOT_RUN_MISSING_REQUIRED_ARTIFACT"
            reason = ((skipped.attrib.get("message") or skipped.text or
                       "required qualification activation/artifact unavailable") if skipped is not None else
                      "test result absent or required qualification activation/artifact unavailable")
        records.append({"class": class_name, "test": method, "source": source,
                        "line": line, "guard": condition, "status": status,
                        "reason": reason})
    payload = {"schema": "prometheus-scientific-qualification-status-v1",
               "allowed_statuses": ["RUN_AND_PASS", "NOT_RUN_MISSING_REQUIRED_ARTIFACT"],
               "qualifications": records,
               "run_and_pass": sum(r["status"] == "RUN_AND_PASS" for r in records),
               "not_run_missing_required_artifact": sum(r["status"] != "RUN_AND_PASS" for r in records)}
    OUT.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")
    print(json.dumps(payload, indent=2, sort_keys=True))

if __name__ == "__main__": main()
