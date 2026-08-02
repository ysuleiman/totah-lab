package totah.lab.math;

import totah.lab.math.charges.ChargeSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal in-memory ChargeSystem fixtures for charge-model unit tests.
 * Decoupled from protein model classes, mirroring how the models consume it.
 */
public final class TestChargeSystems {

    private TestChargeSystems() {
    }

    /**
     * Water-like geometry: O at the origin, two symmetry-equivalent H atoms
     * (~0.957 A), bonds O-H1 and O-H2.
     */
    public static ChargeSystem water() {
        return of(
                new String[]{"O", "H", "H"},
                new double[][]{
                        {0.000, 0.000, 0.0},
                        {0.757, 0.586, 0.0},
                        {-0.757, 0.586, 0.0}},
                new int[][]{{0, 1}, {0, 2}});
    }

    public static ChargeSystem of(String[] elements, double[][] coords, int[][] bonds) {
        List<List<Integer>> neighbors = new ArrayList<>();
        for (int i = 0; i < elements.length; i++) {
            neighbors.add(new ArrayList<>());
        }
        for (int[] bond : bonds) {
            neighbors.get(bond[0]).add(bond[1]);
            neighbors.get(bond[1]).add(bond[0]);
        }

        return new ChargeSystem() {
            @Override
            public int size() {
                return elements.length;
            }

            @Override
            public double getX(int i) {
                return coords[i][0];
            }

            @Override
            public double getY(int i) {
                return coords[i][1];
            }

            @Override
            public double getZ(int i) {
                return coords[i][2];
            }

            @Override
            public String getElement(int i) {
                return elements[i];
            }

            @Override
            public List<Integer> getNeighbors(int i) {
                return neighbors.get(i);
            }
        };
    }
}
