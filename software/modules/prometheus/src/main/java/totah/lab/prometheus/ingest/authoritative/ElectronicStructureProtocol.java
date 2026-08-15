package totah.lab.prometheus.ingest.authoritative;

import totah.lab.prometheus.recovery.RecoveredField;

/** Explicit components deterministically parsed from the structured method field. */
public record ElectronicStructureProtocol(
        RecoveredField<String> functional,
        RecoveredField<String> basisSet,
        RecoveredField<String> dispersion,
        RecoveredField<Boolean> densityFitted,
        RecoveredField<String> phase) { }
