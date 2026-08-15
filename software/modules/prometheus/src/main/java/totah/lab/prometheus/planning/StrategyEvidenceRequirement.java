package totah.lab.prometheus.planning;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Rich, strategy-level evidence contract layered over a frozen scientific requirement. */
public record StrategyEvidenceRequirement(
        String requirementId,
        ScientificEvidenceRequirement scientific,
        boolean exactProtocolRequired,
        boolean scientificallyRequired,
        Optional<DerivationRule> derivation,
        List<String> functionalFormAssumptions,
        List<String> outputsEnabled,
        List<String> externalDependencies,
        List<String> requiredMetadata) {

    public StrategyEvidenceRequirement {
        requirementId = require(requirementId, "requirementId");
        Objects.requireNonNull(scientific, "scientific");
        derivation = Objects.requireNonNull(derivation, "derivation");
        functionalFormAssumptions = copy(functionalFormAssumptions, "functionalFormAssumptions");
        outputsEnabled = copy(outputsEnabled, "outputsEnabled");
        externalDependencies = copy(externalDependencies, "externalDependencies");
        requiredMetadata = copy(requiredMetadata, "requiredMetadata");
    }

    /** Stable key used to eliminate duplicate scientific requests within and across strategy stages. */
    public String scientificKey() {
        EvidenceRequirement r = scientific.requirement();
        return r.calculationType() + "|" + r.molecule().moleculeId() + "|"
                + (r.geometry() == null ? "none" : r.geometry().sha256()) + "|"
                + scientific.formalCharge() + "|" + scientific.multiplicity() + "|"
                + r.protocol().protocolKey() + "|" + scientific.constraints() + "|"
                + scientific.requestedOutputs() + "|" + r.role();
    }

    private static List<String> copy(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        if (values.stream().anyMatch(v -> v == null || v.isBlank())) {
            throw new IllegalArgumentException(name + " entries must be non-blank");
        }
        return List.copyOf(values);
    }

    private static String require(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must be non-blank");
        return value;
    }
}
