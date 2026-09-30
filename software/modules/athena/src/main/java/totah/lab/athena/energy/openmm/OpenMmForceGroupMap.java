package totah.lab.athena.energy.openmm;

import totah.lab.athena.energy.EnergyComponent;

import java.util.Map;
import java.util.Objects;

/** Auditable force-class/group mapping supplied by the system builder. */
public record OpenMmForceGroupMap(Map<Integer, ForceGroup> groups) {
    public OpenMmForceGroupMap {
        groups = Map.copyOf(Objects.requireNonNull(groups, "groups"));
        if (groups.isEmpty()) throw new IllegalArgumentException("force groups required");
        groups.forEach((id, group) -> {
            if (id == null || id < 0 || id > 31 || group == null) {
                throw new IllegalArgumentException("OpenMM force groups must be 0..31");
            }
        });
    }

    public record ForceGroup(String openMmForceClass, EnergyComponent component,
            String decompositionLimit) {
        public ForceGroup {
            if (openMmForceClass == null || openMmForceClass.isBlank()
                    || decompositionLimit == null || decompositionLimit.isBlank()) {
                throw new IllegalArgumentException("force mapping provenance required");
            }
            Objects.requireNonNull(component, "component");
        }
    }
}
