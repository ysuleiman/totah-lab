package totah.lab.athena.thermo;

import totah.lab.athena.energy.ScientificMethod;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Trapezoidal integration of externally generated lambda observables. */
public final class ThermodynamicIntegrationEstimator {
    private ThermodynamicIntegrationEstimator() { }

    public static Result integrate(List<LambdaObservation> observations,
            String ensembleProvenance) {
        observations = Objects.requireNonNull(observations, "observations").stream()
                .sorted(Comparator.comparingDouble(LambdaObservation::lambda)).toList();
        if (observations.size() < 2 || observations.getFirst().lambda() != 0.0
                || observations.getLast().lambda() != 1.0) {
            throw new IllegalArgumentException("TI observations must span lambda 0 to 1");
        }
        if (ensembleProvenance == null || ensembleProvenance.isBlank()) {
            throw new IllegalArgumentException("ensemble provenance is required");
        }
        EnergyUnit unit = observations.getFirst().derivative().unit();
        double integral = 0.0;
        for (int i = 1; i < observations.size(); i++) {
            LambdaObservation left = observations.get(i - 1);
            LambdaObservation right = observations.get(i);
            if (right.lambda() <= left.lambda()) {
                throw new IllegalArgumentException(
                        "TI lambda observations must be unique");
            }
            integral += (right.lambda() - left.lambda()) * 0.5
                    * (left.derivative().in(unit).value()
                    + right.derivative().in(unit).value());
        }
        return new Result(new MolarEnergy(integral, unit),
                ScientificMethod.TI_ESTIMATE, ensembleProvenance.trim());
    }

    public record LambdaObservation(double lambda, MolarEnergy derivative) {
        public LambdaObservation {
            if (!Double.isFinite(lambda) || lambda < 0.0 || lambda > 1.0) {
                throw new IllegalArgumentException("lambda must be in [0,1]");
            }
            Objects.requireNonNull(derivative, "derivative");
        }
    }

    public record Result(MolarEnergy deltaFreeEnergy, ScientificMethod method,
            String ensembleProvenance) { }
}
