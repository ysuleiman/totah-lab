package totah.lab.web.persistence;

public interface ResidueScoreBandProjection {

    long getRunId();

    long getStructureId();

    long getReceptorId();

    double getScoreLower();

    double getScoreUpper();

    long getResidueId();

    String getChain();

    int getResidueNumber();

    String getResidueName();

    long getLigandCount();

    long getContactingLigandCount();

    double getContactingLigandFraction();

    long getPoseCount();

    long getContactingPoseCount();

    double getContactingPoseFraction();

    Double getAvgContactingScore();

    Double getMedianContactingScore();

    Double getBestContactingScore();

    Double getWorstContactingScore();

    Double getClosestDistance();

    Double getAvgLigandMinDistance();

    Double getAvgPoseMinDistance();
}
