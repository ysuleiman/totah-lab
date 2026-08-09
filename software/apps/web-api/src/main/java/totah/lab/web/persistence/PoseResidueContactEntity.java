package totah.lab.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Maps docking.pose_residue_contact: the residue-level rollup of
 * pose-atom/pocket-atom contacts within 4.0 Å. pose_id and residue_id
 * are mapped as scalars; the foreign keys to docking_pose and residue
 * are enforced by the database.
 */
@Entity
@Table(name = "pose_residue_contact")
@IdClass(PoseResidueContactId.class)
public class PoseResidueContactEntity {

    @Id
    @Column(name = "pose_id")
    private long poseId;

    @Id
    @Column(name = "residue_id")
    private long residueId;

    @Column(name = "atom_contact_count", nullable = false)
    private int atomContactCount;

    @Column(name = "min_distance", nullable = false)
    private double minDistance;

    protected PoseResidueContactEntity() {
    }

    public PoseResidueContactEntity(
            long poseId,
            long residueId,
            int atomContactCount,
            double minDistance
    ) {
        if (atomContactCount < 1) {
            throw new IllegalArgumentException(
                    "atomContactCount must be positive");
        }
        if (minDistance < 0.0 || minDistance > 4.0) {
            throw new IllegalArgumentException(
                    "minDistance outside 0..4.0: " + minDistance);
        }
        this.poseId = poseId;
        this.residueId = residueId;
        this.atomContactCount = atomContactCount;
        this.minDistance = minDistance;
    }

    public long getPoseId() {
        return poseId;
    }

    public long getResidueId() {
        return residueId;
    }

    public int getAtomContactCount() {
        return atomContactCount;
    }

    public double getMinDistance() {
        return minDistance;
    }
}
