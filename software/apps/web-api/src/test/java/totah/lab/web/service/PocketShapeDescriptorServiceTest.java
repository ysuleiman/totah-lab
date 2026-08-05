package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.athena.pocket.similar.PocketRetrievalDistance;
import totah.lab.athena.pocket.similar.PocketShapeDescriptor;
import totah.lab.athena.pocket.similar.PocketShapeDescriptorFactory;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.web.persistence.PocketAlphaSphereRepository;
import totah.lab.web.persistence.PocketRepository;
import totah.lab.web.persistence.PocketShapeDescriptorEntity;
import totah.lab.web.persistence.PocketShapeDescriptorRepository;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PocketShapeDescriptorServiceTest {

    private static final double TOLERANCE = 1.0e-9;

    // Irregular 8-point cloud (same fixture as the similarity tests).
    private static final double[][] CLOUD = {
            {0.0, 0.0, 0.0},
            {10.0, 0.0, 0.0},
            {0.0, 6.0, 0.0},
            {0.0, 0.0, 3.0},
            {8.0, 5.0, 2.0},
            {2.0, 4.0, 6.0},
            {7.0, 1.0, 5.0},
            {3.0, 8.0, 1.0}
    };

    private final PocketRepository pocketRepository =
            mock(PocketRepository.class);
    private final PocketAlphaSphereRepository sphereRepository =
            mock(PocketAlphaSphereRepository.class);
    private final PocketShapeDescriptorRepository descriptorRepository =
            mock(PocketShapeDescriptorRepository.class);

    private final PocketShapeDescriptorService service =
            new PocketShapeDescriptorService(
                    pocketRepository,
                    sphereRepository,
                    descriptorRepository
            );

    @Test
    void persistsDescriptorFieldsMatchingTheAthenaFactory() {
        stubSpheres(1L, CLOUD);

        int persisted = service.computeAndPersist(List.of(1L));

        assertEquals(1, persisted);

        PocketShapeDescriptorEntity entity = savedEntity();

        PocketShapeDescriptor expected =
                PocketShapeDescriptorFactory.describe(
                        new PocketPointCloud(
                                points(CLOUD),
                                PocketGeometryBasis.ALPHA_SPHERES
                        ),
                        PocketShapeDescriptorFactory
                                .DEFAULT_RADIAL_BIN_COUNT
                );

        assertEquals(1L, entity.getPocketId());
        assertEquals(expected.pointCount(), entity.getPointCount());
        assertEquals(
                expected.radiusOfGyration(),
                entity.getRadiusOfGyration(),
                TOLERANCE
        );
        assertEquals(
                expected.majorExtent(),
                entity.getExtentMajor(),
                TOLERANCE
        );
        assertEquals(
                expected.middleExtent(),
                entity.getExtentMiddle(),
                TOLERANCE
        );
        assertEquals(
                expected.minorExtent(),
                entity.getExtentMinor(),
                TOLERANCE
        );
        assertEquals(
                expected.middleExtent() / expected.majorExtent(),
                entity.getElongation(),
                TOLERANCE
        );
        assertEquals(
                expected.minorExtent() / expected.majorExtent(),
                entity.getFlatness(),
                TOLERANCE
        );
        assertArrayEquals(
                expected.radialHistogram(),
                entity.getRadialHistogram(),
                TOLERANCE
        );
        assertEquals(
                PocketRetrievalDistance.DESCRIPTOR_VERSION,
                entity.getDescriptorVersion()
        );
    }

    @Test
    void normalizedElongationAndFlatnessAreBounded() {
        stubSpheres(1L, CLOUD);

        service.computeAndPersist(List.of(1L));

        PocketShapeDescriptorEntity entity = savedEntity();

        assertTrue(entity.getElongation() >= 0.0
                && entity.getElongation() <= 1.0);
        assertTrue(entity.getFlatness() >= 0.0
                && entity.getFlatness() <= 1.0);
    }

    @Test
    void skipsPocketsWithoutSpheres() {
        stubSpheres(1L, CLOUD);

        int persisted = service.computeAndPersist(List.of(1L, 2L, 3L));

        assertEquals(1, persisted);
        assertEquals(1L, savedEntity().getPocketId());
    }

    @Test
    void rerunOverwritesTheSameRows() {
        stubSpheres(1L, CLOUD);

        service.computeAndPersist(List.of(1L));
        service.computeAndPersist(List.of(1L));

        ArgumentCaptor<List<PocketShapeDescriptorEntity>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(descriptorRepository, times(2))
                .saveAll(captor.capture());

        PocketShapeDescriptorEntity first = captor.getAllValues()
                .get(0).get(0);
        PocketShapeDescriptorEntity second = captor.getAllValues()
                .get(1).get(0);

        assertEquals(first.getPocketId(), second.getPocketId());
        assertEquals(
                first.getRadiusOfGyration(),
                second.getRadiusOfGyration(),
                0.0
        );
        assertArrayEquals(
                first.getRadialHistogram(),
                second.getRadialHistogram(),
                0.0
        );
    }

    @SuppressWarnings("unchecked")
    private PocketShapeDescriptorEntity savedEntity() {
        ArgumentCaptor<List<PocketShapeDescriptorEntity>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(descriptorRepository).saveAll(captor.capture());
        return captor.getValue().get(0);
    }

    private void stubSpheres(long pocketId, double[][] coordinates) {
        List<PocketAlphaSphereProjection> projections = new ArrayList<>();

        for (int index = 0; index < coordinates.length; index++) {
            double[] coordinate = coordinates[index];
            int sphereIndex = index;

            PocketAlphaSphereProjection projection =
                    mock(PocketAlphaSphereProjection.class);
            when(projection.getPocketId()).thenReturn(pocketId);
            when(projection.getSphereIndex()).thenReturn(sphereIndex);
            when(projection.getCenterX()).thenReturn(coordinate[0]);
            when(projection.getCenterY()).thenReturn(coordinate[1]);
            when(projection.getCenterZ()).thenReturn(coordinate[2]);
            when(projection.getRadius()).thenReturn(4.0);

            projections.add(projection);
        }

        when(sphereRepository.findPointCloudByPocketIds(anyCollection()))
                .thenReturn(projections);
    }

    private static List<Point3D> points(double[][] coordinates) {
        List<Point3D> points = new ArrayList<>();
        for (double[] coordinate : coordinates) {
            points.add(new Point3D(
                    coordinate[0],
                    coordinate[1],
                    coordinate[2]
            ));
        }
        return points;
    }
}
