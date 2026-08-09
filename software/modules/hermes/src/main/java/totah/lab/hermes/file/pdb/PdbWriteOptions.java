package totah.lab.hermes.file.pdb;

public record PdbWriteOptions(
        boolean writeChainTerminators,
        boolean writeEndRecord) {

    /**
     * Standard protein PDB: a TER record after every chain and a
     * trailing END record.
     */
    public static PdbWriteOptions defaults() {
        return new PdbWriteOptions(true, true);
    }
}
