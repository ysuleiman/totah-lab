package totah.lab.prometheus.neural.ferminet.force;

import java.io.IOException;

/** One pluggable estimator contract behind the canonical force dispatcher. */
public interface FermiNetNuclearForceEstimator {
    NuclearForceResult estimate(
            FermiNetForceEvaluationContext context,
            NuclearForceConfiguration configuration) throws IOException;
}
