package totah.lab.hermes.structure;


import java.util.Objects;

/**
 * Checked failure produced while resolving a protein structure.
 */
public final class StructureResolutionException extends Exception {

    private final StructureResolutionFailure failure;
    private final String requestedAccession;

    public StructureResolutionException(
            StructureResolutionFailure failure,
            String requestedAccession,
            String message
    ) {
        super(message);

        this.failure = Objects.requireNonNull(failure, "failure");
        this.requestedAccession = Objects.requireNonNull(
                requestedAccession,
                "requestedAccession"
        );
    }

    public StructureResolutionException(
            StructureResolutionFailure failure,
            String requestedAccession,
            String message,
            Throwable cause
    ) {
        super(message, cause);

        this.failure = Objects.requireNonNull(failure, "failure");
        this.requestedAccession = Objects.requireNonNull(
                requestedAccession,
                "requestedAccession"
        );
    }

    public StructureResolutionFailure failure() {
        return failure;
    }

    public String requestedAccession() {
        return requestedAccession;
    }
}