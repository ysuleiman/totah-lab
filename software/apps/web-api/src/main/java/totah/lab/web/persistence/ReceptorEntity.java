package totah.lab.web.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "receptor",
        uniqueConstraints = @UniqueConstraint(
                name = "receptor_uniprot_id_unique",
                columnNames = "uniprot_id"
        )
)
public class ReceptorEntity {

    @Id
    @SequenceGenerator(
            name = "receptor_id_sequence",
            sequenceName = "receptor_id_seq",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "receptor_id_sequence"
    )
    private Long id;

    @Column(name = "target_name", length = 100)
    private String targetName;

    @Column(name = "pdb_file", columnDefinition = "text")
    private String pdbFile;

    @Column(name = "pdbqt_file", columnDefinition = "text")
    private String pdbqtFile;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "uniprot_id", length = 20)
    private String uniProtId;

    @Column(name = "protein_name", length = 255)
    private String proteinName;

    @Column(name = "gene_name", length = 50)
    private String geneName;

    @Column(name = "organism", length = 100)
    private String organism;

    @OneToMany(
            mappedBy = "receptor",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private final List<StructureEntity> structures = new ArrayList<>();

    public ReceptorEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getPdbFile() {
        return pdbFile;
    }

    public void setPdbFile(String pdbFile) {
        this.pdbFile = pdbFile;
    }

    public String getPdbqtFile() {
        return pdbqtFile;
    }

    public void setPdbqtFile(String pdbqtFile) {
        this.pdbqtFile = pdbqtFile;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getUniProtId() {
        return uniProtId;
    }

    public void setUniProtId(String uniProtId) {
        this.uniProtId = uniProtId;
    }

    public String getProteinName() {
        return proteinName;
    }

    public void setProteinName(String proteinName) {
        this.proteinName = proteinName;
    }

    public String getGeneName() {
        return geneName;
    }

    public void setGeneName(String geneName) {
        this.geneName = geneName;
    }

    public String getOrganism() {
        return organism;
    }

    public void setOrganism(String organism) {
        this.organism = organism;
    }

    public List<StructureEntity> getStructures() {
        return structures;
    }

    public void addStructure(StructureEntity structure) {
        structures.add(structure);
        structure.setReceptor(this);
    }

    public void removeStructure(StructureEntity structure) {
        structures.remove(structure);
        structure.setReceptor(null);
    }
}
