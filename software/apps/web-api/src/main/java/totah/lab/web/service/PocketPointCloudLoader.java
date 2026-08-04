package totah.lab.web.persistence;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.web.service.PocketAtomProjection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class PocketPointCloudLoader {

    private final PocketAtomRepository pocketAtomRepository;

    public PocketPointCloudLoader(
            PocketAtomRepository pocketAtomRepository
    ) {
        this.pocketAtomRepository = pocketAtomRepository;
    }

    public PocketPointCloud load(long pocketId) {

        PocketPointCloud pointCloud =
                loadAll(List.of(pocketId)).get(pocketId);

        if (pointCloud == null) {
            throw new IllegalArgumentException(
                    "Pocket " + pocketId + " contains no atoms."
            );
        }

        return pointCloud;
    }

    /**
     * Loads the point clouds for many pockets with a single query.
     *
     * <p>Pockets with no atom rows or with missing or malformed
     * coordinates are omitted from the result; callers treat an absent
     * entry as unavailable geometry.
     */
    public Map<Long, PocketPointCloud> loadAll(
            Collection<Long> pocketIds
    ) {
        Objects.requireNonNull(pocketIds, "pocketIds");

        List<Long> distinctPocketIds = pocketIds.stream()
                .distinct()
                .toList();

        if (distinctPocketIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<PocketAtomProjection>> atomsByPocket =
                new LinkedHashMap<>();

        for (PocketAtomProjection atom :
                pocketAtomRepository.findPointCloudByPocketIds(
                        distinctPocketIds
                )) {
            atomsByPocket
                    .computeIfAbsent(
                            atom.getPocketId(),
                            pocketId -> new ArrayList<>()
                    )
                    .add(atom);
        }

        Map<Long, PocketPointCloud> pointClouds = new LinkedHashMap<>();

        for (Map.Entry<Long, List<PocketAtomProjection>> entry :
                atomsByPocket.entrySet()) {
            try {
                List<Point3D> points = entry.getValue()
                        .stream()
                        .map(atom -> new Point3D(
                                atom.getX(),
                                atom.getY(),
                                atom.getZ()
                        ))
                        .toList();

                pointClouds.put(
                        entry.getKey(),
                        new PocketPointCloud(
                                points,
                                PocketGeometryBasis.RESIDUE_ATOMS
                        )
                );
            } catch (RuntimeException exception) {
                // Skip pockets with missing or malformed coordinates.
            }
        }

        return pointClouds;
    }
}
