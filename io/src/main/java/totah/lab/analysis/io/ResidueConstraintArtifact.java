package totah.lab.analysis.io;

import totah.lab.protein.analysis.ResidueConstraintAnalysis;

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
