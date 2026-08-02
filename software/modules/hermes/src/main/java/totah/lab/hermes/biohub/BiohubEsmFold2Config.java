package totah.lab.hermes.biohub;

public record BiohubEsmFold2Config(
        int numSamplingSteps,
        int numLoops,
        double lmDropout,
        double msaColumnMaskRate
) {

    public BiohubEsmFold2Config {
        if (numSamplingSteps < 1) {
            throw new IllegalArgumentException(
                    "numSamplingSteps must be positive"
            );
        }
        if (numLoops < 1) {
            throw new IllegalArgumentException("numLoops must be positive");
        }
        requireProbability(lmDropout, "lmDropout");
        requireProbability(msaColumnMaskRate, "msaColumnMaskRate");
    }

    public static BiohubEsmFold2Config quality() {
        return new BiohubEsmFold2Config(100, 20, 0.3, 0.1);
    }

    public static BiohubEsmFold2Config fast() {
        return new BiohubEsmFold2Config(32, 3, 0.3, 0.1);
    }

    private static void requireProbability(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    name + " must be between zero and one"
            );
        }
    }
}
