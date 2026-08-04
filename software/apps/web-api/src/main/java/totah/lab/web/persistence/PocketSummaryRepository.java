package totah.lab.web.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PocketSummaryRepository
        extends JpaRepository<PocketSummaryEntity, Long> {

    @Query("""
        select candidate
        from PocketSummaryEntity candidate,
             PocketSummaryEntity queryPocket
        where queryPocket.pocketId = :queryPocketId
          and candidate.pocketId <> queryPocket.pocketId

          and candidate.volume between
              queryPocket.volume * :minimumVolumeRatio
              and queryPocket.volume * :maximumVolumeRatio

          and cast(candidate.residueCount as double) >=
              cast(queryPocket.residueCount as double)
                  * :minimumResidueRatio

          and cast(candidate.residueCount as double) <=
              cast(queryPocket.residueCount as double)
                  * :maximumResidueRatio

        order by
            abs(candidate.volume - queryPocket.volume)
                / queryPocket.volume
            +
            abs(
                cast(candidate.residueCount as double)
                - cast(queryPocket.residueCount as double)
            )
                / cast(queryPocket.residueCount as double)
            +
            abs(candidate.hydrophobicFraction
                - queryPocket.hydrophobicFraction)
            +
            abs(candidate.aromaticFraction
                - queryPocket.aromaticFraction)
            +
            abs(candidate.polarFraction
                - queryPocket.polarFraction)
            +
            abs(candidate.positiveFraction
                - queryPocket.positiveFraction)
            +
            abs(candidate.negativeFraction
                - queryPocket.negativeFraction)
        """)
    List<PocketSummaryEntity> findDescriptorCandidates(
            @Param("queryPocketId")
            long queryPocketId,

            @Param("minimumVolumeRatio")
            double minimumVolumeRatio,

            @Param("maximumVolumeRatio")
            double maximumVolumeRatio,

            @Param("minimumResidueRatio")
            double minimumResidueRatio,

            @Param("maximumResidueRatio")
            double maximumResidueRatio,

            Pageable pageable
    );
}