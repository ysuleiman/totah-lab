package totah.lab.athena.ligand.pose;

/**
 * Outcome of assigning a predicted pose to a candidate pocket. A pose
 * is never forced into a pocket: weak evidence yields
 * {@link #NOT_ASSIGNED} and near-tied evidence yields
 * {@link #AMBIGUOUS}.
 */
public enum AssignmentStatus {

    /** One pocket is the clear best match for the predicted pose. */
    ASSIGNED,

    /** The best pocket is reported but the runner-up is within the
     *  ambiguity margin; the match is flagged, not trusted. */
    AMBIGUOUS,

    /** No candidate pocket shows convincing occupancy or contact
     *  overlap; no pocket is reported. */
    NOT_ASSIGNED
}
