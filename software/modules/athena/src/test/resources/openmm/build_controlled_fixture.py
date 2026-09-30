"""Construct a small analytic OpenMM fixture for Java/process-boundary tests."""
import json
import sys
from pathlib import Path

import openmm
from openmm import unit


output = Path(sys.argv[1])
system = openmm.System()
for _ in range(4):
    system.addParticle(12.0)

nonbonded = openmm.NonbondedForce()
for charge in (1.0, -1.0, 0.0, 0.0):
    nonbonded.addParticle(charge, 0.30, 0.20)
system.addForce(nonbonded)

bonds = openmm.HarmonicBondForce()
bonds.addBond(0, 1, 0.25, 100.0)
system.addForce(bonds)

angles = openmm.HarmonicAngleForce()
angles.addAngle(0, 1, 2, 1.8, 20.0)
system.addForce(angles)

torsions = openmm.PeriodicTorsionForce()
torsions.addTorsion(0, 1, 2, 3, 1, 0.0, 2.0)
system.addForce(torsions)

restraint = openmm.CustomExternalForce("0.5*k*((x-x0)^2+(y-y0)^2+(z-z0)^2)")
restraint.addGlobalParameter("k", 10.0)
for parameter in ("x0", "y0", "z0"):
    restraint.addPerParticleParameter(parameter)
restraint.addParticle(3, [0.70, 0.10, 0.20])
if len(sys.argv) <= 2 or sys.argv[2] != "NO_FROZEN_RESTRAINT":
    system.addForce(restraint)

for group, force in enumerate(system.getForces(), start=1):
    force.setForceGroup(group)
if len(sys.argv) > 2 and sys.argv[2] == "WRONG_FORCE_GROUP":
    system.getForce(0).setForceGroup(0)

(output / "system.xml").write_text(openmm.XmlSerializer.serialize(system), encoding="utf-8")
coordinates = {"positionsNanometres": [[0, 0, 0], [0.25, 0, 0],
                                         [0.45, 0.15, 0], [0.70, 0.10, 0.20]]}
(output / "coordinates.json").write_text(json.dumps(coordinates), encoding="utf-8")

platform = openmm.Platform.getPlatformByName("CPU")
integrator = openmm.VerletIntegrator(0.001 * unit.picoseconds)
context = openmm.Context(system, integrator, platform,
                         {"Threads": "8", "DeterministicForces": "true"})
context.setPositions([openmm.Vec3(*xyz) for xyz in coordinates["positionsNanometres"]]
                     * unit.nanometer)
reference = {
    "totalKilojoulesPerMole": context.getState(getEnergy=True).getPotentialEnergy().value_in_unit(
        unit.kilojoules_per_mole),
    "forceGroupKilojoulesPerMole": {
        str(group): context.getState(getEnergy=True, groups=1 << group)
        .getPotentialEnergy().value_in_unit(unit.kilojoules_per_mole)
        for group in range(1, 6)
    },
}
(output / "independent-reference.json").write_text(json.dumps(reference), encoding="utf-8")
del context
del integrator
