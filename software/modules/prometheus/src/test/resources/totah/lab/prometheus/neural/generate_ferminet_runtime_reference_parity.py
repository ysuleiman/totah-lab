#!/usr/bin/env python3
"""Generate the frozen DeepMind/JAX FermiNet runtime parity oracle."""

import argparse
import json
import subprocess
from pathlib import Path

from jax import config

config.update("jax_enable_x64", True)

import jax
import jax.numpy as jnp
import numpy as np

from ferminet import networks


FERMINET_COMMIT = "c4312c315dda1c5728994ba89629744f71c6eb66"
NUCLEI = np.asarray(
    [
        [0.0, 0.0, 0.0],
        [1.7952398191849366, 0.0, 0.0],
        [-0.46464225035067114, 1.7340684963325879, 0.0],
    ],
    dtype=np.float64,
)
CHARGES = np.asarray([8.0, 1.0, 1.0], dtype=np.float64)
ELECTRONS = np.asarray(
    [
        [0.18, 0.11, 0.27],
        [-0.31, 0.42, -0.16],
        [0.57, -0.28, 0.33],
        [-0.63, -0.37, 0.21],
        [0.24, 0.71, -0.45],
        [-0.22, -0.15, -0.38],
        [0.36, -0.54, 0.19],
        [-0.48, 0.26, 0.51],
        [0.69, 0.18, -0.24],
        [-0.12, 0.61, 0.37],
    ],
    dtype=np.float64,
)
SPINS = np.asarray([1, 1, 1, 1, 1, -1, -1, -1, -1, -1], dtype=np.int32)
PROPOSAL = ELECTRONS.copy()
PROPOSAL[0] += np.asarray([0.025, -0.010, 0.015], dtype=np.float64)


def require_reference_checkout() -> None:
    commit = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    if commit != FERMINET_COMMIT:
        raise RuntimeError(
            f"official FermiNet checkout must be pinned to {FERMINET_COMMIT}; got {commit}"
        )


def java_parameter_export(params):
    """Export every official leaf and map it into Java layout order."""
    values = []
    blocks = []

    def append(path, java_name, source, transpose):
        array = np.asarray(source, dtype=np.float64)
        mapped = array.T if transpose else array
        flattened = mapped.reshape(-1)
        values.extend(flattened)
        blocks.append(
            {
                "path": path,
                "shape": list(array.shape),
                "flattened_values": array.reshape(-1).tolist(),
                "java_block": java_name,
                "java_shape": list(mapped.shape),
                "java_flattened_values": flattened.tolist(),
            }
        )

    streams = params["layers"]["streams"]
    for index, layer in enumerate(streams):
        append(
            f"layers.streams[{index}].single.w",
            f"interaction.{index}.one.weight",
            layer["single"]["w"],
            True,
        )
        append(
            f"layers.streams[{index}].single.b",
            f"interaction.{index}.one.bias",
            layer["single"]["b"],
            False,
        )
        if "double" in layer:
            append(
                f"layers.streams[{index}].double.w",
                f"interaction.{index}.two.weight",
                layer["double"]["w"],
                True,
            )
            append(
                f"layers.streams[{index}].double.b",
                f"interaction.{index}.two.bias",
                layer["double"]["b"],
                False,
            )
    for spin in range(2):
        label = "alpha" if spin == 0 else "beta"
        append(
            f"orbital[{spin}].w",
            f"orbital.{label}.weight",
            params["orbital"][spin]["w"],
            True,
        )
        pi = np.asarray(params["envelope"][spin]["pi"])
        sigma = np.asarray(params["envelope"][spin]["sigma"])
        if pi.shape != (3, 20) or sigma.shape != (3, 20):
            raise RuntimeError(
                f"unexpected isotropic envelope shapes: pi={pi.shape}, sigma={sigma.shape}"
            )
        append(
            f"envelope[{spin}].pi",
            f"envelope.{label}.pi",
            pi,
            True,
        )
        append(
            f"envelope[{spin}].sigma",
            f"envelope.{label}.sigma",
            sigma,
            True,
        )
    return np.asarray(values, dtype=np.float64), blocks


