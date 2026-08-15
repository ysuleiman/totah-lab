# ForceBalance protocol qualification pilot

This is a two-geometry protocol qualification, not a parameter fit and not a QM campaign.

Acceptance gates were fixed in software before result inspection:

- exact input-geometry SHA-256 and vector lengths;
- converged SCF result;
- energy units `hartree`, gradient/force units `hartree/bohr`;
- `force = -gradient` maximum absolute error <= 1e-12;
- finite-difference/analytic gradient error <= 5e-5 hartree/bohr;
- historical/new fixed-geometry energy agreement <= 1e-6 hartree for direct qualification.

The control is an existing verified QM-native minimum. The problem geometry is the preregistered
Unit 05L state selected by the largest reported C2-H11 ordinary-LJ response (-11.584998 kcal/mol).
No calculation is launched by this report generator.

## Authoritative inputs

- `/Users/yazan/totah-lab/analysis/prometheus/forcebalance-protocol-pilot/PILOT_CALCULATION_SPECIFICATIONS.json` — SHA-256 `01e7e7639d6d980c08d4bffea78148d913e3c95b5aaec7828e7eeea9f3f44d69`
- `/Users/yazan/totah-lab/analysis/mettl7-phase2/execution-unit-05O/qm-native-minima/MIN02/final.xyz` — SHA-256 `38336cb66b98c55b5d1d15edd9fbcbdc55e6f7840894af6b43f02372cfd9f3f8`
- `/Users/yazan/totah-lab/analysis/mettl7-phase2/execution-unit-05O/qm-native-minima/MIN02/result.json` — SHA-256 `9f23b0f9fbb5d1215f9f18841a5bccd27801836984ef31272af8f900e1847fa5`
- `/Users/yazan/totah-lab/analysis/mettl7-phase2/execution-unit-05O/qm-native-minima/MIN02/final_gradient_hartree_per_bohr.txt` — SHA-256 `cd1c721e4fafbdf7b80cb54a42b7977cbd82c136c4bd71e256072456c0aef27a`
- `/Users/yazan/totah-lab/analysis/mettl7-phase2/execution-unit-05L/points/phi060_psi060_B_m10/final.xyz` — SHA-256 `592826ec52b670ed031a756d458e26593b3715368e26abef71f25eaf307fe338`
- `/Users/yazan/totah-lab/analysis/mettl7-phase2/execution-unit-05L/points/phi060_psi060_B_m10/result.json` — SHA-256 `c68c6d7500b18909e8e89a9dd269f0a96deb7cf98e4ed04fbef397ff2d65be32`
- `/Users/yazan/totah-lab/analysis/prometheus/forcebalance-protocol-pilot/raw/forcebalance-pilot-phi060-psi060-B-m10/result.json` — SHA-256 `7120996b48179a0aa859c2237ee5543d9bf048bba8339f939e522ac1d66c8d68`
- `/Users/yazan/totah-lab/analysis/mettl7-phase2/execution-unit-05L/SPARSE_TWO_ANGLE_CONTACT_RESPONSE.csv` — SHA-256 `0a8422665c4758c2863334448fb4ecef45d1677741c490e5d06d891042b74d85`
