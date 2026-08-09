package totah.lab.hermes.file.pdbqt.writer;

import org.junit.jupiter.api.Test;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtFile;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.pdbqt.PdbqtWriteOptions;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PdbqtRoundTripTest {

    @Test
    void preservesVinaModelsAtomsRemarksAndTorsionTopology() throws Exception {
        Path source = Path.of(getClass()
                .getResource("/vina/dcmb-diffdock-vina_out.pdbqt").toURI());
        PdbqtFile before = new PdbqtReader().read(source);
        StringWriter output = new StringWriter();

        new PdbqtWriter().write(before, output, PdbqtWriteOptions.defaults());
        PdbqtFile after = new PdbqtReader().read(new StringReader(output.toString()));

        assertEquals(before.models().size(), after.models().size());
        for (int index = 0; index < before.models().size(); index++) {
            assertEquivalent(before.models().get(index), after.models().get(index));
        }
    }

    private void assertEquivalent(PdbqtModel expected, PdbqtModel actual) {
        assertEquals(expected.modelNumber(), actual.modelNumber());
        assertEquals(expected.remarks(), actual.remarks());
        assertEquals(expected.torsdof(), actual.torsdof());
        assertEquals(expected.rotatableBondSerials().stream()
                        .map(pair -> List.of(pair[0], pair[1])).toList(),
                actual.rotatableBondSerials().stream()
                        .map(pair -> List.of(pair[0], pair[1])).toList());
        assertEquals(expected.atoms().size(), actual.atoms().size());
        for (int index = 0; index < expected.atoms().size(); index++) {
            assertEquivalent(expected.atoms().get(index), actual.atoms().get(index));
        }
    }

    private void assertEquivalent(PdbqtAtom expected, PdbqtAtom actual) {
        assertEquals(expected.recordType(), actual.recordType());
        assertEquals(expected.serial(), actual.serial());
        assertEquals(expected.atomName(), actual.atomName());
        assertEquals(expected.residueName(), actual.residueName());
        assertEquals(expected.chainId(), actual.chainId());
        assertEquals(expected.residueNumber(), actual.residueNumber());
        assertEquals(expected.insertionCode(), actual.insertionCode());
        assertEquals(expected.x(), actual.x(), 0.0005);
        assertEquals(expected.y(), actual.y(), 0.0005);
        assertEquals(expected.z(), actual.z(), 0.0005);
        assertEquals(expected.partialCharge(), actual.partialCharge(), 0.00005);
        assertEquals(expected.autodockType(), actual.autodockType());
    }
}
