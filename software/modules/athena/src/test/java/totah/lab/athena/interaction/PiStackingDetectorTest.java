package totah.lab.athena.interaction;

import org.junit.jupiter.api.Test;
import totah.lab.athena.interaction.perception.AromaticRing;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.Vector3D;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static totah.lab.athena.interaction.InteractionFixtures.degradedRing;
import static totah.lab.athena.interaction.InteractionFixtures.hexRing;

class PiStackingDetectorTest {

    private static final InteractionThresholds THRESHOLDS =
            InteractionThresholds.athenaDefaults();
    private static final ResidueId PROTEIN = new ResidueId("A", 43, null);
    private static final ResidueId LIGAND = new ResidueId("L", 501, null);
    private static final Vector3D X = new Vector3D(1, 0, 0);
    private static final Vector3D Y = new Vector3D(0, 1, 0);
    private static final Vector3D Z = new Vector3D(0, 0, 1);

    private final PiStackingDetector detector = new PiStackingDetector();

    private static AromaticRing proteinRing(Point3D center) {
        return hexRing("PHE A:43 ring0", PROTEIN, 1, center, X, Y);
    }

    private static AromaticRing ligandRing(Point3D center, Vector3D v) {
        return hexRing("LIG L:501 ring0", LIGAND, 101, center, X, v);
    }

    @Test
    void perfectParallelStackIsAccepted() {
        // Deviation from PLIP: vecangle returning exactly 0.0 rejects a
        // numerically perfect parallel stack there; here a == 0 is valid.
        AromaticRing protein = proteinRing(new Point3D(0, 0, 0));
        AromaticRing ligand = ligandRing(new Point3D(0, 0, 3.4), Y);

        List<Interaction> stacks = detector.detect(
                List.of(protein), List.of(ligand), THRESHOLDS);

        assertThat(stacks).singleElement().satisfies(stack -> {
            assertThat(stack.type())
                    .isEqualTo(InteractionType.PI_STACK_PARALLEL);
            assertThat(stack.residue()).isEqualTo(PROTEIN);
            assertThat(stack.distanceAngstroms())
                    .isCloseTo(3.4, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(stack.primaryAngleDegrees())
                    .isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(stack.secondaryAngleDegrees()).isNull();
            assertThat(stack.proteinGroupId()).isEqualTo("PHE A:43 ring0");
            assertThat(stack.ligandGroupId()).isEqualTo("LIG L:501 ring0");
        });
    }

    @Test
    void parallelStackWithSmallOffsetIsAccepted() {
        AromaticRing protein = proteinRing(new Point3D(0, 0, 0));
        AromaticRing ligand = ligandRing(new Point3D(1.5, 0, 3.4), Y);

        assertThat(detector.detect(List.of(protein), List.of(ligand),
                THRESHOLDS))
                .singleElement()
                .extracting(Interaction::type)
                .isEqualTo(InteractionType.PI_STACK_PARALLEL);
    }

    @Test
    void parallelStackBeyondOffsetIsRejected() {
        AromaticRing protein = proteinRing(new Point3D(0, 0, 0));
        AromaticRing ligand = ligandRing(new Point3D(2.5, 0, 3.4), Y);

        assertThat(detector.detect(List.of(protein), List.of(ligand),
                THRESHOLDS)).isEmpty();
    }

    @Test
    void fortyFiveDegreesYieldsNothing() {
        Vector3D v45 = new Vector3D(0, Math.cos(Math.toRadians(45)),
                Math.sin(Math.toRadians(45)));
        AromaticRing protein = proteinRing(new Point3D(0, 0, 0));
        AromaticRing ligand = ligandRing(new Point3D(0, 0, 3.4), v45);

        assertThat(detector.detect(List.of(protein), List.of(ligand),
                THRESHOLDS)).isEmpty();
    }

    @Test
    void perpendicularWithoutOffsetIsTShaped() {
        AromaticRing protein = proteinRing(new Point3D(0, 0, 0));
        AromaticRing ligand = ligandRing(new Point3D(0, 0, 3.4), Z);

        assertThat(detector.detect(List.of(protein), List.of(ligand),
                THRESHOLDS))
                .singleElement()
                .satisfies(stack -> {
                    assertThat(stack.type())
                            .isEqualTo(InteractionType.PI_STACK_T_SHAPED);
                    assertThat(stack.primaryAngleDegrees()).isCloseTo(90.0,
                            org.assertj.core.data.Offset.offset(1e-9));
                });
    }

    @Test
    void perpendicularBeyondOffsetIsRejected() {
        AromaticRing protein = proteinRing(new Point3D(0, 0, 0));
        AromaticRing ligand = ligandRing(new Point3D(2.5, 0, 3.4), Z);

        assertThat(detector.detect(List.of(protein), List.of(ligand),
                THRESHOLDS)).isEmpty();
    }

    @Test
    void parallelDeviationBoundIsInclusive() {
        // Folded normal angle exactly 30 degrees: accepted here
        // (inclusive), strictly rejected by PLIP.
        Vector3D v30 = new Vector3D(0, Math.cos(Math.toRadians(30)),
                Math.sin(Math.toRadians(30)));
        AromaticRing protein = proteinRing(new Point3D(0, 0, 0));
        AromaticRing ligand = ligandRing(new Point3D(0, 0, 3.4), v30);

        assertThat(detector.detect(List.of(protein), List.of(ligand),
                THRESHOLDS))
                .singleElement()
                .extracting(Interaction::type)
                .isEqualTo(InteractionType.PI_STACK_PARALLEL);
    }

    @Test
    void distanceBoundsAreMinExclusiveMaxInclusive() {
        AromaticRing protein = proteinRing(new Point3D(0, 0, 0));
        AromaticRing atMax = ligandRing(new Point3D(0, 0, 5.5), Y);
        AromaticRing beyondMax = ligandRing(new Point3D(0, 0, 5.6), Y);
        AromaticRing atMin = ligandRing(new Point3D(0, 0, 0.5), Y);

        assertThat(detector.detect(List.of(protein), List.of(atMax),
                THRESHOLDS)).hasSize(1);
        assertThat(detector.detect(List.of(protein), List.of(beyondMax),
                THRESHOLDS)).isEmpty();
        assertThat(detector.detect(List.of(protein), List.of(atMin),
                THRESHOLDS)).isEmpty();
    }

    @Test
    void degradedRingsAreSkipped() {
        AromaticRing protein = proteinRing(new Point3D(0, 0, 0));
        AromaticRing degraded = degradedRing("LIG L:501 ring0", LIGAND, 101,
                new Point3D(0, 0, 3.4));

        assertThat(detector.detect(List.of(protein), List.of(degraded),
                THRESHOLDS)).isEmpty();
    }
}
