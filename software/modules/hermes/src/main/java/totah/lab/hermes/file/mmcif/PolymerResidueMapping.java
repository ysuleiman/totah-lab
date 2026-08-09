package totah.lab.hermes.file.mmcif;

/**
 * Source-derived correspondence between one polymer sequence position in an
 * assembly and a UniProt position reported by the entry mmCIF alignment.
 */
public record PolymerResidueMapping(
        String entityId,
        String labelAsymId,
        String authAsymId,
        int labelSequenceId,
        String authSequenceId,
        ResidueNumberSource residueNumberSource,
        String insertionCode,
        String structureResidueName,
        String uniProtAccession,
        int uniProtPosition,
        String uniProtResidueName,
        CoordinateStatus coordinateStatus,
        SequenceRelation sequenceRelation,
        String differenceDetails) {

    public enum CoordinateStatus {
        RESOLVED,
        UNRESOLVED
    }

    public enum ResidueNumberSource {
        AUTH_SEQ_NUM,
        PDB_SEQ_NUM
    }

    public enum SequenceRelation {
        MATCH,
        SUBSTITUTION,
        MODIFIED,
        UNKNOWN
    }
}
