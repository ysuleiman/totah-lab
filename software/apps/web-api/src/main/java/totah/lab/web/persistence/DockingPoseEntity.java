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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Maps docking.docking_pose. receptor_id is a varchar UniProt accession
 * (legacy convention, not a foreign key) and is mapped as a scalar.
 * source_id, source_artifact_id and source_compound_id are deliberately
 * unmapped: poses inserted by this application carry no external
 * identity.
 */
@Entity
@Table(name = "docking_pose")
public class DockingPoseEntity {

    @Id
    @SequenceGenerator(
            name = "docking_pose_id_sequence",
            sequenceName = "docking_pose_id_seq",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "docking_pose_id_sequence"
    )
    private Long id;

    @Column(name = "ligand_id", nullable = false, length = 32)
    private String ligandId;

    @Column(name = "vina_score", nullable = false)
    private double vinaScore;

    @Column(name = "pose_file", nullable = false, columnDefinition = "text")
    private String poseFile;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "receptor_id", length = 50)
    private String receptorId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private DockingRunEntity run;

    @Column(name = "source_system", length = 64)
    private String sourceSystem;

    @Column(name = "ligand_label", length = 128)
    private String ligandLabel;

    protected DockingPoseEntity() {
    }

    public DockingPoseEntity(
            String ligandId,
            double vinaScore,
            String poseFile,
            String receptorId,
            DockingRunEntity run,
            String sourceSystem,
            String ligandLabel
    ) {
        this.ligandId = Objects.requireNonNull(ligandId, "ligandId");
        this.vinaScore = vinaScore;
        this.poseFile = Objects.requireNonNull(poseFile, "poseFile");
        this.receptorId = receptorId;
        this.run = Objects.requireNonNull(run, "run");
        this.sourceSystem = sourceSystem;
        this.ligandLabel = ligandLabel;
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

    public String getLigandId() {
        return ligandId;
    }

    public double getVinaScore() {
        return vinaScore;
    }

    public String getPoseFile() {
        return poseFile;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getReceptorId() {
        return receptorId;
    }

    public DockingRunEntity getRun() {
        return run;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public String getLigandLabel() {
        return ligandLabel;
    }
}
