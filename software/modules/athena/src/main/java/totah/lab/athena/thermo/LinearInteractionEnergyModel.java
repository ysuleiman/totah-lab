package totah.lab.athena.thermo;

import totah.lab.athena.energy.ScientificMethod;

/** Explicitly empirical LIE model with no parameter defaults. */
public record LinearInteractionEnergyModel(
        double alpha, double beta, MolarEnergy gamma, String parameterProvenance) {
    public LinearInteractionEnergyModel {
        if (!Double.isFinite(alpha) || !Double.isFinite(beta)) {
            throw new IllegalArgumentException("alpha and beta must be finite");
        }
        java.util.Objects.requireNonNull(gamma, "gamma");
        if (parameterProvenance == null || parameterProvenance.isBlank()) {
            throw new IllegalArgumentException("parameter provenance is required");
        }
    }

    public Result evaluate(MolarEnergy deltaVdw, MolarEnergy deltaElectrostatic) {
        EnergyUnit unit = gamma.unit();
        double value = alpha * deltaVdw.in(unit).value()
                + beta * deltaElectrostatic.in(unit).value() + gamma.value();
        return new Result(new MolarEnergy(value, unit),
                ScientificMethod.LIE_EMPIRICAL, parameterProvenance);
    }

    public record Result(MolarEnergy estimate, ScientificMethod method,
            String parameterProvenance) { }
}
