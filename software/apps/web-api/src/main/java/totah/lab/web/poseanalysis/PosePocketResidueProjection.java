package totah.lab.web.poseanalysis;

/**
 * One pocket-member residue of a structure, keyed by pocket so the
 * service can group the bulk query result per candidate pocket.
 */
public interface PosePocketResidueProjection {

    Long getPocketId();

    String getChain();

    Integer getResidueNumber();

    String getInsertionCode();

    String getResidueName();
}
