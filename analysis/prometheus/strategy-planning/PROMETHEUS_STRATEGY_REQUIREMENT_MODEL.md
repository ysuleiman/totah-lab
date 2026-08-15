# Prometheus strategy requirement model

Canonical generation: `a3b0b77e89dcb9d142482732ad3f147d00cb4dcb134f58fb62692152c86c3c3f`

This package is a read-only plan over 100 quantum and 22 classical records. It launches no QM, MM, fitting, or MD.

A strategy declares scientific evidence, protocol exactness, dataset role, derivability, outputs, dependencies, functional form, and engine compatibility. The matcher distinguishes reuse, derivation, generation, holdout reservation, protocol incompatibility, infrastructure blockage, and insufficient metadata. Derivation consumes authoritative artifacts; it is not recomputation.

## Registered methodologies

- `qube-like`: QM-derived bespoke force field using DDEC-like atoms-in-molecule electrostatics and dispersion. Functional form: Fixed atom-centred charges and pairwise 12-6 LJ; harmonic bonds/angles; periodic proper and improper torsions
- `qforce-like`: QM Hessian and torsion-scan force matching. Functional form: Classical fixed-charge form with harmonic bonds/angles and separable periodic proper/improper torsions
- `forcebalance-style`: regularized multi-target optimization. Functional form: Selected by the project: the optimizer only varies explicitly exposed parameters in an existing force-field form

## Scientific guardrails

The accepted RESP model is evidence, not DDEC. Protocol groups are never pooled silently. Existing model-form diagnoses—`ANGLE_LJ_COUPLED_DEFECT_SUPPORTED`, `HARMONIC_BONDED_FORM_INSUFFICIENT`, failed fixed-charge local repairs, and failed published-LJ comparators—are strategy constraints, not scores. Infrastructure failure is reported separately from scientific invalidity.
