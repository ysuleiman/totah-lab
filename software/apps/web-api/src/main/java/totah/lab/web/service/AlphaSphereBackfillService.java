package totah.lab.web.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.hermes.file.pocket.FPocketParser;
import totah.lab.web.persistence.PocketAlphaSphereEntity;
import totah.lab.web.persistence.PocketAlphaSphereRepository;
import totah.lab.web.persistence.PocketEntity;
import totah.lab.web.persistence.PocketRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Backfills {@code pocket_alpha_sphere} rows for pockets that were
 * imported before alpha-sphere persistence existed.
 *
 * Spheres are read from the fpocket {@code pocketN_vert.pqr} file that is
 * the sibling of each pocket's artifact ({@code pockets/pocketN_atm.pdb})
 * — they are never regenerated from structure geometry. Relative artifact
 * storage locations are resolved against the artifact root exactly like
 * {@link StructureArtifactService} does; absolute locations are used
 * as-is.
 *
 * Each structure is backfilled in its own transaction
 * ({@link #backfillStructure}), so a failure rolls back only that
 * structure.
 */
@Service
public class AlphaSphereBackfillService {

    private static final Pattern POCKET_ATOM_FILE =
            Pattern.compile("pocket(\\d+)_atm\\.pdb$");

    private final PocketRepository pocketRepository;
    private final PocketAlphaSphereRepository sphereRepository;
    private final StructureArtifactService structureArtifactService;

    public AlphaSphereBackfillService(
            PocketRepository pocketRepository,
            PocketAlphaSphereRepository sphereRepository,
            StructureArtifactService structureArtifactService
    ) {
        this.pocketRepository =
                Objects.requireNonNull(pocketRepository);
        this.sphereRepository =
                Objects.requireNonNull(sphereRepository);
        this.structureArtifactService =
                Objects.requireNonNull(structureArtifactService);
    }

    /**
     * Structures that have at least one FPOCKET pocket without any
     * persisted alpha spheres. A null accession selects all structures.
     */
    public List<Long> findStructureIdsMissingSpheres(
            String structureAccession
    ) {
        return pocketRepository.findStructureIdsWithPocketsMissingSpheres(
                PocketSource.FPOCKET,
                structureAccession
        );
    }

    /**
     * Backfills all sphere-less FPOCKET pockets of one structure in a
     * single transaction.
     */
    @Transactional(rollbackFor = IOException.class)
    public StructureBackfillResult backfillStructure(long structureId)
            throws IOException {

        return process(structureId, false);
    }

    /**
     * Dry-run counterpart of {@link #backfillStructure}: classifies every
     * candidate (and reports the sphere counts that would be inserted)
     * without writing anything.
     */
    @Transactional(readOnly = true)
    public StructureBackfillResult previewStructure(long structureId)
            throws IOException {

        return process(structureId, true);
    }

    private StructureBackfillResult process(
            long structureId,
            boolean dryRun
    ) throws IOException {

        List<PocketEntity> candidates =
                pocketRepository.findPocketsMissingSpheres(
                        PocketSource.FPOCKET,
                        structureId
                );

        // Defensive: the anti-join already excludes these, but never add
        // spheres to a pocket that has them.
        Set<Long> alreadyHavingSpheres =
                candidates.isEmpty()
                        ? Set.of()
                        : sphereRepository.findPocketIdsHavingSpheres(
                                candidates.stream()
                                        .map(PocketEntity::getId)
                                        .toList()
                        );

        int backfilled = 0;
        int spheresInserted = 0;
        int alreadyHadSpheres = 0;
        List<String> missingVertFiles = new ArrayList<>();
        List<String> unparseableVertFiles = new ArrayList<>();
        List<String> ambiguousArtifacts = new ArrayList<>();

        for (PocketEntity pocket : candidates) {
            if (alreadyHavingSpheres.contains(pocket.getId())) {
                alreadyHadSpheres++;
                continue;
            }

            String storageLocation =
                    pocket.getArtifact().getStorageLocation();
            Matcher matcher =
                    POCKET_ATOM_FILE.matcher(storageLocation);
            if (!matcher.find()) {
                ambiguousArtifacts.add(
                        storageLocation
                                + " (artifact does not end with"
                                + " pockets/pocketN_atm.pdb)"
                );
                continue;
            }

            int artifactPocketNumber = Integer.parseInt(matcher.group(1));
            if (!Objects.equals(
                    artifactPocketNumber,
                    pocket.getPocketNumber()
            )) {
                ambiguousArtifacts.add(
                        storageLocation
                                + " (artifact pocket number "
                                + artifactPocketNumber
                                + " does not match pocket number "
                                + pocket.getPocketNumber()
                                + ")"
                );
                continue;
            }

            Path vertFile = resolve(storageLocation)
                    .resolveSibling(
                            "pocket" + artifactPocketNumber + "_vert.pqr"
                    );
            if (!Files.isRegularFile(vertFile)) {
                missingVertFiles.add(vertFile.toString());
                continue;
            }

            final List<AlphaSphere> spheres;
            try {
                spheres = FPocketParser.readAlphaSpheres(vertFile);
            } catch (IOException | RuntimeException exception) {
                unparseableVertFiles.add(
                        vertFile + " (" + exception.getMessage() + ")"
                );
                continue;
            }

            if (!dryRun) {
                for (int index = 0; index < spheres.size(); index++) {
                    AlphaSphere sphere = spheres.get(index);
                    pocket.addAlphaSphere(new PocketAlphaSphereEntity(
                            index,
                            sphere.center().x(),
                            sphere.center().y(),
                            sphere.center().z(),
                            sphere.radius()
                    ));
                }
                pocketRepository.save(pocket);
            }

            backfilled++;
            spheresInserted += spheres.size();
        }

        return new StructureBackfillResult(
                structureId,
                backfilled,
                spheresInserted,
                alreadyHadSpheres,
                List.copyOf(missingVertFiles),
                List.copyOf(unparseableVertFiles),
                List.copyOf(ambiguousArtifacts)
        );
    }

    private Path resolve(String storageLocation) throws IOException {
        Path path = Path.of(storageLocation);
        return path.isAbsolute()
                ? path
                : structureArtifactService.resolveStorageLocation(
                        storageLocation
                );
    }

    public record StructureBackfillResult(
            long structureId,
            int pocketsBackfilled,
            int spheresInserted,
            int alreadyHadSpheres,
            List<String> missingVertFiles,
            List<String> unparseableVertFiles,
            List<String> ambiguousArtifacts
    ) {
    }
}
