package totah.lab.hermes.structure;

import java.nio.file.Path;

public record StructureReaderOptions(
        boolean onlineCcdLookup,
        Path ccdCacheDirectory) {

    public static StructureReaderOptions defaults() {
        return new StructureReaderOptions(
                false,
                null);
    }
}