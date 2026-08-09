package totah.lab.hermes.file.pdbqt;

public record PdbqtWriteOptions(
        boolean writeChainTerminators,
        boolean writeEndRecord) {

    public static PdbqtWriteOptions defaults() {
        return new PdbqtWriteOptions(false, false);
    }
}
