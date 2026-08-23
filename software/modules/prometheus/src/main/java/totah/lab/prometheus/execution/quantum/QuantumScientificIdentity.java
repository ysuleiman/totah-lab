package totah.lab.prometheus.execution.quantum;

import java.util.Comparator;
import java.util.Set;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.planning.CalculationSpecification;

/** Backend-neutral scientific identity for the Java quantum execution boundary. */
final class QuantumScientificIdentity {
    private QuantumScientificIdentity() { }

    static String calculate(CalculationSpecification specification, String atomMapHash,
            QuantumSolverMode solverMode, Set<QuantumObservable> observables) {
        StringBuilder value = new StringBuilder()
                .append("molecule=").append(specification.molecule().moleculeId())
                .append('\n').append("atomMap=").append(atomMapHash)
                .append('\n').append("geometry=").append(specification.geometry().sha256())
                .append('\n').append("atomCount=").append(specification.geometry().atomCount())
                .append('\n').append("charge=").append(specification.formalCharge())
                .append('\n').append("multiplicity=").append(specification.multiplicity())
                .append('\n').append("method=").append(specification.protocol().method())
                .append('\n').append("basis=").append(specification.protocol().basis())
                .append('\n').append("dispersion=").append(specification.protocol().dispersion())
                .append('\n').append("environment=").append(specification.protocol().environment())
                .append('\n').append("counterpoise=").append(specification.protocol().counterpoise())
                .append('\n').append("calculationType=").append(specification.calculationType())
                .append('\n').append("solverMode=").append(solverMode);
        specification.constraints().stream().sorted()
                .forEach(item -> value.append('\n').append("constraint=").append(item));
        specification.requiredOutputs().stream().sorted()
                .forEach(item -> value.append('\n').append("requiredOutput=").append(item));
        observables.stream().sorted(Comparator.comparing(Enum::name))
                .forEach(item -> value.append('\n').append("observable=").append(item));
        specification.acceptanceGates().stream().sorted()
                .forEach(item -> value.append('\n').append("gate=").append(item));
        return CanonicalHashing.sha256Hex(value.toString());
    }
}
