package totah.lab.athena.pocket.pocketmatch;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DefaultPocketMatchSignatureFactoryTest {

    private final DefaultPocketMatchSignatureFactory factory =
            new DefaultPocketMatchSignatureFactory();

    @Test
    void glycineFallsBackToAlphaCarbonForBetaAndCentroid() {
        Residue glycine = PocketMatchTestFixtures
                .residueWithoutBetaCarbon("GLY", 10, 0.0, 0.0, 0.0);
        Structure structure = PocketMatchTestFixtures.structureOf(glycine);
        Pocket pocket = PocketMatchTestFixtures
                .pocketOfResidueNumbers(structure, 10);

        List<PocketMatchPoint> points =
                factory.extract(structure, pocket).points();

        assertThat(points).hasSize(3);

        Point3D ca = positionOf(points, PocketMatchPointType.CA);
        assertThat(ca).isEqualTo(new Point3D(0.0, 0.0, 0.0));
        assertThat(positionOf(points, PocketMatchPointType.CB))
                .isEqualTo(ca);
        assertThat(positionOf(
                points,
                PocketMatchPointType.SIDE_CHAIN_CENTROID
        )).isEqualTo(ca);
    }

    @Test
    void missingBetaCarbonFallsBackToAlphaCarbon() {
        Residue alanineWithoutCb = PocketMatchTestFixtures
                .residueWithoutBetaCarbon("ALA", 11, 1.0, 2.0, 3.0);
        Structure structure =
                PocketMatchTestFixtures.structureOf(alanineWithoutCb);
        Pocket pocket = PocketMatchTestFixtures
                .pocketOfResidueNumbers(structure, 11);

        List<PocketMatchPoint> points =
                factory.extract(structure, pocket).points();

        assertThat(positionOf(points, PocketMatchPointType.CB))
                .isEqualTo(positionOf(points, PocketMatchPointType.CA));
    }

    @Test
    void sideChainCentroidAveragesSideChainHeavyAtomsOnly() {
        Residue serine = PocketMatchTestFixtures.residue(
                "SER",
                12,
                10.0,
                10.0,
                10.0,
                List.of(
                        PocketMatchTestFixtures.atom(
                                "CB", Element.C, 11.0, 10.0, 10.0),
                        PocketMatchTestFixtures.atom(
                                "OG", Element.O, 13.0, 10.0, 10.0),
                        // hydrogen must be excluded from the centroid
                        PocketMatchTestFixtures.atom(
                                "HG", Element.H, 19.0, 10.0, 10.0)
                )
        );
        Structure structure = PocketMatchTestFixtures.structureOf(serine);
        Pocket pocket = PocketMatchTestFixtures
                .pocketOfResidueNumbers(structure, 12);

        List<PocketMatchPoint> points =
                factory.extract(structure, pocket).points();

        // mean of CB (11,10,10) and OG (13,10,10); backbone excluded
        assertThat(positionOf(
                points,
                PocketMatchPointType.SIDE_CHAIN_CENTROID
        )).isEqualTo(new Point3D(12.0, 10.0, 10.0));
    }

    @Test
    void residueWithoutSideChainHeavyAtomsFallsBackToAlphaCarbon() {
        Residue alanineWithoutCb = PocketMatchTestFixtures
                .residueWithoutBetaCarbon("ALA", 13, 5.0, 5.0, 5.0);
        Structure structure =
                PocketMatchTestFixtures.structureOf(alanineWithoutCb);
        Pocket pocket = PocketMatchTestFixtures
                .pocketOfResidueNumbers(structure, 13);

        List<PocketMatchPoint> points =
                factory.extract(structure, pocket).points();

        assertThat(positionOf(
                points,
                PocketMatchPointType.SIDE_CHAIN_CENTROID
        )).isEqualTo(positionOf(points, PocketMatchPointType.CA));
    }

    @Test
    void distanceListsAreSortedAscending() {
        Structure structure = PocketMatchTestFixtures.structureOf(
                PocketMatchTestFixtures.alanine(1, 0.0, 0.0, 0.0),
                PocketMatchTestFixtures.alanine(2, 4.0, 3.0, 1.0),
                PocketMatchTestFixtures.residueWithoutBetaCarbon(
                        "LYS", 3, 8.0, 1.0, 2.0),
                PocketMatchTestFixtures.residueWithoutBetaCarbon(
                        "TYR", 4, 2.0, 7.0, 5.0)
        );
        Pocket pocket = PocketMatchTestFixtures
                .pocketOfResidueNumbers(structure, 1, 2, 3, 4);

        PocketMatchSignature signature =
                factory.describe(structure, pocket);

        for (double[] distances : signature.sortedDistances()) {
            for (int index = 1; index < distances.length; index++) {
                assertThat(distances[index])
                        .isGreaterThanOrEqualTo(distances[index - 1]);
            }
        }
    }

    @Test
    void selfPointPairsAreExcludedAndPairsAreCountedOnce() {
        Structure structure = PocketMatchTestFixtures.structureOf(
                PocketMatchTestFixtures.alanine(1, 0.0, 0.0, 0.0));
        Pocket pocket = PocketMatchTestFixtures
                .pocketOfResidueNumbers(structure, 1);

        PocketMatchSignature signature =
                factory.describe(structure, pocket);

        // three points of one residue give C(3,2) = 3 pairs exactly
        assertThat(signature.totalDistanceCount()).isEqualTo(3);

        PocketMatchCategory caCa = new PocketMatchCategory(
                PocketMatchResidueGroup.ALIPHATIC_SPECIAL,
                PocketMatchResidueGroup.ALIPHATIC_SPECIAL,
                PocketMatchPointType.CA,
                PocketMatchPointType.CA
        );
        assertThat(signature.distances(caCa)).isEmpty();

        PocketMatchCategory caCb = new PocketMatchCategory(
                PocketMatchResidueGroup.ALIPHATIC_SPECIAL,
                PocketMatchResidueGroup.ALIPHATIC_SPECIAL,
                PocketMatchPointType.CA,
                PocketMatchPointType.CB
        );
        assertThat(signature.distances(caCb)).hasSize(1);
    }

    @Test
    void everyAdditionalResidueAddsExactlyOnePairPerExistingPoint() {
        Structure structure = PocketMatchTestFixtures.structureOf(
                PocketMatchTestFixtures.alanine(1, 0.0, 0.0, 0.0),
                PocketMatchTestFixtures.alanine(2, 5.0, 0.0, 0.0),
                PocketMatchTestFixtures.alanine(3, 0.0, 5.0, 0.0)
        );
        Pocket pocket = PocketMatchTestFixtures
                .pocketOfResidueNumbers(structure, 1, 2, 3);

        PocketMatchSignature signature =
                factory.describe(structure, pocket);

        // nine points give C(9,2) = 36 distances
        assertThat(signature.totalDistanceCount()).isEqualTo(36);
    }

    @Test
    void skipsUnresolvableAndUnclassifiableResiduesWithDiagnostics() {
        Structure structure = PocketMatchTestFixtures.structureOf(
                PocketMatchTestFixtures.alanine(1, 0.0, 0.0, 0.0),
                PocketMatchTestFixtures.residueWithoutBetaCarbon(
                        "MSE", 2, 5.0, 0.0, 0.0)
        );
        // residue 99 does not exist in the structure; MSE is not a
        // classifiable PocketMatch residue name
        Pocket pocket = PocketMatchTestFixtures
                .pocketOfResidueNumbers(structure, 1, 2, 99);

        PocketMatchSignature signature =
                factory.describe(structure, pocket);

        PocketMatchSignatureDiagnostics diagnostics =
                signature.diagnostics();
        assertThat(diagnostics.inputResidueCount()).isEqualTo(3);
        assertThat(diagnostics.representedResidueCount()).isEqualTo(1);
        assertThat(diagnostics.generatedPointCount()).isEqualTo(3);
        assertThat(diagnostics.skippedResidueCount()).isEqualTo(2);
        assertThat(diagnostics.totalDistanceCount())
                .isEqualTo(signature.totalDistanceCount());
    }

    @Test
    void signatureIsBuiltFromResidueAtomsNotAlphaSpheres() {
        // the fixture pocket carries no alpha-sphere set at all, so a
        // non-empty signature can only come from residue atom geometry
        Structure structure = PocketMatchTestFixtures.structureOf(
                PocketMatchTestFixtures.alanine(1, 0.0, 0.0, 0.0),
                PocketMatchTestFixtures.alanine(2, 5.0, 0.0, 0.0)
        );
        Pocket pocket = PocketMatchTestFixtures
                .pocketOfResidueNumbers(structure, 1, 2);

        assertThat(pocket.alphaSphereSet()).isEmpty();
        assertThat(factory.describe(structure, pocket)
                .totalDistanceCount()).isEqualTo(15);
    }

    @Test
    void caDistancesMatchKnownGeometry() {
        Structure structure = PocketMatchTestFixtures.structureOf(
                PocketMatchTestFixtures.alanine(1, 0.0, 0.0, 0.0),
                PocketMatchTestFixtures.alanine(2, 3.0, 4.0, 0.0)
        );
        Pocket pocket = PocketMatchTestFixtures
                .pocketOfResidueNumbers(structure, 1, 2);

        PocketMatchSignature signature =
                factory.describe(structure, pocket);

        PocketMatchCategory caCa = new PocketMatchCategory(
                PocketMatchResidueGroup.ALIPHATIC_SPECIAL,
                PocketMatchResidueGroup.ALIPHATIC_SPECIAL,
                PocketMatchPointType.CA,
                PocketMatchPointType.CA
        );
        assertThat(signature.distances(caCa)).hasSize(1);
        assertThat(signature.distances(caCa)[0])
                .isCloseTo(5.0, within(1.0e-9));
    }

    private static Point3D positionOf(
            List<PocketMatchPoint> points,
            PocketMatchPointType type
    ) {
        return points.stream()
                .filter(point -> point.pointType() == type)
                .map(PocketMatchPoint::position)
                .findFirst()
                .orElseThrow();
    }
}
