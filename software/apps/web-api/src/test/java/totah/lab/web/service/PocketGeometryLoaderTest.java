package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.web.persistence.PocketAtomRepository;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PocketGeometryLoaderTest {

    private final PocketAtomRepository repository =
            mock(PocketAtomRepository.class);
    private final PocketPointCloudLoader loader =
            new PocketPointCloudLoader(repository);

    @Test
    void loadsAllPocketsWithOneQueryPreservingAtomOrder() {
        when(repository.findPointCloudByPocketIds(List.of(1L, 2L)))
                .thenReturn(List.of(
                        row(1L, 0.0, 0.0, 0.0),
                        row(1L, 3.0, 0.0, 0.0),
                        row(1L, 0.0, 4.0, 0.0),
                        row(2L, 5.0, 5.0, 5.0)
                ));

        Map<Long, PocketPointCloud> clouds =
                loader.loadAll(List.of(1L, 2L));

        verify(repository, times(1))
                .findPointCloudByPocketIds(List.of(1L, 2L));
        assertEquals(2, clouds.size());
        assertEquals(
                List.of(
                        new Point3D(0.0, 0.0, 0.0),
                        new Point3D(3.0, 0.0, 0.0),
                        new Point3D(0.0, 4.0, 0.0)
                ),
                clouds.get(1L).points()
        );
        assertEquals(1, clouds.get(2L).size());
    }

    @Test
    void omitsPocketsWithMalformedCoordinates() {
        when(repository.findPointCloudByPocketIds(List.of(1L, 2L)))
                .thenReturn(List.of(
                        row(1L, null, 0.0, 0.0),
                        row(2L, 5.0, 5.0, 5.0)
                ));

        Map<Long, PocketPointCloud> clouds =
                loader.loadAll(List.of(1L, 2L));

        assertFalse(clouds.containsKey(1L));
        assertTrue(clouds.containsKey(2L));
    }

    @Test
    void omitsPocketsWithoutAtomRows() {
        when(repository.findPointCloudByPocketIds(List.of(1L, 2L)))
                .thenReturn(List.of(row(2L, 5.0, 5.0, 5.0)));

        Map<Long, PocketPointCloud> clouds =
                loader.loadAll(List.of(1L, 2L));

        assertFalse(clouds.containsKey(1L));
        assertTrue(clouds.containsKey(2L));
    }

    @Test
    void loadAllWithEmptyInputSkipsTheQuery() {
        assertEquals(Map.of(), loader.loadAll(List.of()));

        verifyNoInteractions(repository);
    }

    @Test
    void loadAllRejectsNullInput() {
        assertThrows(
                NullPointerException.class,
                () -> loader.loadAll(null)
        );

        verifyNoInteractions(repository);
    }

    @Test
    void loadAllDeduplicatesPocketIds() {
        when(repository.findPointCloudByPocketIds(List.of(1L, 2L)))
                .thenReturn(List.of(
                        row(1L, 0.0, 0.0, 0.0),
                        row(2L, 5.0, 5.0, 5.0)
                ));

        Map<Long, PocketPointCloud> clouds =
                loader.loadAll(List.of(1L, 2L, 1L, 2L, 1L));

        verify(repository, times(1))
                .findPointCloudByPocketIds(List.of(1L, 2L));
        assertEquals(2, clouds.size());
    }

    @Test
    void loadReturnsTheCloudForASinglePocket() {
        when(repository.findPointCloudByPocketIds(List.of(1L)))
                .thenReturn(List.of(row(1L, 1.0, 2.0, 3.0)));

        assertEquals(1, loader.load(1L).size());
    }

    @Test
    void loadThrowsWhenThePocketHasNoAtoms() {
        when(repository.findPointCloudByPocketIds(List.of(9L)))
                .thenReturn(List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> loader.load(9L)
        );
    }

    private static PocketAtomProjection row(
            long pocketId,
            Double x,
            Double y,
            Double z
    ) {
        return new PocketAtomProjection() {
            @Override
            public Long getPocketId() {
                return pocketId;
            }

            @Override
            public Double getX() {
                return x;
            }

            @Override
            public Double getY() {
                return y;
            }

            @Override
            public Double getZ() {
                return z;
            }
        };
    }
}
