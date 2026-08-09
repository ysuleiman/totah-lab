package totah.lab.hermes.file.pdb;

/**
 * Summary of one written PDB file.
 */
public record PdbWriteResult(
        java.nio.file.Path output,
        int atomCount) {
}
