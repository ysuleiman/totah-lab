package totah.lab.http.biohub.artifact;

import totah.lab.http.biohub.model.ResidueConstraintAnalysis;

record ResidueConstraintArtifact(
        String schemaVersion,
        String analysisType,
        ResidueConstraintAnalysis analysis
) {

    static final String SCHEMA_VERSION = "1.0";
    static final String ANALYSIS_TYPE = "ESMC_RESIDUE_CONSTRAINT";

    static ResidueConstraintArtifact from(
            ResidueConstraintAnalysis analysis
    ) {
        return new ResidueConstraintArtifact(
                SCHEMA_VERSION,
                ANALYSIS_TYPE,
                analysis
        );
    }
}
