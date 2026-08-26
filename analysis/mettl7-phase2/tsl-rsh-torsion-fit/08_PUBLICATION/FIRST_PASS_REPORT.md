# TSL-RSH publication torsion-fit first-pass report

## Decision

`PUBLICATION_INPUTS_VERIFIED = true`

`RAW_QM_ARTIFACTS_MODIFIED = false`

`BASELINE_FORCE_FIELD_IDENTIFIED = true`

`BASELINE_FORCE_FIELD_SHA256 = 2f4882aed1ea80e7b582a7b2cafa3dfd58ce4d918e5c9312186bcf3e28c88097`

The baseline is the accepted native AmberTools26 three-minimum RESP charge
model, unmodified GAFF2 2.2.30, and the original parmchk2 completion terms.

## Parameter-topology mapping

CHI `[55,25,9,8]` rotates the C9-S1 bond and is represented by:

- prmtop dihedral type 17: two `c6-c6-sh-hs` instances, n=3, phase=0,
  `phi_k=0.25 kcal/mol`, SCEE=1.2, SCNB=2.0;
- type 30: one `h1-c6-sh-hs` instance, n=3, phase=0,
  `phi_k=0.143 kcal/mol`, SCEE=1.2, SCNB=2.0.

PHI `[25,9,8,7]` rotates the C9-C8 bond and is represented by:

- type 12: seven mapped instances, n=3, phase=0,
  `phi_k=0.155555556 kcal/mol`; the generic type occurs 31 times in TSL-RSH;
- type 1: two mapped instances, n=3, phase=0,
  `phi_k=0.13 kcal/mol`; the generic type occurs 71 times.

PSI `[9,8,7,1]` rotates the C8-C7 bond and is represented by:

- type 2: four mapped instances, n=2, phase=0, `phi_k=0`; the generic type
  occurs ten times;
- type 7: two mapped instances, n=3, phase=0,
  `phi_k=0.15 kcal/mol`; both occurrences are mapped.

All terms use SCEE=1.2 and SCNB=2.0. No prmtop dihedral type is shared across
the three scanned axes. Nevertheless, types 1, 2, and 12 are shared with
unrelated physical dihedrals elsewhere in the molecule. Production fitting must
clone each type and assign it only to the mapped instances; mutating the generic
source type globally is prohibited.

`SHARED_TORSION_PARAMETERS = none across CHI/PHI/PSI; generic molecular sharing exists for types 1, 2, and 12`

## Frozen non-torsional model

Charges, bonds, angles, Lennard-Jones parameters, impropers, and 1-4 scaling are
frozen by canonical component hashes in
`02_TOPOLOGY_MAPPING/FROZEN_NON_TORSIONAL_PARAMETERS.json`. SAM, protein,
water, and ions are absent from this isolated gas-phase ligand calculation; they
are not adjusted here and must receive separate downstream system identities
before installation or MD.

## Frozen-geometry diagnostic baseline

| Surface | Points | RMSE | MAE | Maximum absolute error |
|---|---:|---:|---:|---:|
| CHI | 24 | 1.064374 | 0.929993 | 1.679496 |
| PHI | 18 | 7.723844 | 5.205647 | 18.232881 |
| PSI | 14 | 9.975601 | 6.944253 | 22.665410 |

All values are kcal/mol and use independently referenced QM and MM minima.
These single-point values are diagnostic decompositions only, not the final fit
objective.

## Production objective and identifiability

The production objective is preregistered as an MM-relaxed profile: initialize
from every authoritative QM geometry, restrain only the target torsion using the
documented Amber convention, relax all other coordinates with Sander, remove
the restraint contribution, and compare independently referenced relative
profiles. Equal-surface weighting is primary; equal-point weighting and the
energy-weighted sensitivity analysis are mandatory comparisons.

The fixed-geometry instance-local C1 design has 56 observations, six active
directions, numerical rank 6/6, singular values
`[24.672660, 8.157093, 5.624953, 3.077522, 2.205398, 0.473046]`, and condition
number 52.157010. This is not proof of nonlinear relaxed-profile
identifiability. Equal-surface weighting remains rank 6/6 with condition number
56.415377. The largest absolute inter-column cosine correlation is 0.969834,
which is the principal reason the classification remains `CONCERN` despite full
rank.

`FIT_MODEL_CANDIDATES = C0 immutable baseline; C1 six instance-local cloned existing-periodicity amplitudes; conditional C2 chemically justified n=1..3 additions only if C1 fails predictive gates`

`IDENTIFIABILITY = CONCERN`

## Acceptance and readiness

The literature-supported methodology review establishes relaxed profiles and a
1 kcal/mol characteristic energy scale, but no universal molecule-specific
acceptance gate. Conservative numerical gates are proposed in
`00_PROTOCOL/PROPOSED_ACCEPTANCE_GATES.json` and have not been applied.

`PROPOSED_ACCEPTANCE_GATES = relaxed-profile RMSE <=1.0 kcal/mol per surface; MAE <=0.75; max error <=2.0; minimum and major-barrier location error <=15 degrees; major-barrier height error <=1.0 kcal/mol; periodic closure <=0.1 kcal/mol; structured holdout improvement and correct critical-point topology required`

`ACCEPTANCE_GATES_LOCKED = false`

`NEW_QM_REQUIRED = false`

`REASON = existing authoritative 1D surfaces are sufficient for the preregistered first torsion-fit attempt; coupled validation may later expose a separate minimal-QM need, but none is established now`

`READY_TO_FIT = false`

Exact blockers:

1. explicit review and locking of the proposed acceptance gates;
2. production Sander restraint/minimization serialization, numerical-equivalence,
   and restart-determinism tests;
3. instance-local torsion-type cloning/read-back equivalence for generic types;
4. final preregistration of the C1-to-C2 model-admission decision.

No QM, parameter optimization, parameter installation, or production MD was
performed.
