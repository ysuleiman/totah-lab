package totah.lab.mettl7.triage;

public record ExperimentalFeatures(
        boolean productiveTurnoverEstablished,
        boolean mettl7aSelectiveEstablished,
        boolean mettl7bSelectiveEstablished,
        boolean mettl7bCompatibleOnly,
        boolean directBindingEstablished) {
    public static ExperimentalFeatures none() {
        return new ExperimentalFeatures(false, false, false, false, false);
    }
}
