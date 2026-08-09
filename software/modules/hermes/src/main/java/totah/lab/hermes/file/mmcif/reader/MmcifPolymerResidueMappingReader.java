package totah.lab.hermes.file.mmcif.reader;

import org.rcsb.cif.CifIO;
import org.rcsb.cif.model.StrColumn;
import org.rcsb.cif.model.ValueKind;
import org.rcsb.cif.schema.StandardSchemata;
import org.rcsb.cif.schema.mm.MmCifBlock;
import org.rcsb.cif.schema.mm.PdbxPolySeqScheme;
import org.rcsb.cif.schema.mm.StructRef;
import org.rcsb.cif.schema.mm.StructRefSeq;
import org.rcsb.cif.schema.mm.StructRefSeqDif;
import totah.lab.hermes.file.mmcif.PolymerResidueMapping;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads residue-level UniProt correspondence from RCSB mmCIF categories. */
public final class MmcifPolymerResidueMappingReader {

    public List<PolymerResidueMapping> read(Path entryMmcif,
            Path assemblyMmcif) throws IOException {
        Objects.requireNonNull(entryMmcif);
        Objects.requireNonNull(assemblyMmcif);
        MmCifBlock entry = block(entryMmcif);
        MmCifBlock assembly = block(assemblyMmcif);
        List<Alignment> alignments = alignments(entry);
        Map<DifferenceKey, Difference> differences = differences(entry);
        PdbxPolySeqScheme scheme = assembly.getPdbxPolySeqScheme();
        List<PolymerResidueMapping> result = new ArrayList<>();
        for (int row = 0; row < scheme.getRowCount(); row++) {
            int sequenceId = scheme.getSeqId().get(row);
            String entityId = scheme.getEntityId().get(row);
            String authChain = normalized(scheme.getPdbStrandId(), row);
            for (Alignment alignment : alignments) {
                if (!alignment.entityId().equals(entityId)
                        || !alignment.chains().contains(authChain)
                        || sequenceId < alignment.sequenceBegin()
                        || sequenceId > alignment.sequenceEnd()) {
                    continue;
                }
                int uniProtPosition = alignment.databaseBegin()
                        + sequenceId - alignment.sequenceBegin();
                Difference difference = differences.get(new DifferenceKey(
                        alignment.alignmentId(), sequenceId));
                String structureResidue = normalized(scheme.getMonId(), row);
                String uniProtResidue = difference == null ? structureResidue
                        : difference.databaseResidue();
                result.add(new PolymerResidueMapping(entityId,
                        normalized(scheme.getAsymId(), row), authChain,
                        sequenceId, residueNumber(scheme, row),
                        residueNumberSource(scheme, row),
                        normalized(scheme.getPdbInsCode(), row),
                        structureResidue, alignment.accession(), uniProtPosition,
                        uniProtResidue, coordinateStatus(scheme, row),
                        relation(structureResidue, uniProtResidue, difference),
                        difference == null ? null : difference.details()));
            }
        }
        return List.copyOf(result);
    }

    private static MmCifBlock block(Path path) throws IOException {
        return CifIO.readFromPath(path).as(StandardSchemata.MMCIF)
                .getBlocks().getFirst();
    }

    private static List<Alignment> alignments(MmCifBlock block) {
        StructRef refs = block.getStructRef();
        Map<String, String> entityByRef = new HashMap<>();
        Map<String, String> accessionByRef = new HashMap<>();
        for (int row = 0; row < refs.getRowCount(); row++) {
            if ("UNP".equalsIgnoreCase(refs.getDbName().get(row))) {
                entityByRef.put(refs.getId().get(row),
                        refs.getEntityId().get(row));
                accessionByRef.put(refs.getId().get(row),
                        refs.getPdbxDbAccession().get(row));
            }
        }
        StructRefSeq sequences = block.getStructRefSeq();
        List<Alignment> result = new ArrayList<>();
        for (int row = 0; row < sequences.getRowCount(); row++) {
            String refId = sequences.getRefId().get(row);
            String entity = entityByRef.get(refId);
            if (entity == null) continue;
            String accession = value(sequences.getPdbxDbAccession(), row);
            if (accession == null) accession = accessionByRef.get(refId);
            result.add(new Alignment(sequences.getAlignId().get(row), entity,
                    accession, chains(sequences.getPdbxStrandId().get(row)),
                    sequences.getSeqAlignBeg().get(row),
                    sequences.getSeqAlignEnd().get(row),
                    sequences.getDbAlignBeg().get(row)));
        }
        return result;
    }

    private static Map<DifferenceKey, Difference> differences(MmCifBlock block) {
        StructRefSeqDif values = block.getStructRefSeqDif();
        Map<DifferenceKey, Difference> result = new LinkedHashMap<>();
        for (int row = 0; row < values.getRowCount(); row++) {
            result.put(new DifferenceKey(values.getAlignId().get(row),
                    values.getSeqNum().get(row)), new Difference(
                    normalized(values.getDbMonId(), row),
                    normalized(values.getDetails(), row)));
        }
        return result;
    }

    private static PolymerResidueMapping.CoordinateStatus coordinateStatus(
            PdbxPolySeqScheme scheme, int row) {
        return value(scheme.getPdbSeqNum(), row) == null
                || value(scheme.getPdbMonId(), row) == null
                ? PolymerResidueMapping.CoordinateStatus.UNRESOLVED
                : PolymerResidueMapping.CoordinateStatus.RESOLVED;
    }

    private static String residueNumber(PdbxPolySeqScheme scheme, int row) {
        String author = value(scheme.getAuthSeqNum(), row);
        return author == null ? normalized(scheme.getPdbSeqNum(), row) : author;
    }

    private static PolymerResidueMapping.ResidueNumberSource
            residueNumberSource(PdbxPolySeqScheme scheme, int row) {
        return value(scheme.getAuthSeqNum(), row) == null
                ? PolymerResidueMapping.ResidueNumberSource.PDB_SEQ_NUM
                : PolymerResidueMapping.ResidueNumberSource.AUTH_SEQ_NUM;
    }

    private static PolymerResidueMapping.SequenceRelation relation(
            String structureResidue, String uniProtResidue,
            Difference difference) {
        if (difference == null) {
            return PolymerResidueMapping.SequenceRelation.MATCH;
        }
        if (uniProtResidue == null) {
            return PolymerResidueMapping.SequenceRelation.UNKNOWN;
        }
        if (!uniProtResidue.equalsIgnoreCase(structureResidue)) {
            return PolymerResidueMapping.SequenceRelation.SUBSTITUTION;
        }
        return PolymerResidueMapping.SequenceRelation.MODIFIED;
    }

    private static List<String> chains(String value) {
        List<String> result = new ArrayList<>();
        for (String chain : value.split(",")) {
            if (!chain.isBlank()) result.add(chain.trim());
        }
        return List.copyOf(result);
    }

    private static String normalized(StrColumn column, int row) {
        String value = value(column, row);
        return value == null ? "" : value;
    }

    private static String value(StrColumn column, int row) {
        if (!column.isDefined()
                || column.getValueKind(row) != ValueKind.PRESENT) return null;
        String value = column.get(row);
        return value == null || value.isBlank() || value.equals(".")
                || value.equals("?") ? null : value;
    }

    private record Alignment(String alignmentId, String entityId,
            String accession, List<String> chains, int sequenceBegin,
            int sequenceEnd, int databaseBegin) {}

    private record DifferenceKey(String alignmentId, int sequenceId) {}

    private record Difference(String databaseResidue, String details) {}
}
