package totah.lab.gaia.structure;

import java.util.List;
import java.util.Objects;

/** Connectivity provenance and non-silent import diagnostics. */
public record ConnectivityMetadata(
        ConnectivityProvenance provenance,
        List<String> diagnostics) {

    public static final ConnectivityMetadata ABSENT =
            new ConnectivityMetadata(ConnectivityProvenance.ABSENT, List.of());

    public ConnectivityMetadata {
        Objects.requireNonNull(provenance, "provenance");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}
