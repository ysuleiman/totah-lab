package totah.lab.athena.geometry;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

class GridVolumeTest {

    /**
     * Coarse known-geometry setup: one reference atom at the origin
     * on a 1.0 A grid with +/-3.0 A padding and 2.0 A clearance. On
     * the integer lattice, voxels are free exactly when their
     * squared distance from the origin lies in [4, 9]: 96 voxels of
     * 123 in the region (verified against the Python convention).
     */
    private static final FreeVolumeOptions COARSE = new FreeVolumeOptions(
            1.0, 3.0, 2.0, "test: 1.0 A grid, +/-3.0 A padding, 2.0 A clearance"
    );

    private static final EnvelopeOptions COARSE_ENVELOPE =
            new EnvelopeOptions(
                    1.0, 1.0, "test: 1.0 A grid, 1.0 A envelope radius");

    @Test
    void emptyEnvironmentGivesAnalyticFreeVolume() {
        FreeVolume result = GridVolume.localFreeVolume(
                List.of(new Point3D(0, 0, 0)),
                List.of(),
                COARSE
        );

        assertThat(result.regionVoxelCount()).isEqualTo(123);
        assertThat(result.freeVoxelCount()).isEqualTo(96);
        assertThat(result.freeVolumeCubicAngstroms())
                .isCloseTo(96.0, offset(1.0e-12));
        assertThat(result.options()).isSameAs(COARSE);
    }

    @Test
    void singleBlockingAtomRemovesExpectedVoxels() {
        FreeVolume open = GridVolume.localFreeVolume(
                List.of(new Point3D(0, 0, 0)),
                List.of(),
                COARSE
        );
        FreeVolume blocked = GridVolume.localFreeVolume(
                List.of(new Point3D(0, 0, 0)),
                List.of(new Point3D(3, 0, 0)),
                COARSE
        );

        // The blocker at (3,0,0) excludes exactly the 10 previously
        // free voxels within 2.0 A of it.
        assertThat(blocked.regionVoxelCount()).isEqualTo(123);
        assertThat(blocked.freeVoxelCount())
                .isEqualTo(open.freeVoxelCount() - 10);
        assertThat(blocked.freeVoxelCount()).isEqualTo(86);
    }

    @Test
    void ligandAtomsAreHonoredAsOccupied() {
        // A voxel on top of the ligand (distance 0 < clearance) must
        // not count as free.
        FreeVolume result = GridVolume.localFreeVolume(
                List.of(new Point3D(0, 0, 0)),
                List.of(),
                COARSE
        );

        // The 27 voxels with squared distance < 4 from the ligand
        // atom are occupied by the ligand itself.
        assertThat(result.regionVoxelCount() - result.freeVoxelCount())
                .isEqualTo(27);
    }

    @Test
    void knownEnvelopesGiveExactOverlapOnCoarseGrid() {
        EnvelopeOptions options = new EnvelopeOptions(
                1.0, 1.0, "test: 1.0 A grid, 1.0 A envelope radius"
        );
        List<Point3D> first = List.of(new Point3D(0, 0, 0));
        List<Point3D> second = List.of(new Point3D(2, 0, 0));

        EnvelopeVolume envelopeA = GridVolume.envelopeVolume(first, options);
        EnvelopeVolume envelopeB = GridVolume.envelopeVolume(second, options);

        // Each single-atom radius-1.0 envelope covers exactly the 7
        // lattice voxels within 1.0 A of its atom.
        assertThat(envelopeA.voxelCount()).isEqualTo(7);
        assertThat(envelopeB.voxelCount()).isEqualTo(7);
        assertThat(envelopeA.volumeCubicAngstroms())
                .isCloseTo(7.0, offset(1.0e-12));

        SharedEnvelopeVolume shared =
                GridVolume.sharedEnvelopeVolume(first, second, options);

        // Only the midpoint voxel (1,0,0) lies within 1.0 A of both.
        assertThat(shared.overlapVoxelCount()).isEqualTo(1);
        assertThat(shared.overlapVolumeCubicAngstroms())
                .isCloseTo(1.0, offset(1.0e-12));
        assertThat(shared.firstVolumeCubicAngstroms())
                .isCloseTo(7.0, offset(1.0e-12));
        assertThat(shared.secondVolumeCubicAngstroms())
                .isCloseTo(7.0, offset(1.0e-12));
        assertThat(shared.overlapFraction())
                .isCloseTo(1.0 / 7.0, offset(1.0e-12));
        assertThat(shared.options()).isSameAs(options);
    }

