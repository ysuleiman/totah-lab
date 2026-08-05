#!/usr/bin/env python3
"""METTL7A pocket 32 <-> METTL7B pocket 3 correspondence validation.

Pure-stdlib analysis: sequence extraction from artifact PDBs, global
Needleman-Wunsch alignment, greedy one-to-one residue matching (the
production rule), and alignment variants (current PCA+ICP via the API,
sequence-seeded Kabsch, key-residue-seeded Kabsch, trimmed ICP via
Horn quaternion Kabsch with power iteration).
"""
import json
import math
import subprocess
import urllib.request

PSQL = ["psql", "-h", "localhost", "-U", "postgres", "-d", "totah_lab_db", "-tAc"]
ENV = {"PGPASSWORD": "admin", "PATH": "/opt/homebrew/bin:/usr/bin:/bin"}
ROOT = "/Users/yazan/totah-lab/resources/shared-resources/src/main/resources"
API = "http://localhost:8080"

Q_POCKET, C_POCKET = 32, 3  # METTL7A query, METTL7B candidate

AA3TO1 = {
    'ALA':'A','ARG':'R','ASN':'N','ASP':'D','CYS':'C','GLN':'Q','GLU':'E',
    'GLY':'G','HIS':'H','ILE':'I','LEU':'L','LYS':'K','MET':'M','PHE':'F',
    'PRO':'P','SER':'S','THR':'T','TRP':'W','TYR':'Y','VAL':'V',
}
BACKBONE = {"N", "CA", "C", "O", "OXT"}
CONSERVATIVE_SETS = [
    {"LEU","ILE","VAL","MET"}, {"ASP","GLU"}, {"LYS","ARG"},
    {"SER","THR"}, {"ASN","GLN"}, {"PHE","TYR","TRP"},
]
CLASS_OF = {}
for cls, names in {
    'AROMATIC': "PHE TYR TRP", 'HYDROPHOBIC': "ALA VAL LEU ILE MET PRO",
    'POLAR': "SER THR ASN GLN", 'POSITIVE': "LYS ARG HIS",
    'NEGATIVE': "ASP GLU", 'CYSTEINE': "CYS", 'GLYCINE': "GLY",
}.items():
    for n in names.split():
        CLASS_OF[n] = cls
KEY_RESIDUES = ['CYS148','LEU145','HIS175','GLY199','ASP200','GLY201','CYS202','CYS203']
CUTOFF = 4.0


def psql(sql):
    out = subprocess.run(PSQL + [sql], capture_output=True, text=True, env=ENV)
    return out.stdout.strip()


def parse_pdb(path):
    """sequence (resnum -> aa3, ordered) and side-chain centroids."""
    seq = {}
    order = []
    atoms = {}
    with open(path) as fh:
        for line in fh:
            if not line.startswith("ATOM"):
                continue
            resnum = int(line[22:26])
            resname = line[17:20].strip()
            atom = line[12:16].strip()
            element = line[76:78].strip() or atom[0]
            x = float(line[30:38]); y = float(line[38:46]); z = float(line[46:54])
            if resnum not in seq:
                seq[resnum] = resname
                order.append(resnum)
            if element != 'H':
                atoms.setdefault(resnum, []).append((atom, (x, y, z)))
    centroids = {}
    for resnum, alist in atoms.items():
        resname = seq[resnum]
        if resname == 'GLY':
            ca = [a for a in alist if a[0] == 'CA']
            centroids[resnum] = ca[0][1] if ca else alist[0][1]
        else:
            sc = [p for a, p in alist if a not in BACKBONE]
            if not sc:
                ca = [a for a in alist if a[0] == 'CA']
                sc = [ca[0][1]] if ca else [alist[0][1]]
            centroids[resnum] = tuple(sum(p[i] for p in sc) / len(sc) for i in range(3))
    return seq, order, centroids


