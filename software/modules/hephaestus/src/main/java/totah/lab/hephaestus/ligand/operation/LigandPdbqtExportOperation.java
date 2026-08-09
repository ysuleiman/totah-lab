package totah.lab.hephaestus.ligand.operation;

import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hermes.file.pdbqt.writer.PdbqtLigandWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Hephaestus-to-Hermes boundary for prepared-ligand PDBQT export. */
public final class LigandPdbqtExportOperation {
    private final PdbqtLigandWriter serializer;
    private final PdbqtLigandAdapter adapter;

    public LigandPdbqtExportOperation() {
        this(new PdbqtLigandWriter(), new PdbqtLigandAdapter());
    }

    LigandPdbqtExportOperation(
            PdbqtLigandWriter serializer, PdbqtLigandAdapter adapter) {
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
    }

    public Path export(PreparedLigand preparedLigand, Path output) throws IOException {
        Objects.requireNonNull(preparedLigand, "preparedLigand");
        Objects.requireNonNull(output, "output");
        serializer.write(adapter.adapt(preparedLigand), output);
        return output;
    }
}
