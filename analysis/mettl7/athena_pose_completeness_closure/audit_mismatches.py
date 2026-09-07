#!/usr/bin/env python3
"""Inventory receipt/PDBQT/postprocessed-count mismatches without altering history."""
import csv, json, re, hashlib
from collections import defaultdict
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]; OUT=Path(__file__).resolve().parent
tables=[]
for p in (ROOT/"analysis").rglob("*.csv"):
 try:
  with p.open(errors="replace") as f:
   rd=csv.DictReader(f)
   if rd.fieldnames and {"run_id","pose_model","receipt_path"}.issubset(rd.fieldnames): tables.append(p)
 except: pass
rows=[]
for table in sorted(tables):
 by=defaultdict(list)
 try:
  for r in csv.DictReader(table.open(errors="replace")):
   if r.get("receipt_path"): by[(r["run_id"],r["receipt_path"])].append(r)
 except: continue
 for (runid,rpath),rr in by.items():
  receipt=Path(rpath)
  if not receipt.is_absolute(): receipt=ROOT/receipt
  if not receipt.exists():
   rows.append({"table":str(table.relative_to(ROOT)),"run_id":runid,"receipt_expected":"MISSING","pdbqt_models":"","emitted_rows":len(rr),"status":"RECEIPT_MISSING","affected_artifact":str(table.relative_to(ROOT)),"retrospective_impact":"REQUIRES_RECOMPUTATION","impact_reason":"receipt unavailable; completeness cannot be established"});continue
  data=json.loads(receipt.read_text()); pose=receipt.parent/"poses.pdbqt"
  text=pose.read_text(errors="replace") if pose.exists() else ""
  models=len(re.findall(r"(?m)^MODEL\s+",text)); expected=data.get("parsedPoseCount",data.get("parsed_pose_count","MISSING")); emitted=len(rr)
  status="MATCH" if expected==models==emitted else "MISMATCH"
  if status=="MISMATCH":
   pose_rows_complete = models == emitted
   rows.append({"table":str(table.relative_to(ROOT)),"run_id":runid,"receipt_expected":expected,"pdbqt_models":models,"emitted_rows":emitted,"status":status,"affected_artifact":str(table.relative_to(ROOT)),"retrospective_impact":"REQUIRES_RECOMPUTATION","impact_reason":"pose evidence rows UNAFFECTED; receipt/completeness assertion defective" if pose_rows_complete else "pose evidence and downstream summaries require recomputation"})
fields=["table","run_id","receipt_expected","pdbqt_models","emitted_rows","status","affected_artifact","retrospective_impact","impact_reason"]
with (OUT/"ATHENA_POSE_COMPLETENESS_MISMATCH_INVENTORY.csv").open("w",newline="") as f:w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(rows)
print(json.dumps({"tables_scanned":len(tables),"mismatches":len(rows),"unique_runs":len({r['run_id'] for r in rows})},indent=2))
