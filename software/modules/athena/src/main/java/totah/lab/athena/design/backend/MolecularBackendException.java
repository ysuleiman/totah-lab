package totah.lab.athena.design.backend;

/** Checked failure: unsupported or invalid chemistry may not silently fall back. */
public final class MolecularBackendException extends Exception {
    public MolecularBackendException(String message) { super(message); }
    public MolecularBackendException(String message, Throwable cause) { super(message, cause); }
}
