package totah.lab.web.poseanalysis;

/**
 * The structure artifact of one docking structure (the model the
 * pocket rows were generated from), for coordinate-frame provenance.
 */
public interface StructureArtifactProjection {

    Long getStructureId();

    Long getArtifactId();

    String getArtifactFilename();

    String getArtifactStorageLocation();

    String getStructureSource();

    String getSourceAccession();
}
