package totah.lab.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Minimal mapping of public.pipeline_runs.
 *
 * The physical schema is "public" in production; tests remap it through
 * {@link SchemaRemappingPhysicalNamingStrategy}.
 */
@Entity
@Table(name = "pipeline_runs", schema = "public")
public class PipelineRunEntity {

    public static final String STATUS_FINISHED = "FINISHED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "status", nullable = false, length = 255)
    private String status;

    protected PipelineRunEntity() {
    }

    public PipelineRunEntity(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String status
    ) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = Objects.requireNonNull(status, "status");
    }

    /**
     * Creates a run that is already finished, mirroring
     * tools/scripts/generate_docking_resource_import.mjs.
     */
    public static PipelineRunEntity finishedNow() {
        LocalDateTime now = LocalDateTime.now();
        return new PipelineRunEntity(now, now, STATUS_FINISHED);
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getStatus() {
        return status;
    }
}
