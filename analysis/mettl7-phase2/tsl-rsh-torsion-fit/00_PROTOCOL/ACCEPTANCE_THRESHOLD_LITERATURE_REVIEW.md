# Torsion-fit acceptance-threshold literature review

## Established methodology

Betz and Walker's Paramfit paper establishes simultaneous fitting of classical
energies or gradients to QM scan data, including sparse datasets, but does not
define a universal accuracy threshold (J. Comput. Chem. 2015,
DOI: [10.1002/jcc.23775](https://doi.org/10.1002/jcc.23775)).

OpenFF Sage evaluates independently referenced QM and MM relative torsion
profiles after constrained MM relaxation. Its target construction uses weak
positional restraint to prevent unrelated force-field defects from causing large
structural changes (JCTC 2023,
DOI: [10.1021/acs.jctc.3c00039](https://doi.org/10.1021/acs.jctc.3c00039)).

OpenFF BespokeFit uses a 1.0 kcal/mol torsion-profile scaling factor, relaxed MM
profiles, and energy weighting that is constant through 1 kcal/mol and attenuated
to zero at 10 kcal/mol. Reported optimized profile RMSEs around 0.35 kcal/mol are
benchmark performance, not a universal pass threshold (JCIM 2023,
DOI: [10.1021/acs.jcim.2c01153](https://doi.org/10.1021/acs.jcim.2c01153)).

AFFDO performs Amber-compatible constrained MM scans with a 500
kcal/mol/rad2 target-dihedral restraint and a +/-0.5-degree tolerance band,
supporting the explicit execution convention preregistered here (JCIM 2026,
DOI: [10.1021/acs.jcim.6c00528](https://doi.org/10.1021/acs.jcim.6c00528)).

## Decision

The literature supports the objective form, relaxation requirement, weighting
sensitivity analysis, and a 1 kcal/mol characteristic scale. It does not justify
a universal complete set of pass/fail limits for TSL-RSH. Therefore the values in
`PROPOSED_ACCEPTANCE_GATES.json` are conservative project proposals for explicit
review, not retrospectively applied gates.

`ACCEPTANCE_GATES_LOCKED = false`.
