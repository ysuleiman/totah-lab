package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.web.persistence.PocketResidueProjection;
import totah.lab.web.persistence.StructureDetailsProjection;
import totah.lab.web.persistence.StructureRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StructureServiceTest {

    @TempDir
    Path artifactRoot;

    @Test
    void findsAllSpatialNeighborsWithoutChangingResidueIdentityOrOrder()
            throws Exception {
        Path directory = Files.createDirectories(artifactRoot.resolve("Q6UX53"));
        try (var input = getClass().getResourceAsStream(
                "/Q6UX53/Q6UX53_TMT1B_HUMAN.pdb")) {
            Files.copy(input, directory.resolve("Q6UX53_TMT1B_HUMAN.pdb"));
        }

        StructureDetailsProjection structure = proxy(
                StructureDetailsProjection.class,
                java.util.Map.of(
                        "getArtifactId", 6L,
                        "getArtifactStorageLocation",
                        "Q6UX53/Q6UX53_TMT1B_HUMAN.pdb"));

        List<PocketResidueProjection> rows = new ArrayList<>();
        for (int number = 1; number <= 244; number++) {
            PocketResidueProjection row = proxy(
                    PocketResidueProjection.class,
                    java.util.Map.of(
                            "getId", (long) number,
                            "getChain", "A",
                            "getResidueNumber", number,
                            "getInsertionCode", " "));
            rows.add(row);
        }

        StructureRepository repository = proxy(
                StructureRepository.class,
                java.util.Map.of(
                        "findStructureDetails", Optional.of(structure),
                        "findResiduesByStructureId", rows));
        StructureService service = new StructureService(
                repository,
                new StructureArtifactService(artifactRoot.toString()));

        StructureService.ResidueNeighborhood result =
                service.getResidueNeighbors(2L, 1L, 6.0);

        assertThat(result.selectedResidue().residueNumber()).isEqualTo(1);
        assertThat(result.cutoff()).isEqualTo(6.0);
        assertThat(result.neighbors()).isNotEmpty();
        assertThat(result.neighbors())
                .extracting(StructureService.NeighborDetails::distance)
                .isSorted();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.util.Map<String, ?> values) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (instance, method, arguments) -> values.get(method.getName()));
    }
}
