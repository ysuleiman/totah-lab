package totah.lab.web.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import totah.lab.gaia.pocket.PocketSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "pocket")
public class PocketEntity {

    @Id
    @SequenceGenerator(
            name = "pocket_id_sequence",
            sequenceName = "pocket_id_seq",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "pocket_id_sequence"
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receptor_id")
    private ReceptorEntity receptor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "structure_id", nullable = false)
    private StructureEntity structure;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artifact_id", nullable = false)
    private ArtifactEntity artifact;

    @Column(name = "pocket_number")
    private Integer pocketNumber;

    /*
     * docking.pocket_source is a PostgreSQL enum whose labels match
     * PocketSource. PostgreSQLEnumJdbcType binds it as a native PG enum so
     * ddl-auto=validate accepts the mapping.
     */
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(
            name = "source",
            nullable = false,
            columnDefinition = "docking.pocket_source"
    )
    private PocketSource source;

    @Column(name = "fpocket_file", columnDefinition = "text")
    private String fpocketFile;

    @Column(name = "volume")
    private Double volume;

    @Column(name = "druggability_score")
    private Double druggabilityScore;

    @Column(name = "score")
    private Double score;

    @Column(name = "probability")
    private Double probability;

    @OneToMany(
            mappedBy = "pocket",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private final List<PocketResidueEntity> residues = new ArrayList<>();

    public PocketEntity() {
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

    public StructureEntity getStructure() {
        return structure;
    }

    public void setStructure(StructureEntity structure) {
        this.structure = Objects.requireNonNull(structure, "structure");
    }

    public ArtifactEntity getArtifact() {
        return artifact;
    }

    public void setArtifact(ArtifactEntity artifact) {
        this.artifact = Objects.requireNonNull(artifact, "artifact");
    }

    public Integer getPocketNumber() {
        return pocketNumber;
    }

    public void setPocketNumber(Integer pocketNumber) {
        this.pocketNumber = pocketNumber;
    }

    public PocketSource getSource() {
        return source;
    }

    public void setSource(PocketSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public String getFpocketFile() {
        return fpocketFile;
    }

    public void setFpocketFile(String fpocketFile) {
        this.fpocketFile = fpocketFile;
    }

    public Double getVolume() {
        return volume;
    }

    public void setVolume(Double volume) {
        this.volume = volume;
    }

    public Double getDruggabilityScore() {
        return druggabilityScore;
    }

    public void setDruggabilityScore(Double druggabilityScore) {
        this.druggabilityScore = druggabilityScore;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Double getProbability() {
        return probability;
    }

    public void setProbability(Double probability) {
        this.probability = probability;
    }

    public List<PocketResidueEntity> getResidues() {
        return residues;
    }

    public void addResidue(PocketResidueEntity residue) {
        residues.add(residue);
        residue.setPocket(this);
    }

    /**
     * Detaches all memberships. With orphanRemoval the previous rows are
     * deleted on flush, which is how reimports replace pocket content.
     */
    public void clearResidues() {
        for (PocketResidueEntity residue : residues) {
            residue.setPocket(null);
        }
        residues.clear();
    }
}
