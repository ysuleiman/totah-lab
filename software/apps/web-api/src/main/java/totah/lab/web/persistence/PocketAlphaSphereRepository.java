package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import totah.lab.web.service.PocketAlphaSphereProjection;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface PocketAlphaSphereRepository
        extends JpaRepository<PocketAlphaSphereEntity, Long> {

    /**
     * Bulk point-cloud query for many pockets in one round trip, ordered
     * by pocket_id, sphere_index — the sphere counterpart of
     * {@code PocketAtomRepository.findPointCloudByPocketIds}.
     */
    @Query("""
        select
            s.pocket.id as pocketId,
            s.sphereIndex as sphereIndex,
            s.centerX as centerX,
            s.centerY as centerY,
            s.centerZ as centerZ,
            s.radius as radius
        from PocketAlphaSphereEntity s
        where s.pocket.id in :pocketIds
        order by s.pocket.id, s.sphereIndex
        """)
    List<PocketAlphaSphereProjection> findPointCloudByPocketIds(
            @Param("pocketIds") Collection<Long> pocketIds
    );

    /**
     * Loads the spheres of many pockets in one query, ordered so that the
     * result groups by pocket and preserves parser order within a pocket.
     */
    List<PocketAlphaSphereEntity>
    findByPocketIdInOrderByPocketIdAscSphereIndexAsc(
            Collection<Long> pocketIds
    );

    default Map<Long, List<PocketAlphaSphereEntity>>
    findByPocketIdsGrouped(Collection<Long> pocketIds) {
        return findByPocketIdInOrderByPocketIdAscSphereIndexAsc(pocketIds)
                .stream()
                .collect(Collectors.groupingBy(
                        sphere -> sphere.getPocket().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    /**
     * Which of the candidate pockets already have sphere rows — used by
     * backfill selection as an anti-join guard.
     */
    @Query("""
            SELECT DISTINCT sphere.pocket.id
            FROM PocketAlphaSphereEntity sphere
            WHERE sphere.pocket.id IN :pocketIds
            """)
    Set<Long> findPocketIdsHavingSpheres(
            @Param("pocketIds") Collection<Long> pocketIds
    );

    long countByPocketId(long pocketId);
}
