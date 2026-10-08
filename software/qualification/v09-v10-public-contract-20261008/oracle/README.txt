INDEPENDENT UPSTREAM ORACLE — REVIEW ARTIFACT, NOT ATHENA IMPLEMENTATION

Scientific sources are the immutable commits in the previous decision package.
UPSTREAM_HEADERS.json verifies every acquired cctbx file against the pinned Git
blob identity and records SHA-256. Generated headers come from those unmodified
upstream source generators; GENERATED_HEADERS.json pins their outputs. The original
cctbx and data licenses/attribution are retained in the previous package and bound
by SOURCES.json there. Preserve them with any redistribution of these artifacts.
System Boost headers and runtime wheels are not copied into this repository.

The C++ driver calls pinned rama_eval.h, its original compiled tables, and pinned
scitbx/math/dihedral.h directly. It contains no score/interpolation/category formula.
Compile command (repository root):
 c++ -std=c++11 -O0 -ffp-contract=off \
   -Isoftware/qualification/v09-v10-public-contract-20261008/oracle/upstream \
   -I/opt/homebrew/include -MMD -MF /private/tmp/athena-v09-v10-oracle.d \
   software/qualification/v09-v10-public-contract-20261008/oracle/driver.cpp \
   -o /private/tmp/athena-v09-v10-oracle

The isolated Python3.11 environment uses the exact wheels hashed in RUNTIME.json:
cctbx-base2025.11 supplies the real native flex.float array (not evaluator authority),
six1.17.0 and numpy1.26.4 supply runtime dependencies. Pinned n_dim_table.py is loaded
by absolute source path, not from that wheel. The exact pinned rotalyze.evaluateScore
AST is executed with its reviewed constants and an isolated counter object; there
is no reimplementation of its classification branches. Runtime package and native
module hashes, compiler/platform, Boost header hashes and executable digest are in
RUNTIME.json. The wheel is NOT claimed to have the selected evaluator commit.

Reproduce acquisition (network, immutable verified sources): fetch_headers.py.
Generate missing array headers: run generate_headers.py in the isolated environment.
Run fetch_headers.py again to acquire generated-header dependencies. No source
substitutes or mock array implementations are used.
Compile the C++ driver, then in that environment execute make_fixtures.py twice.
REPLAY.json records independent process results, hashes and counts.
The gzip stream is deterministic (mtime0, empty embedded filename), JSON records
have sorted keys and no NaN/Infinity. qHex/angleHex use Python's exact C99-style
float.hex notation, NOT the proposed public binary64Hex 16-digit field format.
Both represent the same exact IEEE value; fixtures are not public payload examples.

UPSTREAM_FIXTURES.jsonl.gz contains:
- every center in all six180x180 compiled Rama tables;
- fractional, midpoint and wrap/seam probes;
- every center and integer midpoint in the signed domain of all three rotamer grids;
- exact category nextDown/exact/nextUp score probes;
- nine coordinate torsion characterization probes, including degeneracy differences.
The 360-degree rotamer probe is explicitly outside selected signed-coordinate
input and is characterization only; it does not authorize multi-turn angles.

CLASS_ORACLE.json separately executes the exact upstream precedence branch AST
and cis interval expression on supplied synthetic canonical identity/omega facts.
It is NOT a residue-name admission oracle or a structure parser. It records the
upstream missing-omega=>trans behavior as a difference Athena must refuse.
make_class_fixtures.py pins the exact source lines/hash and generates580 probes.

ExistingGeometryProbe.java/compare_geometry.py call the existing Athena source
unchanged in an isolated temporary Java21 build. GEOMETRY_COMPARISON.json records
actual outputs and source pins. It is characterization, NOT a tolerance-based
qualification of cctbx-coordinate equivalence. PUBLIC_CONTRACT_REVIEW.txt section6
requests explicit approval of the minimal composition boundary.

No experimental structures, chemical-source authorities, production review receipts
or Athena V09/V10 implementations are fabricated by these numerical probes.
