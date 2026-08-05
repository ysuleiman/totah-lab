package totah.lab.athena.pocket.pocketmatch;

import totah.lab.athena.pocket.compare.residue.ResidueCentroidCalculator;
import totah.lab.athena.pocket.compare.residue.ResidueReference;
import totah.lab.athena.pocket.selection.PocketResidueSelection;
import totah.lab.athena.pocket.selection
        .PocketResidueSelection.ResolvedPocketResidue;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Athena-owned {@link PocketMatchSignatureFactory}.
 *
 * <p>Each resolvable, classifiable pocket residue contributes three
 * representative points (CA, CB, side-chain centroid) built from actual
 * residue atom coordinates — never from alpha-sphere geometry:</p>
 *
 * <ul>
 *     <li>CA: the alpha carbon. A residue without a CA atom contributes
 *     no points at all and is counted as skipped.</li>
 *     <li>CB: the beta carbon; glycine and residues with an absent CB
 *     atom fall back to the CA position.</li>
 *     <li>Side-chain centroid: centroid of the side-chain heavy atoms,
 *     computed by the shared {@link ResidueCentroidCalculator} rules
 *     (glycine uses CA; missing side-chain heavy atoms fall back to
 *     CA).</li>
 * </ul>
 *
 * <p>Distances are computed for every unordered pair of distinct point
 * instances ({@code i < j}), so a point is never paired with itself and
 * every physical point pair is counted exactly once. Each distance is
 * filed into the canonical category of its chemistry-group pair and
 * point-type pair, and every category list is sorted ascending.</p>
 *
 * <p>Residues that cannot be resolved against the structure, carry an
 * unknown residue name, or lack a usable CA coordinate are skipped and
 * reported through
 * {@link PocketMatchSignature#diagnostics()}.</p>
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the package documentation for the full citation and provenance.</p>
 */
public final class DefaultPocketMatchSignatureFactory
        implements PocketMatchSignatureFactory {

    private final PocketResidueSelection residueSelection;
    private final ResidueCentroidCalculator centroidCalculator;

    public DefaultPocketMatchSignatureFactory() {
        this(new PocketResidueSelection(), new ResidueCentroidCalculator());
    }

    public DefaultPocketMatchSignatureFactory(
            PocketResidueSelection residueSelection,
            ResidueCentroidCalculator centroidCalculator
    ) {
        this.residueSelection = Objects.requireNonNull(
                residueSelection,
                "residueSelection"
        );
        this.centroidCalculator = Objects.requireNonNull(
                centroidCalculator,
                "centroidCalculator"
        );
    }

    @Override
    public PocketMatchSignature describe(
            Structure structure,
            Pocket pocket
    ) {
        PocketMatchPointExtraction extraction = extract(structure, pocket);

        return new PocketMatchSignature(
                buildDistanceLists(extraction.points()),
                countDistances(extraction.points().size()),
                new PocketMatchSignatureDiagnostics(
                        extraction.inputResidueCount(),
                        extraction.representedResidueCount(),
                        extraction.points().size(),
                        extraction.skippedResidueCount(),
                        countDistances(extraction.points().size())
                )
        );
    }

    /**
     * Extracts representative points with per-residue accounting.
     * Exposed for diagnostics and tests; signature construction goes
     * through {@link #describe(Structure, Pocket)}.
     */
    public PocketMatchPointExtraction extract(
            Structure structure,
            Pocket pocket
    ) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(pocket, "pocket");

        List<PocketMatchPoint> points = new ArrayList<>();
        int representedResidues = 0;

        for (ResolvedPocketResidue resolved :
                residueSelection.resolvedPocketResidues(structure, pocket)) {

            Optional<List<PocketMatchPoint>> residuePoints =
                    representativePoints(resolved);

            if (residuePoints.isEmpty()) {
                continue;
            }

            points.addAll(residuePoints.get());
            representedResidues++;
        }

        int inputResidues = pocket.residues().size();

        return new PocketMatchPointExtraction(
                List.copyOf(points),
                inputResidues,
                representedResidues,
                inputResidues - representedResidues
        );
    }

    /**
     * Returns the three representative points of one resolved residue,
     * or {@link Optional#empty()} when the residue cannot be classified
     * or has no usable alpha carbon.
     */
    private Optional<List<PocketMatchPoint>> representativePoints(
            ResolvedPocketResidue resolved
    ) {
        Residue residue = resolved.residue();

        Optional<PocketMatchResidueGroup> group =
                PocketMatchResidueGroup.classify(residue.getName());

        if (group.isEmpty()) {
            return Optional.empty();
        }

        Optional<Atom> alphaCarbon = residue.findAtom("CA");

        if (alphaCarbon.isEmpty()
                || alphaCarbon.get().getPosition() == null) {
            return Optional.empty();
        }

        Point3D caPosition = alphaCarbon.get().getPosition();

        Point3D cbPosition = residue.findAtom("CB")
                .map(Atom::getPosition)
                .orElse(caPosition);

        Point3D centroidPosition =
                centroidCalculator.calculate(residue);

        ResidueReference reference = new ResidueReference(
                resolved.id().chainId(),
                resolved.id().residueNumber(),
                normalizeInsertionCode(resolved.id().insertionCode()),
                residue.getName()
        );

        return Optional.of(List.of(
                new PocketMatchPoint(
                        reference,
                        group.get(),
                        PocketMatchPointType.CA,
                        caPosition
                ),
                new PocketMatchPoint(
                        reference,
                        group.get(),
                        PocketMatchPointType.CB,
                        cbPosition
                ),
                new PocketMatchPoint(
                        reference,
                        group.get(),
                        PocketMatchPointType.SIDE_CHAIN_CENTROID,
                        centroidPosition
                )
        ));
    }

    private static double[][] buildDistanceLists(
            List<PocketMatchPoint> points
    ) {
        @SuppressWarnings("unchecked")
        List<Double>[] buckets = new List[PocketMatchCategories.CATEGORY_COUNT];
        for (int index = 0; index < buckets.length; index++) {
            buckets[index] = new ArrayList<>();
        }

        for (int first = 0; first < points.size(); first++) {
            for (int second = first + 1; second < points.size(); second++) {
                PocketMatchPoint a = points.get(first);
                PocketMatchPoint b = points.get(second);

                int categoryIndex = PocketMatchCategories.categoryIndex(
                        a.residueGroup(),
                        b.residueGroup(),
                        a.pointType(),
                        b.pointType()
                );

                buckets[categoryIndex].add(
                        a.position().distance(b.position())
                );
            }
        }

        double[][] lists =
                new double[PocketMatchCategories.CATEGORY_COUNT][];
        for (int index = 0; index < buckets.length; index++) {
            double[] distances = buckets[index].stream()
                    .mapToDouble(Double::doubleValue)
                    .toArray();
            Arrays.sort(distances);
            lists[index] = distances;
        }
        return lists;
    }

    private static int countDistances(int pointCount) {
        return pointCount * (pointCount - 1) / 2;
    }

    private static char normalizeInsertionCode(Character insertionCode) {
        if (insertionCode == null
                || Character.isWhitespace(insertionCode)) {
            return ' ';
        }
        return insertionCode;
    }
}
