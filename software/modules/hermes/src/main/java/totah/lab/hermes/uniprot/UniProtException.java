package totah.lab.hermes.uniprot;

import java.io.IOException;

/** Checked failure returned by the UniProt integration. */
public final class UniProtException extends IOException {

    public UniProtException(String message) {
        super(message);
    }

    public UniProtException(String message, Throwable cause) {
        super(message, cause);
    }
}
