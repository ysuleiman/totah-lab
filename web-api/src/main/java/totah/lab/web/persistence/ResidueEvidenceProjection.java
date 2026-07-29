package totah.lab.web.persistence;

public interface ResidueEvidenceProjection {

    long getResidueId();

    String getAnalysisType();

    Double getScore();

    Integer getRank();

    String getProvider();

    String getModel();

    String getBestAlternative();

    Double getWildTypeMinusBestAlternative();

    Double getAminoAcidEntropy();

    long getArtifactId();
}
