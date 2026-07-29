package totah.lab.web.persistence;

public interface ResidueAnalysisProjection {

    long getRunId();

    long getStructureId();

    long getReceptorId();

    long getResidueId();

    String getChain();

    int getResidueNumber();

    String getResidueName();

    double getContactScoreThreshold();

    long getScoreFilteredLigandCount();

    long getScoreFilteredContactingLigandCount();

    double getScoreFilteredContactingLigandFraction();

    long getScoreFilteredPoseCount();

    long getScoreFilteredContactingPoseCount();

    double getScoreFilteredContactingPoseFraction();

    long getTotalLigandCount();

    long getContactingLigandCount();

    double getContactingLigandFraction();

    long getTotalPoseCount();

    long getContactingPoseCount();

    double getContactingPoseFraction();

    long getTotalGoodLigandCount();

    long getGoodContactingLigandCount();

    double getGoodContactingLigandFraction();

    long getTotalBadLigandCount();

    long getBadContactingLigandCount();

    double getBadContactingLigandFraction();

    double getContactFractionDifference();

    Double getEnrichmentRatio();

    Double getLog2Enrichment();

    Double getAvgContactingScore();

    Double getMedianContactingScore();

    Double getBestContactingScore();

    Double getWorstContactingScore();

    Double getClosestDistance();

    Double getAvgLigandMinDistance();

    Double getAvgPoseMinDistance();
}
