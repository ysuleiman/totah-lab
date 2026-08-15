package totah.lab.prometheus.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import totah.lab.prometheus.identity.CanonicalHashing;

/**
 * Steric clash check with Bondi van der Waals radii (H 1.20, C 1.70, N 1.55,
 * O 1.52, S 1.80 Å; unknown elements fall back to 1.70 Å and are still
 * evaluated).
 *
 * <p>A pair clashes when its distance is below
 * {@code scaleFactor * (r1 + r2)} — e.g. scale factor 0.75, the TSL
 * probe-geometry audit for scaffold-collision probes. Covalently bound pairs
 * (given as label pairs) are excluded: a bonded S–H at 1.34 Å is not a clash.
 */
public final class BondiClashChecker implements GeometryClashChecker {

    private static final Map<String, Double> BONDI_RADII = Map.of(
            "H", 1.20,
            "C", 1.70,
            "N", 1.55,
            "O", 1.52,
            "S", 1.80);

    private static final double FALLBACK_RADIUS = 1.70;

    private final double scaleFactor;
    private final Set<Set<String>> bondedPairs;

    public BondiClashChecker(double scaleFactor, Set<Set<String>> bondedPairs) {
        if (scaleFactor <= 0.0) {
            throw new IllegalArgumentException("scaleFactor must be > 0, got " + scaleFactor);
        }
        this.scaleFactor = scaleFactor;
        Objects.requireNonNull(bondedPairs, "bondedPairs");
        Set<Set<String>> copy = new HashSet<>();
        for (Set<String> pair : bondedPairs) {
            Objects.requireNonNull(pair, "bonded pair");
            if (pair.size() != 2) {
                throw new IllegalArgumentException(
                        "a bonded pair must contain exactly two distinct labels: " + pair);
            }
            copy.add(Set.copyOf(pair));
        }
        this.bondedPairs = Set.copyOf(copy);
    }

    @Override
    public List<String> clashes(List<ClashAtom> atoms) {
        Objects.requireNonNull(atoms, "atoms");
        List<String> clashes = new ArrayList<>();
        for (int i = 0; i < atoms.size(); i++) {
            for (int j = i + 1; j < atoms.size(); j++) {
                ClashAtom a = atoms.get(i);
                ClashAtom b = atoms.get(j);
                if (isBonded(a.label(), b.label())) {
                    continue;
                }
                double limit = scaleFactor * (radiusOf(a.elementSymbol()) + radiusOf(b.elementSymbol()));
                double distance = distance(a, b);
                if (distance < limit) {
                    clashes.add(a.label() + "-" + b.label()
                            + ": distance " + CanonicalHashing.format(distance)
                            + " Å < " + CanonicalHashing.format(limit)
                            + " Å (scaled Bondi vdW sum)");
                }
            }
        }
        return List.copyOf(clashes);
    }

    private boolean isBonded(String labelA, String labelB) {
        return bondedPairs.contains(Set.of(labelA, labelB));
    }

    private static double radiusOf(String elementSymbol) {
        return BONDI_RADII.getOrDefault(elementSymbol, FALLBACK_RADIUS);
    }

    private static double distance(ClashAtom a, ClashAtom b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
