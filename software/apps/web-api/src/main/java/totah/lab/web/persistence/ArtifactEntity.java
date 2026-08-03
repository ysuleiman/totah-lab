package totah.lab.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Maps docking.artifacts. The pipeline_run_id and target_id foreign keys
 * cross into the public schema and are mapped as relationships to
 * {@link PipelineRunEntity} and {@link TargetEntity}.
 */
@Entity
@Table(name = "artifacts")
public class ArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    @Column(name = "storage_location", nullable = false, length = 1024)
    private String storageLocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pipeline_run_id", nullable = false)
    private PipelineRunEntity pipelineRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    private TargetEntity target;

    protected ArtifactEntity() {
    }

    public ArtifactEntity(
            String filename,
            String label,
            String storageLocation,
            PipelineRunEntity pipelineRun,
            TargetEntity target
    ) {
        this.filename = Objects.requireNonNull(filename, "filename");
        this.label = Objects.requireNonNull(label, "label");
        this.storageLocation =
                Objects.requireNonNull(storageLocation, "storageLocation");
        this.pipelineRun =
                Objects.requireNonNull(pipelineRun, "pipelineRun");
        this.target = Objects.requireNonNull(target, "target");
    }

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getFilename() {
        return filename;
    }

    public String getLabel() {
        return label;
    }

    public String getStorageLocation() {
        return storageLocation;
    }

    public PipelineRunEntity getPipelineRun() {
        return pipelineRun;
    }

    public TargetEntity getTarget() {
        return target;
    }
}
