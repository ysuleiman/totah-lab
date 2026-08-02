package totah.lab.hephaestus.export;

import totah.lab.hermes.file.writer.pdbqt.PdbqtWriteOptions;

import java.nio.file.Path;
import java.util.Objects;

public record ReceptorPdbqtExportOptions(
        Path outputPath,
        Path flexibleOutputPath,
        PdbqtWriteOptions writeOptions) {

    public ReceptorPdbqtExportOptions {
        outputPath = Objects.requireNonNull(
                outputPath, "outputPath").toAbsolutePath().normalize();
        flexibleOutputPath = flexibleOutputPath == null
                ? outputPath.resolveSibling("prepared_flex.pdbqt")
                : flexibleOutputPath.toAbsolutePath().normalize();
        writeOptions = writeOptions == null
                ? PdbqtWriteOptions.defaults()
                : writeOptions;
    }

    public ReceptorPdbqtExportOptions(Path outputPath) {
        this(outputPath, null, PdbqtWriteOptions.defaults());
    }

    public ReceptorPdbqtExportOptions(
            Path outputPath, PdbqtWriteOptions writeOptions) {
        this(outputPath, null, writeOptions);
    }
}
