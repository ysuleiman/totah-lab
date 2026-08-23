# External Python execution policy

Prometheus production code must not launch general-purpose Python. Historical artifacts generated
by PySCF, AmberTools workflows, or Python campaign scripts remain read-only
provenance evidence; they are not executable production dependencies.

Disabled Java launchers:

- `LockedPyscfEnergyGradientExecutor`
- `LockedPyscfForceTargetExecutor`

Both report `supports=false` so the executor registry cannot route work to them,
and both fail closed before creating files or starting a process. Replacement work
must be implemented and qualified in Java before these capabilities can be used
in production. Removing this boundary requires an explicit project decision and
must not occur as an incidental executor change.

## Deliberate numerical-backend exception

The frozen 60-point TSL-RSH PBE-D3(BJ)/def2-SVP force campaign authorizes exactly
one external process identity: `hardened-tslrsh-pyscf-energy-gradient`. Java
verifies the frozen inputs and owns a checksummed specification containing every
scientific and resource setting. The Python worker performs one calculation,
with no checkpoint search, fallback, retry, protocol selection, or acceptance
decision. Java independently validates and registers the result. This exception
does not re-enable either historical launcher or authorize other Python execution.
