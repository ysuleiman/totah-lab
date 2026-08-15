# Nuclear-Force Estimator Literature Review

## Scope

This review concerns force estimators only. The frozen H2 wavefunction,
parameters, optimizer result, deterministic configurations, Hamiltonian, and
scientific thresholds are not development variables.

## Authoritative methodological lineage

1. Assaraf and Caffarel introduced improved or renormalized observables and the
   zero-variance principle, which changes estimator variance without changing
   the target expectation value. DOI:
   [10.1103/PhysRevLett.83.4682](https://doi.org/10.1103/PhysRevLett.83.4682).

2. Their force-specific zero-variance/zero-bias construction explicitly targets
   both statistical fluctuation and approximate-wavefunction bias. DOI:
   [10.1063/1.1621615](https://doi.org/10.1063/1.1621615); archival manuscript:
   [arXiv:physics/0310035](https://arxiv.org/abs/physics/0310035).

3. Filippi and Umrigar showed that correlated sampling combined with a
   space-warp coordinate transformation can substantially reduce statistical
   error in numerical molecular forces. DOI:
   [10.1103/PhysRevB.61.R16291](https://doi.org/10.1103/PhysRevB.61.R16291);
   archival manuscript:
   [arXiv:cond-mat/9911326](https://arxiv.org/abs/cond-mat/9911326).

4. Qian, Fu, Ren, and Chen compared AC-ZV, AC-ZVZB, and SWCT estimators with
   neural-network VMC. They report that SWCT produced the lowest force variance
   in their tests, AC-ZVZB generally improved on AC-ZV, Pulay contributions can
   matter near equilibrium, and force accuracy remains related to wavefunction
   quality. DOI: [10.1063/5.0112344](https://doi.org/10.1063/5.0112344);
   manuscript: [arXiv:2207.07810](https://arxiv.org/abs/2207.07810).

5. Sorella and Capriotti combined adjoint algorithmic differentiation with
   differential SWCT, obtaining all nuclear-force components at a cost
   comparable to total-energy evaluation. This directly supports Prometheus's
   owned-Java computation-graph design. DOI:
   [10.1063/1.3516208](https://doi.org/10.1063/1.3516208); manuscript:
   [arXiv:1010.5560](https://arxiv.org/abs/1010.5560).

6. Nakano, Raghav, and Sorella benchmarked SWCT from H through Br dimers. They
   found force/energy cost scaling of approximately Z^2.5 without SWCT and an
   approximately Z-independent ratio with SWCT, while also requiring appropriate
   regularization of infinite-variance terms. DOI:
   [10.1063/5.0076302](https://doi.org/10.1063/5.0076302); manuscript:
   [arXiv:2110.12234](https://arxiv.org/abs/2110.12234).

7. Nakano et al. demonstrated VMC phonon dispersions and reported up to two
   orders of magnitude lower force statistical error when basis conditioning was
   combined with SWCT, corresponding to an effective efficiency gain up to 10^4
   over bare-force treatment. DOI:
   [10.1103/PhysRevB.103.L121110](https://doi.org/10.1103/PhysRevB.103.L121110).

## Consequences for Prometheus

- The frozen failure does not justify altering the shared H2 state.
- The very large raw force-estimator variances (27.9--46.4 Ha2/bohr2) make an
  estimator-variance study scientifically justified.
- Direct analytic, correlated finite-difference, SWCT, and ZVZB estimators must
  be distinct immutable implementations sharing a typed force request/result
  boundary.
- A lower variance alone is insufficient. Each estimator must preserve the same
  energy derivative and pass independent sign, unit, symmetry, and finite-
  difference checks.
- SWCT and ZVZB mathematics must be implemented from the published equations,
  not inferred from descriptive prose.
- Stationarity matters. Because the frozen shared optimizer did not converge,
  Prometheus must measure the variational gradient and separately audit the
  parameter-response term. This is not permission to optimize the state.
