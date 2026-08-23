import unittest

import numpy as np


class CurvatureConventionTests(unittest.TestCase):
    def test_pyscf_axis_permutation_recovers_atom_major_matrix(self):
        canonical = np.arange(36.0).reshape(6, 6)
        canonical = canonical + canonical.T
        pyscf_axes = canonical.reshape(2, 3, 2, 3).transpose(0, 2, 1, 3)
        recovered = pyscf_axes.transpose(0, 2, 1, 3).reshape(6, 6)
        np.testing.assert_array_equal(recovered, canonical)
        self.assertFalse(np.array_equal(pyscf_axes.reshape(6, 6), canonical))

    def test_mass_weighted_quadratic_energy_gradient_and_hessian_agree(self):
        masses = np.array([1.0, 4.0])
        vector = np.array([1.0, 0.5])
        vector /= np.sqrt(np.sum(masses * vector**2))
        curvature = 0.75
        hessian = curvature * np.outer(masses * vector, masses * vector)
        q = 0.03
        plus, minus = q * vector, -q * vector
        energy = lambda x: 0.5 * x @ hessian @ x
        gradient = lambda x: hessian @ x
        energy_curvature = (energy(plus) + energy(minus) - 2 * energy(np.zeros(2))) / q**2
        gradient_curvature = ((gradient(plus) - gradient(minus)) @ vector) / (2 * q)
        hessian_curvature = vector @ hessian @ vector
        self.assertAlmostEqual(energy_curvature, hessian_curvature, places=14)
        self.assertAlmostEqual(gradient_curvature, hessian_curvature, places=14)

    def test_linear_term_controls_one_sided_lowering_not_central_curvature(self):
        q, slope, curvature = 0.05, 0.2, 0.7
        energy = lambda x: slope * x + 0.5 * curvature * x * x
        self.assertLess(energy(-q), energy(0.0))
        self.assertGreater(energy(q), energy(0.0))
        self.assertAlmostEqual((energy(q) + energy(-q) - 2 * energy(0.0)) / q**2,
                               curvature, places=14)

    def test_bohr_angstrom_curvature_conversion_is_quadratic(self):
        bohr_angstrom = 0.529177210903
        curvature_per_bohr2 = 0.25
        curvature_per_angstrom2 = curvature_per_bohr2 / bohr_angstrom**2
        q_angstrom = 0.02
        q_bohr = q_angstrom / bohr_angstrom
        self.assertAlmostEqual(curvature_per_bohr2 * q_bohr**2,
                               curvature_per_angstrom2 * q_angstrom**2, places=14)


if __name__ == "__main__":
    unittest.main()