def coulomb_components(electrons: np.ndarray):
    electron_nuclear = 0.0
    for electron in electrons:
        for nucleus, charge in zip(NUCLEI, CHARGES):
            electron_nuclear -= charge / np.linalg.norm(electron - nucleus)
    electron_electron = 0.0
    for left in range(len(electrons)):
        for right in range(left + 1, len(electrons)):
            electron_electron += 1.0 / np.linalg.norm(
                electrons[left] - electrons[right]
            )
    nuclear_nuclear = 0.0
    for left in range(len(NUCLEI)):
        for right in range(left + 1, len(NUCLEI)):
            nuclear_nuclear += (
                CHARGES[left]
                * CHARGES[right]
                / np.linalg.norm(NUCLEI[left] - NUCLEI[right])
            )
    return electron_nuclear, electron_electron, nuclear_nuclear


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    require_reference_checkout()

    atoms = jnp.asarray(NUCLEI)
    charges = jnp.asarray(CHARGES)
    spins = jnp.asarray(SPINS)
    network = networks.make_fermi_net(
        (5, 5),
        charges,
        ndim=3,
        determinants=2,
        hidden_dims=((8, 4), (8, 4)),
        full_det=True,
        bias_orbitals=False,
        use_last_layer=False,
        separate_spin_channels=False,
        jastrow="NONE",
    )
    params = network.init(jax.random.PRNGKey(0))
    parameter_vector, parameter_blocks = java_parameter_export(params)

    def signed_log(flat_coordinates):
        return network.apply(params, flat_coordinates, spins, atoms, charges)

    def log_abs(flat_coordinates):
        return signed_log(flat_coordinates)[1]

    coordinates = jnp.asarray(ELECTRONS.reshape(-1))
    proposal_coordinates = jnp.asarray(PROPOSAL.reshape(-1))
    sign, log_value = signed_log(coordinates)
    proposal_sign, proposal_log_value = signed_log(proposal_coordinates)
    del proposal_sign
    gradient = jax.grad(log_abs)(coordinates)
    hessian = jax.hessian(log_abs)(coordinates)
    laplacian_over_wavefunction = jnp.trace(hessian) + jnp.vdot(gradient, gradient)
    kinetic = -0.5 * laplacian_over_wavefunction
    electron_nuclear, electron_electron, nuclear_nuclear = coulomb_components(
        ELECTRONS
    )
    total = (
        float(np.asarray(kinetic))
        + electron_nuclear
        + electron_electron
        + nuclear_nuclear
    )

    fixture = {
        "ferminet_commit": FERMINET_COMMIT,
        "architecture": {
            "identity": "ferminet-v1-reduced-full-det-x64",
            "spatial_dimensions": 3,
            "interaction_layers": 2,
            "one_electron_width": 8,
            "two_electron_width": 4,
            "determinants": 2,
            "full_det": True,
            "bias_orbitals": False,
            "use_last_layer": False,
            "separate_spin_channels": False,
            "isotropic_envelope": True,
            "jastrow": "NONE",
            "parameter_seed": 0,
        },
        "parameter_count": int(parameter_vector.size),
        "parameters": parameter_vector.tolist(),
        "parameter_blocks": parameter_blocks,
        "nuclei": [
            {
                "ordered_index": index,
                "element": ["O", "H", "H"][index],
                "nuclear_charge": int(CHARGES[index]),
                "coordinates_bohr": xyz.tolist(),
            }
            for index, xyz in enumerate(NUCLEI)
        ],
        "spins": SPINS.tolist(),
        "electron_coordinates": ELECTRONS.tolist(),
        "sign": int(np.asarray(sign)),
        "log_abs_psi": float(np.asarray(log_value)),
        "coordinate_gradient_log_abs_psi": np.asarray(gradient).tolist(),
        "laplacian_over_wavefunction": float(
            np.asarray(laplacian_over_wavefunction)
        ),
        "kinetic_hartree": float(np.asarray(kinetic)),
        "electron_nuclear_hartree": electron_nuclear,
        "electron_electron_hartree": electron_electron,
        "nuclear_nuclear_hartree": nuclear_nuclear,
        "total_local_energy_hartree": total,
        "proposal_coordinates": PROPOSAL.tolist(),
        "proposal_log_abs_psi": float(np.asarray(proposal_log_value)),
        "metropolis_log_ratio": float(
            2.0 * np.asarray(proposal_log_value - log_value)
        ),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(fixture, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
