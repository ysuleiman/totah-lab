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

    String getUniProtId();

    String getProteinName();

    String getGeneName();

    String getOrganism();

    Long getArtifactId();

    String getArtifactFilename();

    String getArtifactLabel();

    String getArtifactStorageLocation();

    Long getChosenPocketId();

    Integer getChosenPocketNumber();

    String getChosenPocketSource();
}
