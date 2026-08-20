package totah.lab.prometheus.neural.ferminet.force;

import java.io.IOException;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeEngine;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeConfiguration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeEngines;

/** One pluggable estimator contract behind the canonical force dispatcher. */
public interface FermiNetNuclearForceEstimator {
    default NuclearForceResult estimate(
            FermiNetForceEvaluationContext context,
            NuclearForceConfiguration configuration) throws IOException {
        return estimate(context, configuration,
                FermiNetDerivativeEngines.create(
                        FermiNetDerivativeConfiguration.referenceJet()));
    }

    NuclearForceResult estimate(
            FermiNetForceEvaluationContext context,
            NuclearForceConfiguration configuration,
            FermiNetDerivativeEngine derivativeEngine) throws IOException;
}
