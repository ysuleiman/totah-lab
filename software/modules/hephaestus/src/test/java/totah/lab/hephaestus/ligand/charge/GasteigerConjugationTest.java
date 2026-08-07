package totah.lab.hephaestus.ligand.charge;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conjugated lone-pair heteroatoms take the sp2 Gasteiger parameters
 * (RDKit hybridization semantics): an O or N adjacent to an sp2 center
 * polarizes much more strongly than an sp3 one.
 */
class GasteigerConjugationTest {

    private final GasteigerModel model = new GasteigerModel();

    @Test
    void anisoleOxygenIsSp2AndStronglyPolarized() {
        // methanol: sp3 oxygen
        double[] plain = model.computeCharges(
                system(new String[]{"C", "O", "C"},
                        bonds(entry(0, 1, 1.0), entry(1, 2, 1.0)),
                        new int[]{}), 0.0);
        // anisole fragment: same bonds, but the second carbon is aromatic
        double[] conjugated = model.computeCharges(
                system(new String[]{"C", "O", "C"},
                        bonds(entry(0, 1, 1.0), entry(1, 2, 1.0)),
                        new int[]{2}), 0.0);

        assertTrue(conjugated[1] < plain[1] - 0.1,
                "conjugated O should be much more negative: "
                        + conjugated[1] + " vs " + plain[1]);
        assertTrue(conjugated[0] > plain[0] + 0.05,
                "methyl C on a conjugated O should be more positive: "
                        + conjugated[0] + " vs " + plain[0]);
    }

    @Test
    void amideNitrogenIsSp2() {
        // amine N on a carbonyl carbon (C with a double bond) vs plain amine
        double[] amine = model.computeCharges(
                system(new String[]{"N", "C"},
                        bonds(entry(0, 1, 1.0)), new int[]{}), 0.0);
        double[] amide = model.computeCharges(
                system(new String[]{"N", "C", "O"},
                        bonds(entry(0, 1, 1.0), entry(1, 2, 2.0)),
                        new int[]{}), 0.0);

        assertTrue(amine[0] < amide[0],
                "amide N (sp2) is less negative than amine N (sp3): "
                        + amide[0] + " vs " + amine[0]);
    }

    @SafeVarargs
    private static Map<String, double[]> bonds(
            Map.Entry<String, double[]>... entries) {
        Map<String, double[]> map = new HashMap<>();
        for (Map.Entry<String, double[]> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    private static Map.Entry<String, double[]> entry(
            int first, int second, double order) {
        return Map.entry(first + "-" + second, new double[]{first, second, order});
    }

    private static ChargeSystem system(
            String[] elements,
            Map<String, double[]> bonds,
            int[] aromaticAtoms
    ) {
        Map<String, Double> bondOrders = new HashMap<>();
        List<List<Integer>> neighbors = new ArrayList<>();
        for (int index = 0; index < elements.length; index++) {
            neighbors.add(new ArrayList<>());
        }
        for (var entry : bonds.entrySet()) {
            int a = (int) entry.getValue()[0];
            int b = (int) entry.getValue()[1];
            bondOrders.put(a + "-" + b, entry.getValue()[2]);
            bondOrders.put(b + "-" + a, entry.getValue()[2]);
            neighbors.get(a).add(b);
            neighbors.get(b).add(a);
        }
        return new ChargeSystem() {
            @Override
            public int size() {
                return elements.length;
            }

            @Override
            public double getX(int i) {
                return i;
            }

            @Override
            public double getY(int i) {
                return 0;
            }

            @Override
            public double getZ(int i) {
                return 0;
            }

            @Override
            public String getElement(int i) {
                return elements[i];
            }

            @Override
            public List<Integer> getNeighbors(int i) {
                return neighbors.get(i);
            }

            @Override
            public double getBondOrder(int a, int b) {
                return bondOrders.getOrDefault(a + "-" + b, 0.0);
            }

            @Override
            public boolean isAromatic(int i) {
                for (int atom : aromaticAtoms) {
                    if (atom == i) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
}
