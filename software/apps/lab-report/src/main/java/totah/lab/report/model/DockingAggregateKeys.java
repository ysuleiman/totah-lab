package totah.lab.report.model;

/**
 * Canonical field names for docking aggregates supplied to a pocket report.
 *
 * <p>The persistence layer owns the SQL and maps database rows to this
 * contract. The report module remains independent of a database technology
 * and docking target.</p>
 */
public final class DockingAggregateKeys {

    public static final String RUN_ID = "runId";
    public static final String TOTAL_LIGAND_COUNT = "totalLigandCount";
    public static final String TOTAL_POSE_COUNT = "totalPoseCount";
    public static final String CONTACT_SCORE_THRESHOLD =
            "contactScoreThreshold";
    public static final String RESIDUES = "residues";
    public static final String SCORE_BANDS = "scoreBands";

    public static final String RESIDUE_ID = "residueId";
    public static final String CHAIN = "chain";
    public static final String RESIDUE_NUMBER = "residueNumber";
    public static final String RESIDUE_NAME = "residueName";
    public static final String CONTACTING_LIGAND_COUNT =
            "contactingLigandCount";
    public static final String CONTACTING_LIGAND_FRACTION =
            "contactingLigandFraction";
    public static final String CONTACTING_POSE_COUNT = "contactingPoseCount";
    public static final String CONTACTING_POSE_FRACTION =
            "contactingPoseFraction";
    public static final String SCORE_FILTERED_LIGAND_COUNT =
            "scoreFilteredLigandCount";
    public static final String SCORE_FILTERED_CONTACTING_LIGAND_COUNT =
            "scoreFilteredContactingLigandCount";
    public static final String SCORE_FILTERED_CONTACTING_LIGAND_FRACTION =
            "scoreFilteredContactingLigandFraction";
    public static final String SCORE_FILTERED_POSE_COUNT =
            "scoreFilteredPoseCount";
    public static final String SCORE_FILTERED_LIGAND_RETENTION =
            "scoreFilteredLigandRetention";
    public static final String SCORE_FILTERED_POSE_RETENTION =
            "scoreFilteredPoseRetention";
    public static final String ENRICHMENT_LOW_CONFIDENCE =
            "enrichmentLowConfidence";
    public static final String RESIDUE_ROLES = "roles";
    public static final String SCORE_FILTERED_CONTACTING_POSE_COUNT =
            "scoreFilteredContactingPoseCount";
    public static final String SCORE_FILTERED_CONTACTING_POSE_FRACTION =
            "scoreFilteredContactingPoseFraction";
    public static final String ENRICHMENT_RATIO = "enrichmentRatio";
    public static final String LOG2_ENRICHMENT = "log2Enrichment";
    public static final String CONTACT_FRACTION_DIFFERENCE =
            "contactFractionDifference";
    public static final String AVERAGE_CONTACTING_SCORE =
            "avgContactingScore";
    public static final String MEDIAN_CONTACTING_SCORE =
            "medianContactingScore";
    public static final String BEST_CONTACTING_SCORE =
            "bestContactingScore";
    public static final String WORST_CONTACTING_SCORE =
            "worstContactingScore";
    public static final String CLOSEST_DISTANCE = "closestDistance";
    public static final String AVERAGE_LIGAND_MIN_DISTANCE =
            "avgLigandMinDistance";
    public static final String AVERAGE_POSE_MIN_DISTANCE =
            "avgPoseMinDistance";

    private DockingAggregateKeys() {
    }
}
