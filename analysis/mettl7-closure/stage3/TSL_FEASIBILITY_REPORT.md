# Productive TSL feasibility across the eight-system matrix

Status: **PASS**. All reported states satisfy the locked Stage 1 gates.

Each system tested the same 14,400 deterministic TSL placements. Static feasibility was evaluated before any receptor response. Limited response was used only when a system had no static passing state; SAM and TSL remained fixed.

| System | Static passing candidates | Static families | Accepted states | Required response |
|---|---:|---:|---:|---|
| METTL7A WT | 0 | 0 | 5 | side-chain or limited local backbone |
| METTL7A F43L | 0 | 0 | 5 | side-chain or limited local backbone |
| METTL7A F199G | 0 | 0 | 5 | side-chain or limited local backbone |
| METTL7A F43L/F199G | 0 | 0 | 5 | side-chain or limited local backbone |
| METTL7B WT | 7 | 6 | 6 | none |
| METTL7B L43F | 11 | 7 | 7 | none |
| METTL7B G199F | 1 | 1 | 1 | none |
| METTL7B L43F/G199F | 3 | 2 | 2 | none |

All 36 accepted states have zero protein or nonreactive-SAM heavy-atom pairs below 2.0 Å. Across limited-response states, maximum backbone RMSD is 0.0424 Å, maximum atomic displacement is 1.4140 Å, and maximum bond deviation is 0.0122 Å; each remains below the locked 0.25, 1.50, and 0.02 Å ceilings.

## Interpretation boundary

The matrix supports a reproducible computational difference in static productive-substrate accessibility: every METTL7B background retains at least one static state, whereas no METTL7A background does. The tested reciprocal substitutions modulate the number of METTL7B static families strongly—L43F expands and G199F contracts them—but neither 7A single nor double mutant creates a static passing state.

This does not establish catalytic rates, conformational populations, mutation causality in cells, or energetic favorability. State and family counts are geometric-search outcomes, not thermodynamic probabilities. The limited response is restrained geometry, not a validated molecular-mechanics trajectory.

The normative outputs are `matrix_summary.csv`, `all_states.csv`, and per-system coordinate/state records. `manifest.json` records every artifact checksum.