def nw_align(seq_a, seq_b):
    """Needleman-Wunsch, match +2 / mismatch -1 / gap -2. Returns pairs."""
    n, m = len(seq_a), len(seq_b)
    score = [[0]*(m+1) for _ in range(n+1)]
    for i in range(1, n+1): score[i][0] = -2*i
    for j in range(1, m+1): score[0][j] = -2*j
    for i in range(1, n+1):
        for j in range(1, m+1):
            match = score[i-1][j-1] + (2 if seq_a[i-1] == seq_b[j-1] else -1)
            score[i][j] = max(match, score[i-1][j]-2, score[i][j-1]-2)
    pairs = []
    i, j = n, m
    while i > 0 and j > 0:
        if score[i][j] == score[i-1][j-1] + (2 if seq_a[i-1] == seq_b[j-1] else -1):
            pairs.append((seq_a[i-1][0], seq_b[j-1][0], seq_a[i-1][1], seq_b[j-1][1]))
            i -= 1; j -= 1
        elif score[i][j] == score[i-1][j] - 2:
            i -= 1
        else:
            j -= 1
    pairs.reverse()
    return pairs


def sub(a, b): return tuple(x-y for x, y in zip(a, b))
def add(a, b): return tuple(x+y for x, y in zip(a, b))
def scale(a, s): return tuple(x*s for x in a)
def dist(a, b): return math.sqrt(sum((x-y)**2 for x, y in zip(a, b)))
def centroid(pts): return tuple(sum(p[i] for p in pts)/len(pts) for i in range(3))


def mat_vec(M, v):
    return tuple(sum(M[i][j]*v[j] for j in range(3)) for i in range(3))


