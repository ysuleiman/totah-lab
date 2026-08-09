package totah.lab.web.poseanalysis;

public interface LigandRunOptionProjection {

    String getLigandId();

    String getLabel();

    String getSmiles();

    Long getRunId();

    String getMethod();

    Long getPoseCount();

    Double getBestScore();
}