    @Test
    void disjointEnvelopesHaveZeroOverlap() {
        EnvelopeOptions options = new EnvelopeOptions(
                1.0, 1.0, "test: 1.0 A grid, 1.0 A envelope radius"
        );

        SharedEnvelopeVolume shared = GridVolume.sharedEnvelopeVolume(
                List.of(new Point3D(0, 0, 0)),
                List.of(new Point3D(10, 0, 0)),
                options
        );

        assertThat(shared.overlapVoxelCount()).isZero();
        assertThat(shared.overlapVolumeCubicAngstroms()).isZero();
        assertThat(shared.overlapFraction()).isZero();
        assertThat(shared.firstVolumeCubicAngstroms())
                .isCloseTo(7.0, offset(1.0e-12));
        assertThat(shared.secondVolumeCubicAngstroms())
                .isCloseTo(7.0, offset(1.0e-12));
    }

    @Test
    void resultsAreDeterministic() {
        List<Point3D> ligand = List.of(
                new Point3D(0, 0, 0),
                new Point3D(1.3, 0.7, -0.4)
        );
        List<Point3D> protein = List.of(
                new Point3D(3, 0, 0),
                new Point3D(-2.2, 1.1, 0.9)
        );

        FreeVolume first =
                GridVolume.localFreeVolume(ligand, protein, COARSE);
        FreeVolume second =
                GridVolume.localFreeVolume(ligand, protein, COARSE);
        assertThat(first).isEqualTo(second);

        SharedEnvelopeVolume sharedFirst =
                GridVolume.sharedEnvelopeVolume(ligand, protein, COARSE_ENVELOPE);
        SharedEnvelopeVolume sharedSecond =
                GridVolume.sharedEnvelopeVolume(ligand, protein, COARSE_ENVELOPE);
        assertThat(sharedFirst).isEqualTo(sharedSecond);
    }

    @Test
    void rejectsNonPositiveSpacing() {
        assertThatThrownBy(() -> new FreeVolumeOptions(
                0.0, 3.0, 2.0, "test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FreeVolumeOptions(
                -0.5, 3.0, 2.0, "test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnvelopeOptions(
                0.0, 1.7, "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonFiniteOrNonPositiveParameters() {
        assertThatThrownBy(() -> new FreeVolumeOptions(
                Double.NaN, 3.0, 2.0, "test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FreeVolumeOptions(
                0.5, 0.0, 2.0, "test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FreeVolumeOptions(
                0.5, 3.0, -2.0, "test"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnvelopeOptions(
                0.5, Double.POSITIVE_INFINITY, "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankProvenance() {
        assertThatThrownBy(() -> new FreeVolumeOptions(
                0.5, 3.0, 2.0, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EnvelopeOptions(0.5, 1.7, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyAtomSets() {
        assertThatThrownBy(() -> GridVolume.localFreeVolume(
                List.of(), List.of(), COARSE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GridVolume.envelopeVolume(
                List.of(), COARSE_ENVELOPE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GridVolume.sharedEnvelopeVolume(
                List.of(), List.of(new Point3D(0, 0, 0)), COARSE_ENVELOPE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GridVolume.sharedEnvelopeVolume(
                List.of(new Point3D(0, 0, 0)), List.of(), COARSE_ENVELOPE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
