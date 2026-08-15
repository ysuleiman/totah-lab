# Frozen Baseline Replay Audit

The preregistered bitwise replay gate failed at two of three geometries.

| R (bohr) | Historical serialized value | Replayed value (17 digits) | Historical binary64 | Replayed binary64 | Difference |
|---:|---:|---:|---|---|---:|
| 1.0 | 0.3119796341184747 | 0.31197963411847474 | `3fd3f7796d63a429` | `3fd3f7796d63a42a` | +1 ULP |
| 1.4 | -0.01693434536884336 | -0.016934345368843355 | `bf91573cae27458f` | `bf91573cae27458e` | -1 ULP |
| 3.0 | -0.07331633902930705 | -0.073316339029307050 | `bfb2c4dc0e64b0c3` | `bfb2c4dc0e64b0c3` | 0 ULP |

The frozen Generation result writer used `%.16g`, which is insufficient to
guarantee round-trip recovery of every binary64 value. The observed differences
are approximately `5.55e-17` and `3.47e-18 Ha/bohr`, respectively. They are not
scientifically material, but the archived record cannot prove bitwise identity.

Per the locked protocol, the formal study classification remains
`FROZEN_BASELINE_REPLAY_MISMATCH`. The complete estimator outputs are preserved
as diagnostic evidence but are not promoted into a production-estimator
decision. No estimator was rerun or adjusted.

