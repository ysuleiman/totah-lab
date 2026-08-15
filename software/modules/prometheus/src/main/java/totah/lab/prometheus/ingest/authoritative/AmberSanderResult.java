package totah.lab.prometheus.ingest.authoritative;

import totah.lab.prometheus.recovery.RecoveredField;
import java.util.Map;

public record AmberSanderResult(RecoveredField<String> software,
                                RecoveredField<String> executable,
                                RecoveredField<Map<String, String>> fileAssignments,
                                RecoveredField<Map<String, String>> controls,
                                RecoveredField<AmberEnergyComponents> components) {
}
