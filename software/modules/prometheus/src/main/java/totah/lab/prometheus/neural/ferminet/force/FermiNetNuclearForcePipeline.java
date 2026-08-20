package totah.lab.prometheus.neural.ferminet.force;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeConfiguration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeEngines;

/** The single canonical estimator-selection boundary. */
public final class FermiNetNuclearForcePipeline {

    private final Map<NuclearForceEstimatorType, FermiNetNuclearForceEstimator> estimators;

    public FermiNetNuclearForcePipeline() {
        this(Map.of(NuclearForceEstimatorType.CORRELATED_FD,
                new CorrelatedFdFermiNetForceEstimator(),
                NuclearForceEstimatorType.SWCT,
                new SwctFermiNetForceEstimator(),
                NuclearForceEstimatorType.AC_ZV,
                new AcZvFermiNetForceEstimator(),
                NuclearForceEstimatorType.AC_ZVZB,
                new AcZvzbFermiNetForceEstimator()));
    }

    FermiNetNuclearForcePipeline(
            Map<NuclearForceEstimatorType, FermiNetNuclearForceEstimator> estimators) {
        this.estimators = Map.copyOf(estimators);
    }

    public NuclearForceResult estimate(
            FermiNetForceEvaluationContext context,
            NuclearForceConfiguration configuration) throws IOException {
        return estimate(context, configuration,
                FermiNetDerivativeConfiguration.referenceJet());
    }

    public NuclearForceResult estimate(
            FermiNetForceEvaluationContext context,
            NuclearForceConfiguration configuration,
            FermiNetDerivativeConfiguration derivativeConfiguration)
            throws IOException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(configuration, "configuration");
        FermiNetNuclearForceEstimator estimator = estimators.get(
                configuration.estimatorType());
        if (estimator == null) {
            throw new UnsupportedOperationException(
                    "FermiNet force estimator is not implemented: "
                            + configuration.estimatorType());
        }
        return estimator.estimate(context, configuration,
                FermiNetDerivativeEngines.create(
                        Objects.requireNonNull(derivativeConfiguration,
                                "derivativeConfiguration")));
    }
}
