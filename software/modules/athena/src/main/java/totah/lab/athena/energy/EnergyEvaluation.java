package totah.lab.athena.energy;

import totah.lab.athena.thermo.EnergyUnit;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Engine result for one state. It is explicitly not a binding free energy. */
public record EnergyEvaluation(
        String stateId,
        Map<EnergyComponent, Double> components,
        EnergyUnit unit,
        String engineIdentity,
        String forceFieldIdentity,
        String parameterProvenance,
        ScientificMethod method,
        Map<String, String> provenance) {

    public EnergyEvaluation {
        if (stateId == null || stateId.isBlank()) {
            throw new IllegalArgumentException("stateId must not be blank");
        }
        Objects.requireNonNull(components, "components");
        EnumMap<EnergyComponent, Double> copy = new EnumMap<>(EnergyComponent.class);
        components.forEach((component, value) -> {
            Objects.requireNonNull(component, "component");
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("energy components must be finite");
            }
            copy.put(component, value);
        });
        if (!copy.containsKey(EnergyComponent.TOTAL_POTENTIAL)) {
            throw new IllegalArgumentException("total potential energy is required");
        }
        components = Map.copyOf(copy);
        Objects.requireNonNull(unit, "unit");
        engineIdentity = requireText(engineIdentity, "engineIdentity");
        forceFieldIdentity = requireText(forceFieldIdentity, "forceFieldIdentity");
        parameterProvenance = requireText(parameterProvenance, "parameterProvenance");
        if (method != ScientificMethod.STATE_ENERGY) {
            throw new IllegalArgumentException("energy evaluation must be STATE_ENERGY");
        }
        provenance = Map.copyOf(Objects.requireNonNull(provenance, "provenance"));
    }

    public EnergyEvaluation(String stateId, Map<EnergyComponent, Double> components,
            EnergyUnit unit, String engineIdentity, String forceFieldIdentity,
            String parameterProvenance, ScientificMethod method) {
        this(stateId, components, unit, engineIdentity, forceFieldIdentity,
                parameterProvenance, method, Map.of());
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
