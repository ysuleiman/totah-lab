package totah.lab.athena.clash;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Detects steric clashes between heavy atoms of a structure.
 *
 * <p>A clash is reported when the distance between two heavy atoms is
 * smaller than the scaled sum of their van der Waals radii:</p>
 *
 * <pre>
 * distance &lt; (vanDerWaalsRadius_i + vanDerWaalsRadius_j) * radiusScale
 * </pre>
 *
 * <p>The default radius scale is {@link #DEFAULT_RADIUS_SCALE} (0.7), a
 * common threshold for severe (physically implausible) clashes; values
 * closer to 1.0 flag progressively milder contacts.</p>
 *
 * <p>The following atom pairs are always excluded:</p>
 *
 * <ul>
 *   <li>pairs within the same residue (same chain, residue number, and
 *       insertion code) — bonded or not, intra-residue contacts are
 *       governed by the residue's own geometry,</li>
 *   <li>pairs joined by an explicit {@link Bond} of the structure,</li>
 *   <li>pairs involving hydrogen atoms or atoms without a known element
 *       (atoms with a {@code null} element are skipped entirely).</li>
 * </ul>
 *
 * <p>Van der Waals radii are obtained from
 * {@link totah.lab.gaia.chemistry.Element#getVanDerWaalsRadiusOrDefault(double)};
 * elements without a tabulated radius use
 * {@link #DEFAULT_UNKNOWN_RADIUS}.</p>
 *
 * <p>The comparison is O(n^2) in the number of heavy atoms: athena depends
 * only on gaia and has no spatial-index structure available. This is
 * acceptable for the intended use (single mutant side chains and pocket
 * residue sets) but may be slow on very large structures.</p>
 *
 * <p>Results are deterministic: clashes are emitted in structure traversal
 * order of the atom pairs.</p>
 */
public final class StericClashAnalysis {

    /**
     * Default van der Waals radius scale. A pair closer than 0.7 times
     * the sum of the van der Waals radii is a common severe-clash
     * threshold in structural validation.
     */
    public static final double DEFAULT_RADIUS_SCALE = 0.7;

    /** Fallback van der Waals radius for an element without a known radius. */
    public static final double DEFAULT_UNKNOWN_RADIUS = 1.70;

    private StericClashAnalysis() {
    }

    /**
     * A steric clash between two heavy atoms.
     *
     * <p>{@code first} and {@code second} are stored in canonical order
     * ({@code first.compareTo(second) <= 0}) regardless of traversal
     * order. All distances are in angstroms.</p>
     *
     * <p>{@code overlapAmount} is the <em>unscaled</em> sum of the van der
     * Waals radii minus the distance. It is positive when the atoms
     * interpenetrate their full van der Waals spheres and negative when
     * the pair only violates the scaled threshold.</p>
     *
     * @param first first atom reference (canonical order)
     * @param second second atom reference (canonical order)
     * @param distance interatomic distance in angstroms
     * @param overlapAmount van der Waals radius sum minus distance, in
     *                      angstroms
     */
    public record Clash(
            AtomReference first,
            AtomReference second,
            double distance,
            double overlapAmount) {

        public Clash {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            if (first.equals(second)) {
                throw new IllegalArgumentException(
                        "A clash cannot involve the same atom twice: "
                                + first);
            }
            if (first.compareTo(second) > 0) {
                AtomReference swap = first;
                first = second;
                second = swap;
            }
            if (!Double.isFinite(distance) || distance < 0.0) {
                throw new IllegalArgumentException(
                        "distance must be finite and non-negative: "
                                + distance);
            }
            if (!Double.isFinite(overlapAmount)) {
                throw new IllegalArgumentException(
                        "overlapAmount must be finite: " + overlapAmount);
            }
        }
    }

    /**
     * Clash-detection options.
     *
     * @param radiusScale multiplier applied to the sum of van der Waals
     *                    radii; must be finite and positive. 0.7 flags
     *                    severe clashes, 1.0 flags any van der Waals
     *                    sphere interpenetration
     */
    public record Options(double radiusScale) {

        public Options {
            if (!Double.isFinite(radiusScale) || radiusScale <= 0.0) {
                throw new IllegalArgumentException(
                        "radiusScale must be finite and positive: "
                                + radiusScale);
            }
        }

        /**
         * Returns the default options ({@link #DEFAULT_RADIUS_SCALE}).
         */
        public static Options defaults() {
            return new Options(DEFAULT_RADIUS_SCALE);
        }
    }

    /**
     * Finds all clashes among the heavy atoms of a structure using the
     * default radius scale.
     *
     * @param structure structure to inspect
     * @return clashes in structure traversal order
     */
    public static List<Clash> findClashes(Structure structure) {
        return findClashes(structure, Options.defaults());
    }

    /**
     * Finds all clashes among the heavy atoms of a structure.
     *
     * @param structure structure to inspect
     * @param options clash-detection options
     * @return clashes in structure traversal order
     */
    public static List<Clash> findClashes(
            Structure structure,
            Options options) {

        return findClashes(structure, null, options);
    }

    /**
     * Finds clashes involving a subset of atoms, using the default radius
     * scale. This is the mutant-validation use case: the atoms of a new
     * side chain (or any residue set) are checked against the rest of the
     * structure.
     *
     * <p>A pair is reported when at least one of the two atoms belongs to
     * {@code scope}. The usual exclusions still apply, so scoping a single
     * residue checks exactly that residue against its environment.</p>
     *
     * @param structure structure to inspect
     * @param scope atom references of the subset to check; references that
     *              do not resolve to atoms of the structure never match
     * @return clashes in structure traversal order
     */
    public static List<Clash> findClashes(
            Structure structure,
            Collection<AtomReference> scope) {

        return findClashes(structure, scope, Options.defaults());
    }

    /**
     * Finds clashes involving a subset of atoms.
     *
     * @param structure structure to inspect
     * @param scope atom references of the subset to check
     * @param options clash-detection options
     * @return clashes in structure traversal order
     */
    public static List<Clash> findClashes(
            Structure structure,
            Collection<AtomReference> scope,
            Options options) {

        Objects.requireNonNull(scope, "scope");
        Set<AtomReference> scopeSet = new HashSet<>(scope.size());
        for (AtomReference reference : scope) {
            scopeSet.add(Objects.requireNonNull(
                    reference, "scope must not contain null references"));
        }
        return findClashes(structure, scopeSet, options);
    }

    private static List<Clash> findClashes(
            Structure structure,
            Set<AtomReference> scope,
            Options options) {

        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(options, "options");

        List<HeavyAtom> heavyAtoms = collectHeavyAtoms(structure);
        Map<AtomReference, Set<AtomReference>> bondedNeighbors =
                bondedNeighbors(structure);

        List<Clash> clashes = new ArrayList<>();

        for (int firstIndex = 0;
             firstIndex < heavyAtoms.size();
             firstIndex++) {

            HeavyAtom first = heavyAtoms.get(firstIndex);

            for (int secondIndex = firstIndex + 1;
                 secondIndex < heavyAtoms.size();
                 secondIndex++) {

                HeavyAtom second = heavyAtoms.get(secondIndex);

                if (scope != null
                        && !scope.contains(first.reference())
                        && !scope.contains(second.reference())) {
                    continue;
                }

                if (sameResidue(first.reference(), second.reference())) {
                    continue;
                }

                if (bondedNeighbors
                        .getOrDefault(first.reference(), Set.of())
                        .contains(second.reference())) {
                    continue;
                }

                double distance = first.position()
                        .distance(second.position());

                double radiusSum = first.radius() + second.radius();

                if (distance < radiusSum * options.radiusScale()) {
                    clashes.add(new Clash(
                            first.reference(),
                            second.reference(),
                            distance,
                            radiusSum - distance));
                }
            }
        }

        return List.copyOf(clashes);
    }

    private static List<HeavyAtom> collectHeavyAtoms(Structure structure) {
        List<HeavyAtom> heavyAtoms = new ArrayList<>();

        for (Chain chain : structure.getChains()) {
            Objects.requireNonNull(
                    chain, "Structure must not contain null chains");

            for (Residue residue : chain.residues()) {
                Objects.requireNonNull(
                        residue, "Chain must not contain null residues");

                char insertionCode = residue.getInsertionCode() == null
                        ? ' '
                        : residue.getInsertionCode();

                for (Atom atom : residue.getAtoms()) {
                    if (atom == null || !atom.isHeavyAtom()) {
                        continue;
                    }

                    AtomReference reference = new AtomReference(
                            chain.id(),
                            residue.getNumber(),
                            insertionCode,
                            atom.getName());

                    double radius = atom.getElement()
                            .getVanDerWaalsRadiusOrDefault(
                                    DEFAULT_UNKNOWN_RADIUS);

                    if (!Double.isFinite(radius) || radius <= 0.0) {
                        throw new IllegalArgumentException(
                                "Invalid van der Waals radius for "
                                        + reference + ": " + radius);
                    }

                    heavyAtoms.add(new HeavyAtom(
                            reference,
                            atom.getPosition(),
                            radius));
                }
            }
        }

        return heavyAtoms;
    }

    private static Map<AtomReference, Set<AtomReference>> bondedNeighbors(
            Structure structure) {

        Map<AtomReference, Set<AtomReference>> neighbors = new HashMap<>();

        for (Bond bond : structure.bonds()) {
            neighbors.computeIfAbsent(bond.atom1(), key -> new HashSet<>())
                    .add(bond.atom2());
            neighbors.computeIfAbsent(bond.atom2(), key -> new HashSet<>())
                    .add(bond.atom1());
        }

        return neighbors;
    }

    private static boolean sameResidue(
            AtomReference first,
            AtomReference second) {

        return first.chainId().equals(second.chainId())
                && first.residueNumber() == second.residueNumber()
                && first.insertionCode() == second.insertionCode();
    }

    private record HeavyAtom(
            AtomReference reference,
            Point3D position,
            double radius) {

        private HeavyAtom {
            Objects.requireNonNull(reference, "reference");
            Objects.requireNonNull(position, "position");
        }
    }
}
