# Step 3 multi-nuclear H2O validation

## Classification

`STEP_3_MULTI_NUCLEAR_VALIDATION_FAILED`

Dominant blocker: `sampling_variance`

The prior optimizer execution defect was corrected under the separately locked
numerical protocol. All three geometries now complete. Because the statistical
uncertainty gates fail by orders of magnitude, this result is **not** classified
or interpreted as an H2O physics failure.

## Frozen-gate results

- Completed geometries: 3/3
- Energy RMSE: 165.79892714 Ha (gate <= 0.010)
- Maximum absolute energy error: 169.05823659 Ha (gate <= 0.015)
- Force-component RMSE: 18.46000141 Ha/bohr (gate <= 0.010)
- Maximum force-component error: 37.21370233 Ha/bohr (gate <= 0.025)
- Maximum energy SE: 42.81244105 Ha
- Maximum force-component SE: 40.19622955 Ha/bohr
- Exact force=-gradient: true
- Immediate reuse with zero recomputation: true
- Maximum peak-heap growth: 464806496 bytes

Step 3 is frozen at this classification. The optimizer blocker is resolved;
sampling variance remains the blocker. No tuning, Step 4, or new molecule was
started.
