package totah.lab.prometheus.molecular;

import java.util.Objects;
import java.util.Optional;
import totah.lab.prometheus.variational.DifferentiableStateEvaluation;

/** One shared molecular state traversal: amplitude representation, derivatives, features, and optional energy. */
public record MolecularStateEvaluation(double logAbsoluteWavefunction,int sign,DifferentiableStateEvaluation derivatives,MolecularFeatureBundle features,Optional<LocalEnergyComponents> localEnergy){public MolecularStateEvaluation{if(!Double.isFinite(logAbsoluteWavefunction)||Math.abs(sign)!=1)throw new IllegalArgumentException("invalid real amplitude representation");Objects.requireNonNull(derivatives);Objects.requireNonNull(features);localEnergy=Objects.requireNonNull(localEnergy);}}
