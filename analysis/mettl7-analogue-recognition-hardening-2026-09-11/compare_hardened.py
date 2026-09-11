#!/usr/bin/env python3
"""Compare only the explicitly named, blinded recognition execution artifacts."""
import csv
import hashlib
import json
from decimal import Decimal
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OLD = ROOT / 'analysis/mettl7-analogue-recognition-v1'
NEW = ROOT / 'analysis/mettl7-analogue-recognition-v1-hardened'
REPORT = Path(__file__).resolve().parent
MANIFEST_HASH = 'b9639b6a13a0ec12c127a7e74bda4284202184bebc963e9181babcad6366ab1a'
PREDICTIONS = [
    ('ar_13503_deesterified', 'AR-13503', 'A_INHIBITION_LIKELY_INCREASED', 'MEDIUM'),
    ('des_ortho_methyl', 'Des-ortho-methyl', 'B_SELECTIVITY_WEAKENED', 'MEDIUM'),
    ('des_para_methyl', 'Des-para-methyl', 'B_SELECTIVITY_RETAINED', 'MEDIUM'),
    ('n_acetyl_amine', 'N-acetyl-aminomethyl', 'B_SELECTIVITY_WEAKENED', 'LOW'),
    ('branch_point_enantiomer', 'Branch-point enantiomer', 'B_SELECTIVITY_RETAINED', 'MEDIUM'),
    ('quinoline_regioisomer', 'Quinoline N-position', 'B_SELECTIVITY_WEAKENED', 'MEDIUM'),
]
ARTIFACTS = [
    'ANALOGUE_RECOGNITION_MANIFEST.csv',
    'materialization/MATERIALIZATION_OUTCOMES.csv',
    'materialization/BATCH_RECEIPT.txt',
    'materialization/DCMB_HALOGEN_ASSIGNMENT_AUDIT.csv',
    'materialization/PAIRWISE_DIAGNOSTICS.csv',
    'materialization/DIAGNOSTIC_SUMMARY.txt',
    'basins/BASINS.csv', 'basins/PAIR_ADMISSIONS.csv',
    'basins/RECURRENT_EDGE_EVIDENCE.csv', 'basins/BASIN_EXECUTION_RECEIPT.txt',
]
SCIENTIFIC = set(ARTIFACTS) - {'materialization/BATCH_RECEIPT.txt',
    'materialization/DIAGNOSTIC_SUMMARY.txt', 'basins/BASIN_EXECUTION_RECEIPT.txt',
    'materialization/PAIRWISE_DIAGNOSTICS.csv'}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rows(path):
    with path.open() as stream:
        return list(csv.DictReader(stream))


def write_csv(path, fields, values):
    with path.open('w', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, lineterminator='\n')
        writer.writeheader()
        writer.writerows(values)


def receipts_valid(directory):
    references = {
        'materialization/BATCH_RECEIPT.txt': {
            'accounting_sha256': 'materialization/MATERIALIZATION_OUTCOMES.csv',
            'halogen_assignment_audit_sha256': 'materialization/DCMB_HALOGEN_ASSIGNMENT_AUDIT.csv',
            'pairwise_diagnostics_sha256': 'materialization/PAIRWISE_DIAGNOSTICS.csv',
            'diagnostic_summary_sha256': 'materialization/DIAGNOSTIC_SUMMARY.txt',
        },
        'basins/BASIN_EXECUTION_RECEIPT.txt': {
            'basins_sha256': 'basins/BASINS.csv',
            'pair_admissions_sha256': 'basins/PAIR_ADMISSIONS.csv',
            'recurrent_edge_evidence_sha256': 'basins/RECURRENT_EDGE_EVIDENCE.csv',
        },
    }
    for receipt, refs in references.items():
        values = dict(line.split('=', 1) for line in (directory / receipt).read_text().splitlines() if '=' in line)
        for key, path in refs.items():
            assert values[key] == sha(directory / path), (directory, receipt, key)


def primary(directory):
    result = defaultdict(lambda: [0, 0])
    for row in rows(directory / 'basins/BASINS.csv'):
        if float(row['geometry_threshold_A']) == 2 and float(row['topology_threshold']) == .4:
            result[row['arm']][0] += 1
            result[row['arm']][1] += row['recurrent'] == 'true'
    return dict(result)


