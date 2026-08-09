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
 * Maps docking.docking_run. source_id and source_metadata are
 * deliberately unmapped: runs inserted by this application carry no
 * external identity, and the column defaults apply.
 */
@Entity
@Table(name = "docking_run")
public class DockingRunEntity {

    @Id
    @SequenceGenerator(
            name = "docking_run_id_sequence",
            sequenceName = "docking_run_id_seq",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "docking_run_id_sequence"
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receptor_id")
    private ReceptorEntity receptor;

    @Column(name = "grid_center_x")
    private Double gridCenterX;

    @Column(name = "grid_center_y")
    private Double gridCenterY;

    @Column(name = "grid_center_z")
    private Double gridCenterZ;

    @Column(name = "grid_size_x")
    private Double gridSizeX;

    @Column(name = "grid_size_y")
    private Double gridSizeY;

    @Column(name = "grid_size_z")
    private Double gridSizeZ;

    @Column(name = "vina_version", length = 50)
    private String vinaVersion;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "structure_id", nullable = false)
    private StructureEntity structure;

    @Column(name = "source_system", length = 64)
    private String sourceSystem;

    protected DockingRunEntity() {
    }

    public DockingRunEntity(
            ReceptorEntity receptor,
            StructureEntity structure,
            double centerX, double centerY, double centerZ,
            double sizeX, double sizeY, double sizeZ,
            String vinaVersion,
            String sourceSystem
    ) {
        this.receptor = receptor;
        this.structure = Objects.requireNonNull(structure, "structure");
        this.gridCenterX = centerX;
        this.gridCenterY = centerY;
        this.gridCenterZ = centerZ;
        this.gridSizeX = sizeX;
        this.gridSizeY = sizeY;
        this.gridSizeZ = sizeZ;
        this.vinaVersion = vinaVersion;
        this.sourceSystem = sourceSystem;
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

    public ReceptorEntity getReceptor() {
        return receptor;
    }

    public StructureEntity getStructure() {
        return structure;
    }

    public Double getGridCenterX() {
        return gridCenterX;
    }

    public Double getGridCenterY() {
        return gridCenterY;
    }

    public Double getGridCenterZ() {
        return gridCenterZ;
    }

    public Double getGridSizeX() {
        return gridSizeX;
    }

    public Double getGridSizeY() {
        return gridSizeY;
    }

    public Double getGridSizeZ() {
        return gridSizeZ;
    }

    public String getVinaVersion() {
        return vinaVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }
}
