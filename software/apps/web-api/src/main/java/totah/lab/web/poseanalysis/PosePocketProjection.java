package totah.lab.web.poseanalysis;

/**
 * One candidate pocket of a structure for pose-to-pocket assignment:
 * identity only — geometry comes from the sphere and residue queries.
 */
public interface PosePocketProjection {

    Long getId();

    Integer getPocketNumber();

    String getSource();
}
