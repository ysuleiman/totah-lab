package totah.lab.hermes.file.pdbqt;

import java.io.IOException;

public final class PdbqtFormatException
        extends IOException {

    public PdbqtFormatException(String message) {
        super(message);
    }

    public PdbqtFormatException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
