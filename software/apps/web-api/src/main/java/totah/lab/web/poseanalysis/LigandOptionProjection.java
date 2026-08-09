package totah.lab.web.poseanalysis;

public interface LigandOptionProjection {

    String getLigandId();

    String getLabel();

    String getSmiles();

    Long getRunCount();

    Long getPoseCount();

    Double getBestScore();
}
