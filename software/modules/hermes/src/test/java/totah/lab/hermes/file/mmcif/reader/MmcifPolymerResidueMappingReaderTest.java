package totah.lab.hermes.file.mmcif.reader;

import org.junit.jupiter.api.Test;
import totah.lab.hermes.file.mmcif.PolymerResidueMapping;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MmcifPolymerResidueMappingReaderTest {
    private final MmcifPolymerResidueMappingReader reader =
            new MmcifPolymerResidueMappingReader();

    @Test
    void mapsAlignmentRangeAndPreservesMutationAndMissingCoordinates()
            throws Exception {
        var mappings = reader.read(resource("residue-mapping-entry.cif"),
                resource("residue-mapping-assembly.cif"));

        assertEquals(3, mappings.size());
        assertEquals(10, mappings.getFirst().uniProtPosition());
        assertEquals(PolymerResidueMapping.SequenceRelation.MATCH,
                mappings.getFirst().sequenceRelation());
        assertEquals(11, mappings.get(1).uniProtPosition());
        assertEquals("GLY", mappings.get(1).uniProtResidueName());
        assertEquals(PolymerResidueMapping.SequenceRelation.SUBSTITUTION,
                mappings.get(1).sequenceRelation());
        assertEquals(PolymerResidueMapping.CoordinateStatus.UNRESOLVED,
                mappings.get(2).coordinateStatus());
        assertEquals(PolymerResidueMapping.ResidueNumberSource.PDB_SEQ_NUM,
                mappings.get(2).residueNumberSource());
    }

    private static Path resource(String name) throws Exception {
        return Path.of(MmcifPolymerResidueMappingReaderTest.class
                .getResource("/mmcif/" + name).toURI());
    }
}
