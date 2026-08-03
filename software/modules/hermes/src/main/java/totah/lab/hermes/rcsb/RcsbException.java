package totah.lab.hermes.rcsb;

import java.io.IOException;

/** Checked failure returned by the RCSB integration. */
public final class RcsbException extends IOException {

    public RcsbException(String message) {
        super(message);
    }

    public RcsbException(String message, Throwable cause) {
        super(message, cause);
    }
}
