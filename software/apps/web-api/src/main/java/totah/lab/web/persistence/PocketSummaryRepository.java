package totah.lab.web.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PocketSummaryRepository
        extends JpaRepository<PocketSummaryEntity, Long> {

    /*
     * Stage 1 retrieval: the same broad volume/residue band predicates as
     * before, but ordered by the exact Athena PocketRetrievalDistance
     * formula evaluated over the precomputed descriptor columns of the
     * materialized view (geometry only — volume/residue/chemistry no
     * longer drive retrieval):
     *
     *   logRatio(a, b) = a <= 0 or b <= 0 ? 1 : least(1, |ln(a/b)| / ln 4)
     *   scaleAware     = 0.20 * logRatio(rg) + 0.20 * logRatio(major)
     *                  + 0.15 * |Δelongation| + 0.15 * |Δflatness|
     *                  + 0.30 * (0.5 * Σ|Δh_i|)
     *   scaleNormalized= (0.15 * |Δelongation| + 0.15 * |Δflatness|
     *                  + 0.30 * (0.5 * Σ|Δh_i|)) / 0.60
     *   retrieval      = least(scaleAware, scaleNormalized + 0.05)
     *
     * Candidates without a precomputed descriptor evaluate to NULL and
     * sort last (PostgreSQL ASC defaults to NULLS LAST).
     */
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
            least(
                0.20 * (case
                    when candidate.radiusOfGyration <= 0.0
                         or :queryRadiusOfGyration <= 0.0
                    then 1.0
                    else least(1.0,
                        abs(ln(
                            candidate.radiusOfGyration
                                / :queryRadiusOfGyration))
                            / ln(4.0))
                    end)
                + 0.20 * (case
                    when candidate.extentMajor <= 0.0
                         or :queryExtentMajor <= 0.0
                    then 1.0
                    else least(1.0,
                        abs(ln(
                            candidate.extentMajor / :queryExtentMajor))
                            / ln(4.0))
                    end)
                + 0.15 * abs(candidate.elongation - :queryElongation)
                + 0.15 * abs(candidate.flatness - :queryFlatness)
                + 0.30 * (0.5 * (
                      abs(candidate.h0 - :queryH0)
                    + abs(candidate.h1 - :queryH1)
                    + abs(candidate.h2 - :queryH2)
                    + abs(candidate.h3 - :queryH3)
                    + abs(candidate.h4 - :queryH4)
                    + abs(candidate.h5 - :queryH5)
                    + abs(candidate.h6 - :queryH6)
                    + abs(candidate.h7 - :queryH7)
                    + abs(candidate.h8 - :queryH8)
                    + abs(candidate.h9 - :queryH9)
                    + abs(candidate.h10 - :queryH10)
                    + abs(candidate.h11 - :queryH11))),
                (0.15 * abs(candidate.elongation - :queryElongation)
                + 0.15 * abs(candidate.flatness - :queryFlatness)
                + 0.30 * (0.5 * (
                      abs(candidate.h0 - :queryH0)
                    + abs(candidate.h1 - :queryH1)
                    + abs(candidate.h2 - :queryH2)
                    + abs(candidate.h3 - :queryH3)
                    + abs(candidate.h4 - :queryH4)
                    + abs(candidate.h5 - :queryH5)
                    + abs(candidate.h6 - :queryH6)
                    + abs(candidate.h7 - :queryH7)
                    + abs(candidate.h8 - :queryH8)
                    + abs(candidate.h9 - :queryH9)
                    + abs(candidate.h10 - :queryH10)
                    + abs(candidate.h11 - :queryH11)))) / 0.60 + 0.05)
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

            @Param("queryRadiusOfGyration")
            double queryRadiusOfGyration,

            @Param("queryExtentMajor")
            double queryExtentMajor,

            @Param("queryElongation")
            double queryElongation,

            @Param("queryFlatness")
            double queryFlatness,

            @Param("queryH0")
            double queryH0,

            @Param("queryH1")
            double queryH1,

            @Param("queryH2")
            double queryH2,

            @Param("queryH3")
            double queryH3,

            @Param("queryH4")
            double queryH4,

            @Param("queryH5")
            double queryH5,

            @Param("queryH6")
            double queryH6,

            @Param("queryH7")
            double queryH7,

            @Param("queryH8")
            double queryH8,

            @Param("queryH9")
            double queryH9,

            @Param("queryH10")
            double queryH10,

            @Param("queryH11")
            double queryH11,

            Pageable pageable
    );

    /**
     * Legacy Stage 1 ordering (volume/residue/chemistry descriptor
     * distance), used only when the query pocket has no precomputed shape
     * descriptor — e.g. a legacy row predating the descriptor backfill.
     */
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
    List<PocketSummaryEntity> findDescriptorCandidatesLegacyOrder(
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