def mat_mul(A, B):
    return [[sum(A[i][k]*B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def quat_to_mat(q):
    w, x, y, z = q
    return [
        [1-2*(y*y+z*z), 2*(x*y-w*z), 2*(x*z+w*y)],
        [2*(x*y+w*z), 1-2*(x*x+z*z), 2*(y*z-w*x)],
        [2*(x*z-w*y), 2*(y*z+w*x), 1-2*(x*x+y*y)],
    ]


def power_iteration(M, iterations=200):
    v = [1.0, 0.0, 0.0, 0.0]
    for _ in range(iterations):
        w = [sum(M[i][j]*v[j] for j in range(4)) for i in range(4)]
        norm = math.sqrt(sum(x*x for x in w))
        if norm < 1e-12:
            return v
        v = [x/norm for x in w]
    return v


def kabsch(source, target):
    """Rigid transform (R, t) mapping source points onto target points."""
    cs, ct = centroid(source), centroid(target)
    S = [[0.0]*3 for _ in range(3)]
    for p, q in zip(source, target):
        ps, qt = sub(p, cs), sub(q, ct)
        for i in range(3):
            for j in range(3):
                S[i][j] += ps[i]*qt[j]
    sxx, sxy, sxz = S[0]
    syx, syy, syz = S[1]
    szx, szy, szz = S[2]
    N = [
        [sxx+syy+szz, syz-szy, szx-sxz, sxy-syx],
        [syz-szy, sxx-syy-szz, sxy+syx, szx+sxz],
        [szx-sxz, sxy+syx, -sxx+syy-szz, syz+szy],
        [sxy-syx, szx+sxz, syz+szy, -sxx-syy+szz],
    ]
    R = quat_to_mat(power_iteration(N))
    t = sub(ct, mat_vec(R, cs))
    return R, t


def apply(R, t, p):
    return add(mat_vec(R, p), t)


def icp(query_pts, cand_pts, R, t, trim_fraction=0.0, iterations=50):
    best_R, best_t = R, t
    best_error = float('inf')
    for _ in range(iterations):
        moved = [apply(R, t, p) for p in cand_pts]
        pairs = []
        for i, p in enumerate(moved):
            j = min(range(len(query_pts)), key=lambda k: dist(p, query_pts[k]))
            pairs.append((dist(p, query_pts[j]), i, j))
        pairs.sort()
        keep = pairs[:max(3, int(len(pairs) * (1 - trim_fraction)))]
        # degenerate correspondence sets (many-to-one collapse) must
        # not drive Kabsch
        if len({j for _, _, j in keep}) < 3:
            break
        src = [cand_pts[i] for _, i, _ in keep]
        tgt = [query_pts[j] for _, _, j in keep]
        mean_error = sum(d for d, _, _ in keep) / len(keep)
        if mean_error > best_error + 1e-9:
            break  # diverging; keep the best transform seen
        best_error = mean_error
        best_R, best_t = R, t
        dR, dt = kabsch(src, tgt)
        R = mat_mul(dR, R)
        t = add(mat_vec(dR, t), dt)
    return best_R, best_t


def geometry_metrics(query_pts, cand_pts_transformed):
    def stats(src, tgt):
        ds = [min(dist(p, q) for q in tgt) for p in src]
        return sum(ds)/len(ds), sum(1 for x in ds if x <= 2.0)/len(ds)
    qm, qcov = stats(query_pts, cand_pts_transformed)
    cm, ccov = stats(cand_pts_transformed, query_pts)
    bidir = (qm + cm) / 2
    distance_sim = 1.0 / (1.0 + bidir)
    geom = min(1.0, distance_sim * math.sqrt(qcov * ccov))
    return bidir, qcov, ccov, geom


def greedy_match(query_res, cand_res_transformed):
    pairs = []
    for qi, (ql, qp) in enumerate(query_res):
        for ci, (cl, cp) in enumerate(cand_res_transformed):
            d = dist(qp, cp)
            if d <= CUTOFF:
                pairs.append((d, ql, cl, qi, ci))
    pairs.sort(key=lambda p: (p[0], p[1], p[2]))
    used_q, used_c, out = set(), set(), []
    for d, ql, cl, qi, ci in pairs:
        if qi in used_q or ci in used_c:
            continue
        used_q.add(qi); used_c.add(ci)
        out.append((ql, cl, d))
    return out


def parse_label(label):
    """'A:CYS202' -> ('A', 'CYS', 202)."""
    chain, rest = label.split(':')
    name = ''.join(ch for ch in rest if ch.isalpha())
    number = int(''.join(ch for ch in rest if ch.isdigit()))
    return chain, name, number


def chemistry(q_name, c_name):
    if q_name == c_name:
        return 'IDENTICAL', True, True
    qc, cc = CLASS_OF.get(q_name, 'OTHER'), CLASS_OF.get(c_name, 'OTHER')
    if qc in ('CYSTEINE', 'GLYCINE') or cc in ('CYSTEINE', 'GLYCINE'):
        return 'DIFFERENT', False, False
    if any(q_name in s and c_name in s for s in CONSERVATIVE_SETS):
        return 'CONSERVATIVE', False, True
    if qc == cc:
        return 'CHEMISTRY_COMPATIBLE', False, True
    return 'DIFFERENT', False, False


def main():
    q_pdb = f"{ROOT}/Q9H8H3/Q9H8H3_TMT1A_HUMAN.pdb"
    c_pdb = f"{ROOT}/Q6UX53/Q6UX53_TMT1B_HUMAN.pdb"
    q_seq, q_order, q_cent = parse_pdb(q_pdb)
    c_seq, c_order, c_cent = parse_pdb(c_pdb)

    q_pocket = [int(x) for x in psql(
        "select residue_number from docking.pocket_residue where pocket_id = 32 order by residue_number").split()]
    c_pocket = [int(x) for x in psql(
        "select residue_number from docking.pocket_residue where pocket_id = 3 order by residue_number").split()]

    q_spheres = [tuple(map(float, r.split('|'))) for r in psql(
        "select center_x||'|'||center_y||'|'||center_z from docking.pocket_alpha_sphere where pocket_id = 32 order by sphere_index").splitlines()]
    c_spheres = [tuple(map(float, r.split('|'))) for r in psql(
        "select center_x||'|'||center_y||'|'||center_z from docking.pocket_alpha_sphere where pocket_id = 3 order by sphere_index").splitlines()]

    print(f"sequences: METTL7A {len(q_order)} res, METTL7B {len(c_order)} res")
    print(f"pocket residues: query(32) {len(q_pocket)}, candidate(3) {len(c_pocket)}")
    print(f"spheres: query {len(q_spheres)}, candidate {len(c_spheres)}")

    qa = [(r, q_seq[r]) for r in q_order]
    ca = [(r, c_seq[r]) for r in c_order]
    pairs = nw_align(qa, ca)
    identity = sum(1 for _, _, a, b in pairs if a == b) / len(pairs)
    print(f"alignment: {len(pairs)} aligned positions, identity {identity:.2f}")

    q_to_c = {qr: (cr, caa) for qr, cr, qaa, caa in pairs}
    q_pocket_set, c_pocket_set = set(q_pocket), set(c_pocket)

    # --- CSV 1: sequence mapping for pocket-32 residues ---
    with open('/tmp/mettl7a_p32_mettl7b_p3_sequence_mapping.csv', 'w') as fh:
        fh.write("query_residue,candidate_residue,query_amino_acid,candidate_amino_acid,"
                 "alignment_status,identical,conservative,query_in_pocket,candidate_in_pocket\n")
        for qr in q_pocket:
            qaa = q_seq[qr]
            if qr in q_to_c:
                cr, caa = q_to_c[qr]
                _, ident, compat = chemistry(qaa, caa)
                cons = (not ident) and compat
                fh.write(f"A:{qaa}{qr},A:{caa}{cr},{qaa},{caa},aligned,{ident},{cons},true,{str(cr in c_pocket_set).lower()}\n")
            else:
                fh.write(f"A:{qaa}{qr},,{qaa},,unaligned,false,false,true,false\n")
        aligned_q = {qr for qr, _, _, _ in pairs}
        for cr in c_pocket:
            if any(c == cr for _, c, _, _ in pairs):
                continue
            fh.write(f",A:{c_seq[cr]}{cr},,{c_seq[cr]},unaligned,false,false,false,true\n")
    print("wrote sequence mapping CSV")

    shared = [(qr, cr) for qr, (cr, _) in q_to_c.items()
              if qr in q_pocket_set and cr in c_pocket_set and qr in q_cent and cr in c_cent]
    print(f"sequence-aligned residue pairs present in BOTH pockets: {len(shared)}")
    for qr, cr in shared:
        print(f"   A:{q_seq[qr]}{qr} <-> A:{c_seq[cr]}{cr} {'identical' if q_seq[qr]==c_seq[cr] else 'substitution'}")

    # --- alignment variants ---
    q_sphere_centroids = q_spheres
    c_sphere_centroids = c_spheres

    variants = {}
    # (a) current PCA+ICP transform from the API
    api = json.load(urllib.request.urlopen(f"{API}/api/pockets/32/compare/3", timeout=240))
    T = api['transform']
    R_api = T['rotation']
    t_api = (T['translation']['x'], T['translation']['y'], T['translation']['z'])
    variants['PCA+ICP (production)'] = (R_api, t_api)

    # (b) sequence-seeded Kabsch (+ ICP refine)
    if len(shared) >= 3:
        src = [c_cent[cr] for _, cr in shared]
        tgt = [q_cent[qr] for qr, _ in shared]
        R0, t0 = kabsch(src, tgt)
        variants['sequence-seeded Kabsch'] = (R0, t0)
        R1, t1 = icp(q_sphere_centroids, c_sphere_centroids, R0, t0)
        variants['sequence-seeded Kabsch + ICP'] = (R1, t1)

    # (c) key-residue-seeded Kabsch + ICP (key residues that are sequence-aligned and in both pockets)
    key_pairs = [(qr, cr) for qr, cr in shared
                 if f"{q_seq[qr]}{qr}" in KEY_RESIDUES]
    if len(key_pairs) >= 3:
        R0, t0 = kabsch([c_cent[cr] for _, cr in key_pairs],
                        [q_cent[qr] for qr, _ in key_pairs])
        variants['key-residue-seeded Kabsch + ICP'] = icp(
            q_sphere_centroids, c_sphere_centroids, R0, t0)
    else:
        print(f"key-residue-seeded: only {len(key_pairs)} usable pairs (<3)")

    # (d) trimmed ICP from the production transform
    variants['PCA + trimmed ICP (25%)'] = icp(
        q_sphere_centroids, c_sphere_centroids, R_api, t_api, trim_fraction=0.25)

    q_res_points = [(f"A:{q_seq[r]}{r}", q_cent[r]) for r in q_pocket if r in q_cent]
    c_res_points = [(f"A:{c_seq[r]}{r}", c_cent[r]) for r in c_pocket if r in c_cent]

    # --- evaluate each variant ---
    report = []
    for name, (R, t) in variants.items():
        moved_spheres = [apply(R, t, p) for p in c_sphere_centroids]
        bidir, qcov, ccov, geom = geometry_metrics(q_sphere_centroids, moved_spheres)
        moved_res = [(l, apply(R, t, p)) for l, p in c_res_points]
        matches = greedy_match(q_res_points, moved_res)
        seq_consistent = sum(
            1 for ql, cl, _ in matches
            if q_to_c.get(parse_label(ql)[2], (None, None))[0]
               == parse_label(cl)[2]
        )
        counts = {'IDENTICAL': 0, 'CONSERVATIVE': 0, 'CHEMISTRY_COMPATIBLE': 0, 'DIFFERENT': 0}
        for ql, cl, _ in matches:
            qn = parse_label(ql)[1]
            cn = parse_label(cl)[1]
            mt, _, _ = chemistry(qn, cn)
            counts[mt] += 1
        m = len(matches)
        acceptable = counts['IDENTICAL'] + counts['CONSERVATIVE'] + counts['CHEMISTRY_COMPATIBLE']
        chem_sim = ((1.0*counts['IDENTICAL'] + 0.7*counts['CONSERVATIVE']
                     + 0.8*counts['CHEMISTRY_COMPATIBLE']) / m) if m else 0.0
        compat_frac = acceptable / m if m else 0.0
        repl_frac = counts['DIFFERENT'] / m if m else 1.0
        report.append((name, geom, qcov, ccov, bidir, m, seq_consistent,
                       chem_sim, compat_frac, repl_frac, counts))
        print(f"\n== {name}")
        print(f"   geometry={geom:.3f} fwdCov={qcov:.2f} revCov={ccov:.2f} bidir={bidir:.2f}A")
        print(f"   matched={m} seq-consistent={seq_consistent}")
        print(f"   chemistry={chem_sim:.3f} compatible={compat_frac:.2f} replacements={repl_frac:.2f}")
        print(f"   counts={counts}")

    with open('/tmp/mettl7a_p32_mettl7b_p3_alignment_comparison.csv', 'w') as fh:
        fh.write("method,geometry_similarity,forward_coverage,reverse_coverage,"
                 "bidirectional_distance,matched_residues,sequence_consistent,"
                 "chemistry_similarity,compatible_fraction,replacement_fraction,"
                 "identical,conservative,chemistry_compatible,spatial_replacements\n")
        for row in report:
            name, geom, qcov, ccov, bidir, m, sc, chem, comp, repl, counts = row
            fh.write(f"\"{name}\",{geom:.3f},{qcov:.2f},{ccov:.2f},{bidir:.2f},{m},{sc},"
                     f"{chem:.3f},{comp:.2f},{repl:.2f},{counts['IDENTICAL']},"
                     f"{counts['CONSERVATIVE']},{counts['CHEMISTRY_COMPATIBLE']},{counts['DIFFERENT']}\n")
    print("\nwrote alignment comparison CSV")


if __name__ == '__main__':
    main()
