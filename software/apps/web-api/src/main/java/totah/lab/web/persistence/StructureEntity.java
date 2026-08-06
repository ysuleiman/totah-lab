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
 * Maps docking.structure.
 *
 * chosen_pocket_id is mapped as a plain scalar (read-mostly; the
 * importer never sets it). Its composite foreign key
 * (chosen_pocket_id, id) -> pocket(id, structure_id) cannot be
 * expressed on the owning side in JPA, so it is intentionally not
 * modeled as a relationship.
 */
@Entity
@Table(name = "structure")
public class StructureEntity {

    @Id
    @SequenceGenerator(
            name = "structure_id_sequence",
            sequenceName = "structure_id_seq",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "structure_id_sequence"
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receptor_id", nullable = false)
    private ReceptorEntity receptor;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "source_accession", length = 100)
    private String sourceAccession;

    @Column(name = "chain", length = 10)
    private String chain;

    @Column(name = "model_number")
    private Integer modelNumber;

    @Column(name = "preparation_state", nullable = false, length = 20)
    private String preparationState = "RAW";

    @Column(name = "parent_structure_id")
    private Long parentStructureId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artifact_id", nullable = false)
    private ArtifactEntity artifact;

    /*
     * Scalar-only mapping of the chosen pocket reference (see class
     * javadoc); rows inserted by this application leave it NULL.
     */
    @Column(name = "chosen_pocket_id")
    private Long chosenPocketId;

    public StructureEntity() {
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

    public void setReceptor(ReceptorEntity receptor) {
        this.receptor = receptor;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public String getSourceAccession() {
        return sourceAccession;
    }

    public void setSourceAccession(String sourceAccession) {
        this.sourceAccession = sourceAccession;
    }

    public String getChain() {
        return chain;
    }

    public void setChain(String chain) {
        this.chain = chain;
    }

    public Integer getModelNumber() {
        return modelNumber;
    }

    public void setModelNumber(Integer modelNumber) {
        this.modelNumber = modelNumber;
    }

    public String getPreparationState() {
        return preparationState;
    }

    public void setPreparationState(String preparationState) {
        this.preparationState =
                Objects.requireNonNull(preparationState, "preparationState");
    }

    public Long getParentStructureId() {
        return parentStructureId;
    }

    public void setParentStructureId(Long parentStructureId) {
        this.parentStructureId = parentStructureId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public ArtifactEntity getArtifact() {
        return artifact;
    }

    public void setArtifact(ArtifactEntity artifact) {
        this.artifact = Objects.requireNonNull(artifact, "artifact");
    }

    public Long getChosenPocketId() {
        return chosenPocketId;
    }

    public void setChosenPocketId(Long chosenPocketId) {
        this.chosenPocketId = chosenPocketId;
    }
}
