package totah.lab.athena.pocket.evidence;

/**
 * How a candidate pocket reached the comparison stage.
 */
public enum PocketCandidateSource {

    /**
     * Retrieved by global (whole-structure) shape similarity.
     */
    GLOBAL_SHAPE,

    /**
     * Retrieved by pocket-match similarity.
     */
    POCKET_MATCH,

    /**
     * The structure the query was compared against by explicit
     * choice (the reference structure itself).
     */
    CHOSEN_REFERENCE
}
