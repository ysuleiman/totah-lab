package totah.lab.web.poseanalysis;

public interface PoseRunProjection {

    Long getId();

    Long getReceptorId();

    Long getStructureId();

    String getTargetName();

    String getUniProtId();

    String getMethod();

    String getReceptorArtifactId();

    Long getPoseCount();

    Double getBestScore();
}
