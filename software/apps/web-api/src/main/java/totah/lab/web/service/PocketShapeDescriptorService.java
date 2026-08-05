package totah.lab.web.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.athena.pocket.similar.PocketShapeDescriptor;
import totah.lab.athena.pocket.similar.PocketShapeDescriptorFactory;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.web.persistence.PocketAlphaSphereRepository;
import totah.lab.web.persistence.PocketRepository;
import totah.lab.web.persistence.PocketShapeDescriptorEntity;
import totah.lab.web.persistence.PocketShapeDescriptorRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Computes Athena {@link PocketShapeDescriptor}s from persisted alpha
 * spheres and upserts them into {@code pocket_shape_descriptor} for the
 * precomputed Stage 1 retrieval ordering.
 *
 * Pockets without persisted spheres are skipped (they stay descriptor-
 * less; the summary view's eligibility already keeps RESIDUE_ATOMS-only
 * pockets out of the ALPHA_SPHERES retrieval path).
 */
@Service
public class PocketShapeDescriptorService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PocketShapeDescriptorService.class);

    /**
     * Pockets per bulk sphere query / upsert batch.
     */
    static final int CHUNK_SIZE = 1000;

    private final PocketRepository pocketRepository;
    private final PocketAlphaSphereRepository sphereRepository;
    private final PocketShapeDescriptorRepository descriptorRepository;

    public PocketShapeDescriptorService(
            PocketRepository pocketRepository,
            PocketAlphaSphereRepository sphereRepository,
            PocketShapeDescriptorRepository descriptorRepository
    ) {
        this.pocketRepository =
                Objects.requireNonNull(pocketRepository);
        this.sphereRepository =
                Objects.requireNonNull(sphereRepository);
        this.descriptorRepository =
                Objects.requireNonNull(descriptorRepository);
    }

    /**
     * Structures having at least one FPOCKET pocket with persisted
     * spheres but no descriptor row (anti-join used by the backfill).
     */
    @Transactional(readOnly = true)
    public List<Long> findStructureIdsMissingDescriptors() {
        return pocketRepository
                .findStructureIdsWithPocketsMissingDescriptors(
                        PocketSource.FPOCKET
                );
    }

    /**
     * FPOCKET pockets of one structure that have persisted spheres but no
     * descriptor row.
     */
    @Transactional(readOnly = true)
    public List<Long> findPocketIdsMissingDescriptors(long structureId) {
        return pocketRepository.findPocketIdsMissingDescriptors(
                PocketSource.FPOCKET,
                structureId
        );
    }

    /**
     * Computes and persists the descriptors of all sphere-backed pockets
     * of one structure in a single transaction, so a failure rolls back
     * only that structure. Idempotent: existing rows are overwritten.
     */
    @Transactional
    public int computeAndPersistForStructure(long structureId) {
        return computeAndPersist(
                findPocketIdsMissingDescriptors(structureId)
        );
    }

    /**
     * Computes and persists descriptors for the given pockets, bulk-
     * loading spheres in chunks of {@value #CHUNK_SIZE} pockets. Pockets
     * without persisted spheres are skipped. Returns the number of
     * descriptor rows written.
     */
    @Transactional
    public int computeAndPersist(Collection<Long> pocketIds) {
        Objects.requireNonNull(pocketIds, "pocketIds");

        List<Long> distinctPocketIds = pocketIds.stream()
                .distinct()
                .toList();

        int persisted = 0;

        for (int from = 0;
             from < distinctPocketIds.size();
             from += CHUNK_SIZE) {

            List<Long> chunk = distinctPocketIds.subList(
                    from,
                    Math.min(from + CHUNK_SIZE, distinctPocketIds.size())
            );

            Map<Long, List<Point3D>> centersByPocket =
                    new LinkedHashMap<>();

            for (PocketAlphaSphereProjection sphere :
                    sphereRepository.findPointCloudByPocketIds(chunk)) {
                centersByPocket
                        .computeIfAbsent(
                                sphere.getPocketId(),
                                pocketId -> new ArrayList<>()
                        )
                        .add(new Point3D(
                                sphere.getCenterX(),
                                sphere.getCenterY(),
                                sphere.getCenterZ()
                        ));
            }

            List<PocketShapeDescriptorEntity> rows =
                    new ArrayList<>(centersByPocket.size());

            for (Map.Entry<Long, List<Point3D>> entry
                    : centersByPocket.entrySet()) {
                rows.add(PocketShapeDescriptorEntity.from(
                        entry.getKey(),
                        describe(entry.getValue())
                ));
            }

            descriptorRepository.saveAll(rows);
            persisted += rows.size();

            LOG.info("Shape descriptors: chunk {}/{} pockets, {} rows "
                            + "written ({} sphere-less skipped)",
                    from / CHUNK_SIZE + 1,
                    (distinctPocketIds.size() + CHUNK_SIZE - 1)
                            / CHUNK_SIZE,
                    rows.size(),
                    chunk.size() - rows.size());
        }

        return persisted;
    }

    /**
     * Computes and persists the descriptor of a single pocket from its
     * in-memory alpha-sphere centers — used by the import path, which
     * already holds the parsed spheres and must not re-read them.
     */
    @Transactional
    public void computeAndPersistFromCenters(
            long pocketId,
            List<Point3D> centers
    ) {
        descriptorRepository.save(PocketShapeDescriptorEntity.from(
                pocketId,
                describe(centers)
        ));
    }

    private static PocketShapeDescriptor describe(List<Point3D> centers) {
        return PocketShapeDescriptorFactory.describe(
                new PocketPointCloud(
                        centers,
                        PocketGeometryBasis.ALPHA_SPHERES
                ),
                PocketShapeDescriptorFactory.DEFAULT_RADIAL_BIN_COUNT
        );
    }
}
