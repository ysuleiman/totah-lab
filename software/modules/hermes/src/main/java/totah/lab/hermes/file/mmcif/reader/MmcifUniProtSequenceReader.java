package totah.lab.hermes.file.mmcif.reader;

import org.rcsb.cif.CifIO;
import org.rcsb.cif.schema.StandardSchemata;
import org.rcsb.cif.schema.mm.StructRef;
import totah.lab.hermes.file.mmcif.UniProtSequenceReference;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Reads source-reported UniProt sequence references from mmCIF struct_ref. */
public final class MmcifUniProtSequenceReader {
    public List<UniProtSequenceReference> read(Path entryMmcif)
            throws IOException {
        Objects.requireNonNull(entryMmcif);
        StructRef references = CifIO.readFromPath(entryMmcif)
                .as(StandardSchemata.MMCIF).getBlocks().getFirst()
                .getStructRef();
        List<UniProtSequenceReference> result = new ArrayList<>();
        for (int row = 0; row < references.getRowCount(); row++) {
            if (!"UNP".equalsIgnoreCase(references.getDbName().get(row))) {
                continue;
            }
            String sequence = references.getPdbxSeqOneLetterCode().get(row)
                    .replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            if (!sequence.isEmpty() && sequence.chars()
                    .allMatch(character -> character >= 'A' && character <= 'Z')) {
                result.add(new UniProtSequenceReference(
                        references.getPdbxDbAccession().get(row),
                        references.getDbCode().get(row), sequence));
            }
        }
        return List.copyOf(result);
    }
}
