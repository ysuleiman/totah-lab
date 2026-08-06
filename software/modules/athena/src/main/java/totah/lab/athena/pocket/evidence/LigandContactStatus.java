package totah.lab.athena.pocket.evidence;

/**
 * Availability of a {@link LigandContact} record. {@link #AVAILABLE}
 * marks a measured contact annotation; {@link #NOT_AVAILABLE} is the
 * explicit marker for absent evidence — it replaces the
 * Optional-absence convention at the single-contact level, so that a
 * missing annotation is reported as NOT_AVAILABLE and never as a
 * zeroed contact.
 */
public enum LigandContactStatus {
    AVAILABLE,
    NOT_AVAILABLE
}
