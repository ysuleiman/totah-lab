package totah.lab.prometheus.potential.delta.basis;

import totah.lab.prometheus.potential.QuantumCoordinates;

public interface ManyBodyBasis { int dimension(); BasisEvaluation evaluate(QuantumCoordinates coordinates); }
