# Step 3 validation-molecule selection

Selection was completed before any Step 3 solver execution.

| Candidate | Nuclei | Electrons | Heteronuclear | Independent internal coordinates | Decision |
|---|---:|---:|---|---:|---|
| HeH+ | 2 | 2 | yes | 1 | rejected: does not extend interacting-electron count or Cartesian geometry beyond H2 |
| LiH | 2 | 4 | yes | 1 | rejected for this gate: adds electrons and heteronuclear behavior but remains a one-coordinate diatomic |
| H3+ | 3 | 2 | no | 3 | rejected: adds nuclear geometry but not the required greater-than-two-electron test |
| H2O | 3 | 10 | yes | 3 | selected: smallest candidate covering three nuclei, heteronuclear centers, more than two electrons, stretch and bend coordinates, and nine Cartesian force components |

The H2O-13 archive of Bartok, Gillan, Manby and Csanyi (Physical Review B 88, 054104, 2013; DOI 10.1103/PhysRevB.88.054104) supplies published water-monomer geometries, energies, and forces. The frozen reference used here is its Partridge-Schwenke column, an independent high-accuracy water potential derived from correlated ab-initio calculations. The absolute electronic-energy zero is anchored to the nonrelativistic estimate `-76.4390 +/- 0.0004 hartree` of Bytautas and Ruedenberg (JCP 124, 174304, 2006; DOI 10.1063/1.2194542). No Prometheus result is used as reference.
