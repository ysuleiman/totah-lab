package totah.lab.athena.surface.differential;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic SurfDiff-compatible residue neighborhoods and distances. */
public final class LocalResidueNeighborhood {
    private static final List<String> DISTANCE_EXCLUDED = List.of("N", "C", "O");

    private LocalResidueNeighborhood() {
    }

    public static Map<ResidueId, Map<ResidueId, Double>> build(
            List<SurfaceResidue> residues,
            double neighborhoodRadius) {
        Objects.requireNonNull(residues, "residues");
        LinkedHashMap<ResidueId, Map<ResidueId, Double>> result =
                new LinkedHashMap<>();
        for (SurfaceResidue central : residues) {
            LinkedHashMap<ResidueId, Double> distances = new LinkedHashMap<>();
            distances.put(central.id(), 0.0);
            for (SurfaceResidue candidate : sorted(residues)) {
                if (candidate.id().equals(central.id())) {
                    continue;
                }
                if (alphaCarbonDistance(central.residue(), candidate.residue())
                        <= neighborhoodRadius) {
                    distances.put(candidate.id(), minimumIncludedAtomDistance(
                            central.residue(), candidate.residue()));
                }
            }
            result.put(central.id(), Map.copyOf(distances));
        }
        return Map.copyOf(result);
    }

    public static double minimumIncludedAtomDistance(Residue first, Residue second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        double minimum = Double.POSITIVE_INFINITY;
        for (Atom firstAtom : includedAtoms(first)) {
            for (Atom secondAtom : includedAtoms(second)) {
                minimum = Math.min(minimum,
                        firstAtom.getPosition().distance(secondAtom.getPosition()));
            }
        }
        return minimum;
    }

    private static double alphaCarbonDistance(Residue first, Residue second) {
        return first.getAlphaCarbonPosition()
                .flatMap(firstPosition -> second.getAlphaCarbonPosition()
                        .map(firstPosition::distance))
                .orElse(Double.POSITIVE_INFINITY);
    }

    private static List<Atom> includedAtoms(Residue residue) {
        return residue.getAtoms().stream()
                .filter(Objects::nonNull)
                .filter(atom -> !DISTANCE_EXCLUDED.contains(atom.getName()))
                .toList();
    }

    private static List<SurfaceResidue> sorted(List<SurfaceResidue> residues) {
        ArrayList<SurfaceResidue> copy = new ArrayList<>(residues);
        copy.sort(Comparator.comparing((SurfaceResidue r) -> r.id().chainId())
                .thenComparingInt(r -> r.id().residueNumber())
                .thenComparing(r -> r.id().insertionCode(),
                        Comparator.nullsFirst(Comparator.naturalOrder())));
        return copy;
    }
}
