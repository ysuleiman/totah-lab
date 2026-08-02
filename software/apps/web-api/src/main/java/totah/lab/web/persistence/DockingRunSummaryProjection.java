package totah.lab.web.persistence;

import java.time.LocalDateTime;

public interface DockingRunSummaryProjection {

    long getId();

    long getStructureId();

    long getReceptorId();

    LocalDateTime getCreatedAt();

    long getTotalLigandCount();

    long getTotalPoseCount();
}
