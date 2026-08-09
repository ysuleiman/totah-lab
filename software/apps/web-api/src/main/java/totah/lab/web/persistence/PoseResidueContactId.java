package totah.lab.web.persistence;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of docking.pose_residue_contact. */
public class PoseResidueContactId implements Serializable {

    private long poseId;
    private long residueId;

    protected PoseResidueContactId() {
    }

    public PoseResidueContactId(long poseId, long residueId) {
        this.poseId = poseId;
        this.residueId = residueId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PoseResidueContactId that)) {
            return false;
        }
        return poseId == that.poseId && residueId == that.residueId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(poseId, residueId);
    }
}
