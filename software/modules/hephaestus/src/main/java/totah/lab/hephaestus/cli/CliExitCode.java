package totah.lab.hephaestus.cli;

public final class CliExitCode {
    public static final int SUCCESS = 0;
    public static final int VALIDATION_ERROR = 1;
    public static final int INVALID_ARGUMENTS = 2;
    public static final int IO_FAILURE = 3;
    public static final int PREPARATION_FAILURE = 4;
    public static final int EXPORT_FAILURE = 5;
    public static final int INTERNAL_FAILURE = 6;

    private CliExitCode() {
    }
}
