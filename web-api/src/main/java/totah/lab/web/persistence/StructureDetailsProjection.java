package totah.lab.web.persistence;

public interface StructureDetailsProjection {

    Long getId();

    String getSource();

    String getSourceAccession();

    String getChain();

    Integer getModelNumber();

    String getPreparationState();

    Long getParentStructureId();

    Long getReceptorId();

    String getTargetName();

    Long getArtifactId();

    String getArtifactFilename();

    String getArtifactLabel();

    String getArtifactStorageLocation();
}
