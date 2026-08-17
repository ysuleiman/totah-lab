package totah.lab.athena.tmt;

import java.util.List;

/** Comparison of classical and reference torsion energies on one explicit angle grid. */
public record TorsionProfileAssessment(
        boolean evaluated,
        List<Double> anglesDegrees,
        List<Double> referenceRelativeEnergies,
        List<Double> classicalRelativeEnergies,
        double rmse,
        double maximumAbsoluteError,
        boolean withinTolerance,
        String reason) {

    public TorsionProfileAssessment {
        anglesDegrees = List.copyOf(anglesDegrees);
        referenceRelativeEnergies = List.copyOf(referenceRelativeEnergies);
        classicalRelativeEnergies = List.copyOf(classicalRelativeEnergies);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (evaluated) {
            int size = anglesDegrees.size();
            if (size < 2 || referenceRelativeEnergies.size() != size || classicalRelativeEnergies.size() != size) {
                throw new IllegalArgumentException("evaluated profiles require matched grids with at least two points");
            }
            if (!allFinite(anglesDegrees) || !allFinite(referenceRelativeEnergies)
                    || !allFinite(classicalRelativeEnergies) || !Double.isFinite(rmse)
                    || !Double.isFinite(maximumAbsoluteError)) {
                throw new IllegalArgumentException("evaluated profile values must be finite");
            }
        } else if (!anglesDegrees.isEmpty() || !referenceRelativeEnergies.isEmpty()
                || !classicalRelativeEnergies.isEmpty() || withinTolerance) {
            throw new IllegalArgumentException("unevaluated profiles must contain no invented curve or pass result");
        }
    }

    public static TorsionProfileAssessment compare(
            List<Double> angles, List<Double> reference, List<Double> classical,
            double rmseTolerance, double maximumErrorTolerance) {
        if (!Double.isFinite(rmseTolerance) || rmseTolerance < 0.0
                || !Double.isFinite(maximumErrorTolerance) || maximumErrorTolerance < 0.0) {
            throw new IllegalArgumentException("tolerances must be finite and non-negative");
        }
        if (angles.size() < 2 || reference.size() != angles.size() || classical.size() != angles.size()) {
            throw new IllegalArgumentException("profiles require matched grids with at least two points");
        }
        double squaredError = 0.0;
        double maximumError = 0.0;
        for (int index = 0; index < angles.size(); index++) {
            double error = classical.get(index) - reference.get(index);
            if (!Double.isFinite(angles.get(index)) || !Double.isFinite(error)) {
                throw new IllegalArgumentException("profile values must be finite");
            }
            squaredError += error * error;
            maximumError = Math.max(maximumError, Math.abs(error));
        }
        double rmse = Math.sqrt(squaredError / angles.size());
        boolean passes = rmse <= rmseTolerance && maximumError <= maximumErrorTolerance;
        return new TorsionProfileAssessment(true, angles, reference, classical, rmse, maximumError,
                passes, passes ? "PROFILE_WITHIN_TOLERANCE" : "PROFILE_OUTSIDE_TOLERANCE");
    }

    public static TorsionProfileAssessment unevaluated(String reason) {
        return new TorsionProfileAssessment(false, List.of(), List.of(), List.of(),
                Double.NaN, Double.NaN, false, reason);
    }

    private static boolean allFinite(List<Double> values) {
        return values.stream().allMatch(value -> value != null && Double.isFinite(value));
    }
}
