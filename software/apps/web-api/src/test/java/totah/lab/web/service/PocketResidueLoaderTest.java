package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.web.persistence.StructureDetailsProjection;
import totah.lab.web.persistence.StructureRepository;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PocketResidueLoaderTest {

    @TempDir
    Path artifactRoot;

    private final FakePocketService pocketService =
            new FakePocketService();
    private final StructureRepository structureRepository =
            mock(StructureRepository.class);

    @Test
    void wrapsMissingStructureArtifactAsUnprocessableEntity() {
        pocketService.register(42L, details(42L));

        StructureDetailsProjection structureDetails =
                mock(StructureDetailsProjection.class);
        when(structureDetails.getArtifactId()).thenReturn(7L);
        when(structureDetails.getArtifactStorageLocation())
                .thenReturn("missing.pdb");
        when(structureRepository.findStructureDetails(1000L))
                .thenReturn(Optional.of(structureDetails));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> loader().load(42L)
        );

        assertEquals(422, exception.getStatusCode().value());
    }

    @Test
    void propagatesMissingPocketAsNotFound() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> loader().load(99L)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    private PocketResidueLoader loader() {
        return new PocketResidueLoader(
                pocketService,
                structureRepository,
                new StructureArtifactService(artifactRoot.toString())
        );
    }

    private static PocketService.PocketDetails details(long pocketId) {
        return new PocketService.PocketDetails(
                pocketId,
                1,
                "FPOCKET",
                100.0,
                null,
                null,
                null,
                new PocketService.StructureSummary(
                        1000L,
                        "PDB",
                        "ACC-1",
                        "A",
                        1
                ),
                new PocketService.ReceptorSummary(2000L, "Target"),
                new PocketService.ArtifactSummary(
                        7L,
                        "missing.pdb",
                        "structure",
                        "missing.pdb"
                ),
                List.of(),
                null
        );
    }

    private static final class FakePocketService
            extends PocketService {

        private final Map<Long, PocketDetails> pockets =
                new HashMap<>();

        private FakePocketService() {
            super(null, null);
        }

        void register(long pocketId, PocketDetails details) {
            pockets.put(pocketId, details);
        }

        @Override
        public PocketDetails getPocket(long pocketId) {
            PocketDetails details = pockets.get(pocketId);

            if (details == null) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Pocket not found: " + pocketId
                );
            }

            return details;
        }
    }
}
