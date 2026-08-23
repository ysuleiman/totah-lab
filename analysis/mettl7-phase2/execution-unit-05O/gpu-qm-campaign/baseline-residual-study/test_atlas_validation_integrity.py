"""Adversarial tests for fold-scoped atlas label isolation.

These tests use synthetic non-symmetric data.  They never open the sealed
GPU-60 validation labels and do not execute QM.
"""

from __future__ import annotations

import copy
import unittest

import numpy as np

import run_conservative_local_qm_atlas as first
import run_secant_hessian_manifold_atlas as second


class AtlasValidationIntegrityTest(unittest.TestCase):
    def setUp(self):
        rng = np.random.default_rng(7319)
        self.count = 6
        self.dimension = second.TANGENT_DIMENSION
        self.z = rng.normal(size=(self.count, self.dimension))
        self.jacobians = rng.normal(size=(self.count, self.dimension, 168))
        self.energies = rng.normal(size=self.count) * 13.0
        self.forces = rng.normal(size=(self.count, 56, 3))
        self.covectors = rng.normal(size=(self.count, self.dimension))
        self.identifiers = np.array([f"SYNTH-{i}" for i in range(self.count)])
        self.minima = np.array(["MIN01", "MIN01", "MIN02", "MIN02", "MIN04", "MIN04"])
        indices = np.arange(self.count)
        self.training = [indices[indices != query] for query in indices]

    def first_order_predictions(self, energies, covectors):
        records, _ = first.evaluate_scheme(
            "SYNTHETIC_LOO", self.training, self.z, self.jacobians,
            energies, self.forces, covectors, 1.7, self.identifiers,
            self.minima)
        return records

    def test_held_out_energy_and_gradient_cannot_change_own_prediction(self):
        baseline = self.first_order_predictions(self.energies, self.covectors)
        for query in range(self.count):
            changed_energy = self.energies.copy()
            changed_covectors = self.covectors.copy()
            changed_energy[query] = 1.0e200
            changed_covectors[query] = -1.0e150
            changed = self.first_order_predictions(changed_energy, changed_covectors)
            self.assertEqual(
                baseline[query]["predicted_energy_kcal_mol"],
                changed[query]["predicted_energy_kcal_mol"])
            np.testing.assert_array_equal(
                baseline[query]["predicted_force_kcal_mol_angstrom"],
                changed[query]["predicted_force_kcal_mol_angstrom"])

    def test_legitimate_training_label_changes_reconstruction(self):
        baseline = self.first_order_predictions(self.energies, self.covectors)
        changed = self.covectors.copy()
        changed[1] += np.array([7.0, -3.0, 2.0, 5.0, -11.0, 13.0])
        actual = self.first_order_predictions(self.energies, changed)
        self.assertFalse(np.array_equal(
            baseline[0]["predicted_force_kcal_mol_angstrom"],
            actual[0]["predicted_force_kcal_mol_angstrom"]))

    def test_fold_hessian_excludes_held_out_gradient_secants(self):
        graph_distances = np.linalg.norm(
            self.z[:, None, :] - self.z[None, :, :], axis=2)
        tangents = np.repeat(np.eye(self.dimension)[None, :, :], self.count, axis=0)
        training = np.array([1, 2, 3, 4, 5])
        baseline = second.fold_hessians(
            training, graph_distances, self.z, self.covectors, tangents)
        changed = self.covectors.copy()
        changed[0] = 1.0e150
        actual = second.fold_hessians(
            training, graph_distances, self.z, changed, tangents)
        np.testing.assert_array_equal(baseline, actual)

    def test_lomo_hessian_excludes_entire_minimum(self):
        graph_distances = np.linalg.norm(
            self.z[:, None, :] - self.z[None, :, :], axis=2)
        tangents = np.repeat(np.eye(self.dimension)[None, :, :], self.count, axis=0)
        training = np.where(self.minima != "MIN01")[0]
        baseline = second.fold_hessians(
            training, graph_distances, self.z, self.covectors, tangents)
        changed = copy.deepcopy(self.covectors)
        changed[self.minima == "MIN01"] = -1.0e150
        actual = second.fold_hessians(
            training, graph_distances, self.z, changed, tangents)
        np.testing.assert_array_equal(baseline, actual)


if __name__ == "__main__":
    unittest.main()
