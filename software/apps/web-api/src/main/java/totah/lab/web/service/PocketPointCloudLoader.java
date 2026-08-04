package totah.lab.web.service;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.web.persistence.PocketAlphaSphereRepository;
import totah.lab.web.persistence.PocketAtomRepository;

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
    private final PocketAlphaSphereRepository pocketAlphaSphereRepository;

    public PocketPointCloudLoader(
            PocketAtomRepository pocketAtomRepository,
            PocketAlphaSphereRepository pocketAlphaSphereRepository
    ) {
        this.pocketAtomRepository = pocketAtomRepository;
        this.pocketAlphaSphereRepository = pocketAlphaSphereRepository;
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
     * Loads the point clouds for many pockets without per-pocket queries.
     *
     * <p>Pockets with persisted alpha spheres get an ALPHA_SPHERES cloud
     * (sphere centers in sphere_index order); all others fall back to
     * their residue-atom coordinates (RESIDUE_ATOMS). Pockets with no
     * usable rows or with missing or malformed coordinates are omitted
     * from the result; callers treat an absent entry as unavailable
     * geometry.
     */
    public Map<Long, PocketPointCloud> loadAll(
            Collection<Long> pocketIds
    ) {
        return loadAllWithSpheres(pocketIds).pointClouds();
    }

    /**
     * Like {@link #loadAll}, but also returns the persisted alpha spheres
     * (with radii) of every pocket that has them, for the geometry views.
     * Exactly one bulk sphere query and at most one bulk atom query (for
     * the sphere-less pockets only) are issued.
     */
    public LoadedPointClouds loadAllWithSpheres(
            Collection<Long> pocketIds
    ) {
        Objects.requireNonNull(pocketIds, "pocketIds");

        List<Long> distinctPocketIds = pocketIds.stream()
                .distinct()
                .toList();

        if (distinctPocketIds.isEmpty()) {
            return new LoadedPointClouds(Map.of(), Map.of());
        }

        Map<Long, List<PocketAlphaSphereProjection>> spheresByPocket =
                new LinkedHashMap<>();

        for (PocketAlphaSphereProjection sphere :
                pocketAlphaSphereRepository.findPointCloudByPocketIds(
                        distinctPocketIds
                )) {
            spheresByPocket
                    .computeIfAbsent(
                            sphere.getPocketId(),
                            pocketId -> new ArrayList<>()
                    )
                    .add(sphere);
        }

        List<Long> spherelessPocketIds = distinctPocketIds.stream()
                .filter(pocketId -> !spheresByPocket.containsKey(pocketId))
                .toList();

        Map<Long, List<PocketAtomProjection>> atomsByPocket =
                new LinkedHashMap<>();

        if (!spherelessPocketIds.isEmpty()) {
            for (PocketAtomProjection atom :
                    pocketAtomRepository.findPointCloudByPocketIds(
                            spherelessPocketIds
                    )) {
                atomsByPocket
                        .computeIfAbsent(
                                atom.getPocketId(),
                                pocketId -> new ArrayList<>()
                        )
                        .add(atom);
            }
        }

        Map<Long, PocketPointCloud> pointClouds = new LinkedHashMap<>();
        Map<Long, List<AlphaSphereView>> alphaSpheres =
                new LinkedHashMap<>();

        for (Long pocketId : distinctPocketIds) {
            List<PocketAlphaSphereProjection> spheres =
                    spheresByPocket.get(pocketId);

            if (spheres != null) {
                try {
                    pointClouds.put(
                            pocketId,
                            new PocketPointCloud(
                                    spheres.stream()
                                            .map(PocketPointCloudLoader
                                                    ::center)
                                            .toList(),
                                    PocketGeometryBasis.ALPHA_SPHERES
                            )
                    );

                    List<AlphaSphereView> views = new ArrayList<>();
                    for (PocketAlphaSphereProjection sphere : spheres) {
                        views.add(new AlphaSphereView(
                                sphere.getSphereIndex(),
                                center(sphere),
                                sphere.getRadius()
                        ));
                    }
                    alphaSpheres.put(pocketId, List.copyOf(views));
                } catch (RuntimeException exception) {
                    // Skip pockets with missing or malformed coordinates.
                    pointClouds.remove(pocketId);
                    alphaSpheres.remove(pocketId);
                }
                continue;
            }

            List<PocketAtomProjection> atoms = atomsByPocket.get(pocketId);
            if (atoms == null) {
                continue;
            }

            try {
                pointClouds.put(
                        pocketId,
                        new PocketPointCloud(
                                atoms.stream()
                                        .map(atom -> new Point3D(
                                                atom.getX(),
                                                atom.getY(),
                                                atom.getZ()
                                        ))
                                        .toList(),
                                PocketGeometryBasis.RESIDUE_ATOMS
                        )
                );
            } catch (RuntimeException exception) {
                // Skip pockets with missing or malformed coordinates.
            }
        }

        return new LoadedPointClouds(pointClouds, alphaSpheres);
    }

    private static Point3D center(PocketAlphaSphereProjection sphere) {
        return new Point3D(
                sphere.getCenterX(),
                sphere.getCenterY(),
                sphere.getCenterZ()
        );
    }

    /**
     * Result of {@link #loadAllWithSpheres}: the point clouds keyed by
     * pocket id (insertion order follows the requested id order) and the
     * alpha spheres of the pockets that have them, in sphere_index order.
     */
    public record LoadedPointClouds(
            Map<Long, PocketPointCloud> pointClouds,
            Map<Long, List<AlphaSphereView>> alphaSpheres
    ) {
    }
}
