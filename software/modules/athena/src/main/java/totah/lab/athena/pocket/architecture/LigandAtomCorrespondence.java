package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Verified heavy-atom correspondence between two poses of what should
 * be the same ligand. RMSD or rigid-fit values computed over an
 * unverified index correspondence are meaningless, so every consumer
 * must establish the mapping through this class first.
 *
 * <p>Verification ladder:</p>
 * <ol>
 *   <li>{@link Method#INDEX_ORDER}: equal heavy-atom counts AND the
 *       same (atom name, element) sequence in structure order —
 *       index correspondence is valid. The name comparison matters:
 *       for a homo-element ligand a permuted atom order has an
 *       identical element sequence, and certifying it would yield a
 *       silently wrong RMSD.</li>
 *   <li>{@link Method#NAME_ELEMENT}: otherwise, an explicit mapping by
 *       (atom name, element), valid only when that key is unique on
 *       both sides and covers every heavy atom.</li>
 *   <li>{@link Method#NONE}: no verified mapping; RMSD/rotation must
 *       be reported as unavailable, with the reason.</li>
 * </ol>
 */
public final class LigandAtomCorrespondence {

    private LigandAtomCorrespondence() {
    }

    public enum Method {
        INDEX_ORDER,
        NAME_ELEMENT,
        NONE
    }

    /**
     * The verified mapping. {@code bToAIndex[i]} is the A-side
     * heavy-atom index corresponding to B-side heavy-atom index
     * {@code i} (heavy atoms in structure order on both sides);
     * meaningless when {@code method} is {@link Method#NONE}.
     */
    public record Mapping(
            Method method,
            int[] bToAIndex,
            String reason
    ) {
        public Mapping {
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(reason, "reason");
            bToAIndex = bToAIndex == null
                    ? new int[0]
                    : bToAIndex.clone();
        }

        @Override
        public int[] bToAIndex() {
            return bToAIndex.clone();
        }

        int[] bToAIndexView() {
            return bToAIndex;
        }
    }

    public static Mapping map(Ligand poseA, Ligand poseB) {
        Objects.requireNonNull(poseA, "poseA");
        Objects.requireNonNull(poseB, "poseB");

        List<Atom> atomsA = heavyAtoms(poseA);
        List<Atom> atomsB = heavyAtoms(poseB);

        if (atomsA.size() != atomsB.size()) {
            return new Mapping(
                    Method.NONE,
                    null,
                    "heavy-atom counts differ: " + atomsA.size()
                            + " vs " + atomsB.size()
            );
        }

        if (sameAtomSequence(atomsA, atomsB)) {
            return new Mapping(
                    Method.INDEX_ORDER,
                    identity(atomsA.size()),
                    "identical heavy-atom (name, element) sequence in "
                            + "structure order"
            );
        }

        return mapByNameAndElement(atomsA, atomsB);
    }

    private static Mapping mapByNameAndElement(
            List<Atom> atomsA,
            List<Atom> atomsB
    ) {
        Map<String, Integer> aIndexByKey = new HashMap<>();

        for (int index = 0; index < atomsA.size(); index++) {
            String key = key(atomsA.get(index));

            if (aIndexByKey.putIfAbsent(key, index) != null) {
                return new Mapping(
                        Method.NONE,
                        null,
                        "duplicate (name, element) key on the A side: "
                                + key
                );
            }
        }

        int[] bToA = new int[atomsB.size()];

        for (int index = 0; index < atomsB.size(); index++) {
            String key = key(atomsB.get(index));
            Integer aIndex = aIndexByKey.remove(key);

            if (aIndex == null) {
                return new Mapping(
                        Method.NONE,
                        null,
                        "no unique A-side atom for B-side key: " + key
                );
            }

            bToA[index] = aIndex;
        }

        if (!aIndexByKey.isEmpty()) {
            return new Mapping(
                    Method.NONE,
                    null,
                    "A-side atoms without a B-side counterpart: "
                            + aIndexByKey.keySet()
            );
        }

        return new Mapping(
                Method.NAME_ELEMENT,
                bToA,
                "explicit (name, element) mapping"
        );
    }

    private static boolean sameAtomSequence(
            List<Atom> atomsA,
            List<Atom> atomsB
    ) {
        for (int index = 0; index < atomsA.size(); index++) {
            Atom atomA = atomsA.get(index);
            Atom atomB = atomsB.get(index);

            if (atomA.getElement() != atomB.getElement()
                    || !atomA.getName().equals(atomB.getName())) {
                return false;
            }
        }

        return true;
    }

    private static String key(Atom atom) {
        return atom.getName() + "|" + atom.getElement();
    }

    private static int[] identity(int size) {
        int[] identity = new int[size];

        for (int index = 0; index < size; index++) {
            identity[index] = index;
        }

        return identity;
    }

    private static List<Atom> heavyAtoms(Ligand ligand) {
        return ligand.structure().getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .filter(Atom::isHeavyAtom)
                .toList();
    }
}
