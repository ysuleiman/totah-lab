# Controlled Experiment 1 Execution Record

## Immutable identities

- Protocol-lock commit: `5ae4e6bd1`
- Implementation commit used for execution: `ae6b9acd7`
- Protocol SHA-256:
  `eb94e90a7dd499ef0a963f4f58ad5213b867db0d2d7cc62cc1684831837ec162`
- Capability class: `REFERENCE_ASSISTED_DIAGNOSTIC`
- Production ab-initio eligibility: `false`

## Progression record

### Problem

The frozen geometry-conditioned H2 model has a good energy curve, but the
compressed R=1.0 PES slope carries most of the trusted-force error. SWCT tracks
that frozen slope closely, so estimator improvement alone cannot repair it.

### Hypothesis

The existing state may have sufficient representational capacity if supplied
one dimensionless, reference-assisted local slope constraint. This would be a
capacity diagnosis only, not production mathematics.

### Mathematical selection

The protocol selected symmetric force-derived local-energy constraints over a
direct force loss. The selected form required ordinary energy/parameter-gradient
evaluations at R=0.95 and 1.05 rather than a new analytic mixed derivative.

### Pre-training experiment

The locked correctness gate compared the proposed VMC covariance RHS with a
centered finite-difference derivative of the exact finite deterministic
diagnostic loss for all 20 parameters. The audit used the locked 2,500 samples,
step `2e-6`, and tolerance `3e-5`.

### Result

- Classification: `DERIVATIVE_AWARE_OBJECTIVE_CORRECTNESS_DEFECT`
- Maximum component mismatch: `17.040986779063722`
- RMS component mismatch: `7.147440083522716`
- Gate: FAIL
- State evaluations: `1,127,500`
- Local-energy evaluations: `1,127,500`
- Objective evaluations: `41`
- Wall time: `11,131,902,875 ns`
- Training iterations: `0`
- Holdouts opened: `false`

### Interpretation

The VMC covariance expression is the expectation-level variational energy
gradient. It is not the exact derivative of this finite deterministic quadrature
loss because that finite objective also changes through the sampled local-energy
term. The locked protocol incorrectly required the two finite-sample quantities
to agree to an absolute deterministic tolerance. The implementation did not
silently weaken that gate.

This result does **not** establish:

- insufficient wavefunction/ansatz capacity;
- failure of derivative-aware training in general;
- an error in the frozen SWCT evidence;
- a need for force-supervised production Prometheus.

### Architectural decision

Stop Controlled Experiment 1 before training. Preserve the negative evidence.
Do not open derivative holdouts or report before/after candidate energies and
forces because no candidate exists.

A future experiment, if separately authorized, must choose one of two coherent
mathematical paths before locking:

1. differentiate the finite diagnostic objective exactly, which requires the
   missing derivatives of the local-energy/Laplacian path; or
2. treat the covariance RHS as a stochastic gradient estimator and preregister
   a statistical agreement/uncertainty gate rather than exact finite-quadrature
   equality.

That is a new architecture decision; this protocol cannot be repaired after the
result.

## Validation and reproducibility

- The checksum-locked protocol bundle was verified synchronously before the
  audit.
- Raw result artifacts were synchronously written and checksummed.
- The implementation's targeted tests passed: 4 tests, 0 failures.
- The complete Gaia+Prometheus reactor passed: Gaia 134 tests plus all 60
  Prometheus test classes, with no failures observed.
- No frozen historical artifact was modified.
