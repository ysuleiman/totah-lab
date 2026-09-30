"""Thin audited OpenMM process adapter. No force-field mathematics lives here."""
import json
import sys
from pathlib import Path

import openmm
from openmm import unit


def load_request(path):
    with Path(path).open("r", encoding="utf-8") as handle:
        return json.load(handle)


def load_system(request):
    fmt = request["systemFormat"]
    if fmt == "OPENMM_XML":
        with Path(request["systemArtifact"]).open("r", encoding="utf-8") as handle:
            system = openmm.XmlSerializer.deserialize(handle.read())
        with Path(request["coordinateArtifact"]).open("r", encoding="utf-8") as handle:
            payload = json.load(handle)
        positions = [openmm.Vec3(*xyz) for xyz in payload["positionsNanometres"]] * unit.nanometer
        box = payload.get("boxVectorsNanometres")
        box_vectors = None if box is None else tuple(openmm.Vec3(*xyz) * unit.nanometer for xyz in box)
        return system, positions, box_vectors
    raise ValueError("unsupported/unfrozen systemFormat: " + fmt
                     + "; provide serialized OPENMM_XML")


def validate_frozen_groups(system, declarations):
    expected = {int(group): name for group, name in declarations.items()}
    seen = set()
    for force in system.getForces():
        force_name = force.__class__.__name__
        matches = [group for group, declared in expected.items()
                   if declared == force_name and group == force.getForceGroup()]
        if len(matches) != 1:
            raise ValueError(f"frozen force {force_name} group={force.getForceGroup()} has "
                             f"{len(matches)} exact declared group matches")
        seen.add(matches[0])
    if seen != set(expected):
        raise ValueError(f"declared force groups not present; declared={sorted(expected)}, seen={sorted(seen)}")


def main(request_path, response_path):
    request = load_request(request_path)
    system, positions, box_vectors = load_system(request)
    validate_frozen_groups(system, request.get("frozenForceGroups", request["forceGroups"]))
    platform = openmm.Platform.getPlatformByName(request["platform"])
    properties = request["platformProperties"]
    integrator = openmm.VerletIntegrator(0.001 * unit.picoseconds)
    context = openmm.Context(system, integrator, platform, properties)
    try:
        if "positionsNanometres" in request:
            positions = [openmm.Vec3(*xyz) for xyz in request["positionsNanometres"]] * unit.nanometer
        if request.get("operation", "ENERGY") == "MINIMIZE":
            reference = positions.value_in_unit(unit.nanometer)
            restrained = [atom for atom in request["selection"]
                          if atom["mobility"] == "POSITION_RESTRAINED"]
            fixed = [atom for atom in request["selection"] if atom["mobility"] == "FIXED"]
            for atom in fixed:
                system.setParticleMass(atom["particleIndex"], 0.0)
            if restrained:
                restraint = openmm.CustomExternalForce(
                    "0.5*k*((x-x0)^2+(y-y0)^2+(z-z0)^2)")
                restraint.addPerParticleParameter("k")
                restraint.addPerParticleParameter("x0")
                restraint.addPerParticleParameter("y0")
                restraint.addPerParticleParameter("z0")
                restraint.setForceGroup(request["restraintForceGroup"])
                constants = request["restraintConstantsKilojoulesPerMoleNanometreSquared"]
                for atom in restrained:
                    index = atom["particleIndex"]
                    xyz = reference[index]
                    restraint.addParticle(index, [constants[atom["componentRole"]], *xyz])
                system.addForce(restraint)
            # Force/mass changes must precede Context construction.
            del context
            del integrator
            integrator = openmm.VerletIntegrator(0.001 * unit.picoseconds)
            context = openmm.Context(system, integrator, platform, properties)
        context.setPositions(positions)
        if box_vectors is not None:
            context.setPeriodicBoxVectors(*box_vectors)
        operation = request.get("operation", "ENERGY")
        converged = True
        if operation == "MINIMIZE":
            pre_total = context.getState(getEnergy=True).getPotentialEnergy().value_in_unit(
                unit.kilojoules_per_mole)
            pre_groups = {str(group): context.getState(getEnergy=True, groups=1 << group)
                .getPotentialEnergy().value_in_unit(unit.kilojoules_per_mole)
                for group in sorted(int(value) for value in request["forceGroups"])}
            openmm.LocalEnergyMinimizer.minimize(context,
                request["forceToleranceKilojoulesPerMoleNanometre"]
                * unit.kilojoules_per_mole / unit.nanometer,
                request["maximumIterations"])
            force_state = context.getState(getForces=True)
            forces = force_state.getForces(asNumpy=True).value_in_unit(
                unit.kilojoules_per_mole / unit.nanometer)
            fixed_indices = {atom["particleIndex"] for atom in fixed}
            maximum_force = max((sum(float(v) ** 2 for v in force) ** 0.5
                                 for index, force in enumerate(forces)
                                 if index not in fixed_indices),
                                default=0.0)
            converged = maximum_force <= request["forceToleranceKilojoulesPerMoleNanometre"]
        groups = {}
        for group in sorted(int(value) for value in request["forceGroups"]):
            state = context.getState(getEnergy=True, groups=1 << group)
            groups[str(group)] = state.getPotentialEnergy().value_in_unit(unit.kilojoules_per_mole)
        total = context.getState(getEnergy=True).getPotentialEnergy().value_in_unit(
            unit.kilojoules_per_mole)
        result = {
            "forceGroupKilojoulesPerMole": groups,
            "totalKilojoulesPerMole": total,
            "openMmVersion": openmm.__version__,
            "platform": platform.getName(),
            "platformProperties": {name: context.getPlatform().getPropertyValue(context, name)
                                   for name in properties},
            "minimized": operation == "MINIMIZE",
            "converged": converged,
        }
        if operation == "MINIMIZE":
            final_state = context.getState(getPositions=True)
            result["positionsNanometres"] = [list(map(float, xyz)) for xyz in
                final_state.getPositions(asNumpy=True).value_in_unit(unit.nanometer)]
            result["maximumForceKilojoulesPerMoleNanometre"] = maximum_force
            result["preMinimizationTotalKilojoulesPerMole"] = pre_total
            result["preMinimizationForceGroupKilojoulesPerMole"] = pre_groups
        with Path(response_path).open("w", encoding="utf-8") as handle:
            json.dump(result, handle, indent=2, sort_keys=True)
            handle.write("\n")
    finally:
        del context
        del integrator


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: athena_openmm_runner.py REQUEST_JSON RESPONSE_JSON")
    main(sys.argv[1], sys.argv[2])
