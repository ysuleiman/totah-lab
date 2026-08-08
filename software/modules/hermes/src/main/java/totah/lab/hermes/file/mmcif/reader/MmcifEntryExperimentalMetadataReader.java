package totah.lab.hermes.file.mmcif.reader;

import org.rcsb.cif.CifIO;
import org.rcsb.cif.schema.StandardSchemata;
import org.rcsb.cif.schema.mm.MmCifBlock;
import totah.lab.hermes.file.mmcif.EntryExperimentalMetadata;

import java.io.IOException;
import java.nio.file.Path;

/** Reads source-reported method and diffraction resolution. */
public final class MmcifEntryExperimentalMetadataReader {
    public EntryExperimentalMetadata read(Path path) throws IOException {
        MmCifBlock block = CifIO.readFromPath(path).as(StandardSchemata.MMCIF)
                .getBlocks().getFirst();
        String method = block.getExptl().getRowCount() == 0 ? null
                : block.getExptl().getMethod().get(0);
        Double resolution = block.getRefine().getRowCount() == 0
                || !block.getRefine().getLsDResHigh().isDefined() ? null
                : block.getRefine().getLsDResHigh().get(0);
        return new EntryExperimentalMetadata(method, resolution);
    }
}
