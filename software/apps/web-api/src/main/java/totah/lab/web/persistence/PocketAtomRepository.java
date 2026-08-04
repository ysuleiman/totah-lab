package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import totah.lab.web.service.PocketAtomProjection;

import java.util.Collection;
import java.util.List;

public interface PocketAtomRepository
        extends JpaRepository<PocketAtomEntity, Long> {

    @Query("""
        select
            pr.pocket.id as pocketId,
            a.x as x,
            a.y as y,
            a.z as z
        from PocketAtomEntity a
        join a.pocketResidue pr
        where pr.pocket.id in :pocketIds
        order by pr.pocket.id, a.id
        """)
    List<PocketAtomProjection> findPointCloudByPocketIds(
            @Param("pocketIds") Collection<Long> pocketIds
    );
}