def main():
    REPORT.mkdir(parents=True, exist_ok=True)
    for directory in (OLD, NEW):
        assert sha(directory / ARTIFACTS[0]) == MANIFEST_HASH
        manifest = rows(directory / ARTIFACTS[0])
        assert len(manifest) == 36
        assert sum(int(r['pose_count']) for r in manifest) == 714
        for row in manifest:
            for field, hash_field in [('sdf', 'sdf_sha256'), ('prepared_pdbqt', 'prepared_sha256'),
                                      ('receptor', 'receptor_sha256'), ('raw_pose', 'raw_sha256')]:
                assert sha(Path(row[field])) == row[hash_field], row[field]
        receipts_valid(directory)
    hash_rows = [dict(artifact=name, old_sha256=sha(OLD / name), hardened_sha256=sha(NEW / name),
                      byte_identical=sha(OLD / name) == sha(NEW / name), scientific_payload=name in SCIENTIFIC)
                 for name in ARTIFACTS]
    write_csv(REPORT / 'ARTIFACT_HASH_COMPARISON.csv', list(hash_rows[0]), hash_rows)

    counts = []
    for state, directory in [('OLD', OLD), ('HARDENED', NEW)]:
        outcomes = rows(directory / 'materialization/MATERIALIZATION_OUTCOMES.csv')
        assert len(outcomes) == len({r['pose_id'] for r in outcomes}) == 714
        assert Counter(r['outcome'] for r in outcomes) == {'ADMITTED_ADEQUATE': 714}
        count = Counter((r['arm'], r['outcome']) for r in outcomes)
        counts.append(count)
    arms = sorted({key[0] for count in counts for key in count})
    admissions = [dict(arm=arm, old_adequate=counts[0][arm, 'ADMITTED_ADEQUATE'],
                       hardened_adequate=counts[1][arm, 'ADMITTED_ADEQUATE']) for arm in arms]
    write_csv(REPORT / 'ADMISSION_COMPARISON.csv', list(admissions[0]), admissions)
    old_basins, new_basins = primary(OLD), primary(NEW)
    basins = [dict(arm=arm, old_basins=old_basins[arm][0], old_recurrent=old_basins[arm][1],
                   hardened_basins=new_basins[arm][0], hardened_recurrent=new_basins[arm][1],
                   changed=old_basins[arm] != new_basins[arm]) for arm in arms]
    write_csv(REPORT / 'BASIN_COMPARISON.csv', list(basins[0]), basins)

    def keyed_edges(directory):
        data = rows(directory / 'basins/RECURRENT_EDGE_EVIDENCE.csv')
        result = {(r['arm'], r['residue'], r['interaction_type']): r for r in data}
        assert len(result) == len(data)
        return result
    old_edges, new_edges = keyed_edges(OLD), keyed_edges(NEW)
    contact_rows = []
    for key in sorted(old_edges.keys() | new_edges.keys()):
        old, new = old_edges.get(key), new_edges.get(key)
        row = dict(arm=key[0], residue=key[1], interaction_type=key[2])
        for label, data in [('old', old), ('hardened', new)]:
            row[label + '_RECURRENT_BASIN_PRESENCE'] = data['recurrent_basins_with_edge'] if data else 'NOT_REPORTED'
            row[label + '_PERSISTENT_WITHIN_BASIN'] = data['persistent_recurrent_basins'] if data else 'NOT_REPORTED'
            row[label + '_total_recurrent_basins'] = data['total_recurrent_basins'] if data else 'NOT_REPORTED'
        row['changed'] = old != new
        contact_rows.append(row)
    write_csv(REPORT / 'CONTACT_COMPARISON.csv', list(contact_rows[0]), contact_rows)
    write_csv(NEW / 'CONTACT_EVIDENCE.csv',
              ['arm', 'residue', 'interaction_type', 'RECURRENT_BASIN_PRESENCE', 'PERSISTENT_WITHIN_BASIN', 'total_recurrent_basins'],
              [dict(arm=r['arm'], residue=r['residue'], interaction_type=r['interaction_type'],
                    RECURRENT_BASIN_PRESENCE=r['recurrent_basins_with_edge'],
                    PERSISTENT_WITHIN_BASIN=r['persistent_recurrent_basins'], total_recurrent_basins=r['total_recurrent_basins'])
               for r in new_edges.values()])
    # Retain every exact diagnostic difference. Do not change model thresholds or round source files.
    old_diagnostics = rows(OLD / 'materialization/PAIRWISE_DIAGNOSTICS.csv')
    new_diagnostics = rows(NEW / 'materialization/PAIRWISE_DIAGNOSTICS.csv')
    diagnostic_changes = []
    assert len(old_diagnostics) == len(new_diagnostics)
    diagnostic_semantics_changed = False
    for old, new in zip(old_diagnostics, new_diagnostics):
        for field in old:
            if old[field] == new[field]:
                continue
            final_decimal_only = (field == 'fixed_frame_heavy_rmsd_A'
                                  and abs(Decimal(old[field]) - Decimal(new[field])) == Decimal('0.000000000001'))
            diagnostic_changes.append(dict(arm=old['arm'], pose_1=old['pose_1'], pose_2=old['pose_2'],
                                           field=field, old=old[field], hardened=new[field],
                                           classification='LAST_PRINTED_DECIMAL_ONLY' if final_decimal_only else 'REVIEW_REQUIRED'))
            diagnostic_semantics_changed |= not final_decimal_only
    write_csv(REPORT / 'EXACT_DIAGNOSTIC_DIFFERENCES.csv',
              ['arm','pose_1','pose_2','field','old','hardened','classification'], diagnostic_changes)
    changed = any(not r['byte_identical'] for r in hash_rows if r['scientific_payload'])
    changed |= diagnostic_semantics_changed
    changed |= old_basins != new_basins or old_edges != new_edges or counts[0] != counts[1]
    predictions = [dict(compound=id, name=name, old_prediction=prediction,
                        hardened_prediction=prediction if not changed else 'REVIEW_REQUIRED',
                        old_confidence=confidence, hardened_confidence=confidence if not changed else 'REVIEW_REQUIRED',
                        interpretation='HYPOTHESIS_NOT_ASSAY_VALIDATED', changed=changed)
                   for id, name, prediction, confidence in PREDICTIONS]
    write_csv(REPORT / 'PREDICTION_COMPARISON.csv', list(predictions[0]), predictions)
    evidence = []
    for id, name, prediction, confidence in PREDICTIONS:
        row = dict(compound=id, prediction=prediction if not changed else 'REVIEW_REQUIRED', confidence=confidence,
                   prediction_state='HARDENED_EXECUTION_VERIFIED_INTERPRETIVE_HYPOTHESIS' if not changed else 'PROVISIONAL_PENDING_EXECUTION_HARDENING',
                   evidence_file='CONTACT_EVIDENCE.csv')
        for paralog, residue, type in [('A','151','HYDROPHOBIC_CONTACT'), ('A','151','PI_CATION'),
                                       ('B','206','HYDROPHOBIC_CONTACT'), ('B','206','PI_CATION'),
                                       ('B','196','HYDROPHOBIC_CONTACT'), ('B','196','PI_CATION')]:
            arm = id.upper() + '_' + paralog
            edge = new_edges.get((arm, residue, type))
            denominator = new_basins[arm][1]
            for metric, key in [('RECURRENT_BASIN_PRESENCE','recurrent_basins_with_edge'),
                                ('PERSISTENT_WITHIN_BASIN','persistent_recurrent_basins')]:
                row[f'{paralog}_{residue}_{type}_{metric}'] = f"{edge[key] if edge else 0}/{denominator}"
        evidence.append(row)
    write_csv(NEW / 'PREDICTION_EVIDENCE.csv', list(evidence[0]), evidence)
    verdicts = [
        dict(item='DOES_MOVING_RING_N_DISRUPT_PARENT_B_RECOGNITION', old='YES_PARTIAL', hardened='YES_PARTIAL' if not changed else 'REVIEW_REQUIRED'),
        dict(item='CAN_THE_FROZEN_RECOGNITION_MODEL_DISCRIMINATE_THESE_ANALOGS', old='YES', hardened='YES' if not changed else 'REVIEW_REQUIRED'),
    ]
    write_csv(REPORT / 'INTERPRETATION_COMPARISON.csv', list(verdicts[0]), verdicts)
    result = dict(SCIENTIFIC_RESULT_CHANGED='YES' if changed else 'NO',
                  diagnostic_last_decimal_changes=len(diagnostic_changes),
                  all_diagnostic_policy_decisions_identical=not diagnostic_semantics_changed,
                  old_adequate=714, hardened_adequate=714, arms=len(arms), contact_rows_compared=len(contact_rows),
                  prospective_labels_changed=changed, assay_files_opened=False, manifest_sha256=MANIFEST_HASH,
                  prior_prediction_state='PROVISIONAL_PENDING_EXECUTION_HARDENING',
                  interpretation_limit='Discrimination means descriptive structural differentiation; inhibition/selectivity hypotheses remain unvalidated.')
    (REPORT / 'COMPARISON_RESULT.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))

if __name__ == '__main__':
    main()
