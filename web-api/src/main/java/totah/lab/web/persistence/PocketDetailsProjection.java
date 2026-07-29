package totah.lab.web.persistence;

public interface PocketDetailsProjection {

    Long getId();

    Integer getPocketNumber();

    String getSource();

    Double getVolume();

    Double getDruggabilityScore();

    Long getStructureId();

    String getStructureSource();

    String getStructureAccession();

    String getChain();

    Integer getModelNumber();

    Long getReceptorId();

    String getTargetName();

    Long getArtifactId();

    String getArtifactFilename();

    String getArtifactLabel();

    String getArtifactStorageLocation();
}
