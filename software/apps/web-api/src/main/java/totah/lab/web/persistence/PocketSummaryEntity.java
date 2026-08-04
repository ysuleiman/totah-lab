package totah.lab.web.persistence;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "pocket_summary_mv", schema = "docking")
public class PocketSummaryEntity {

    @Id
    @Column(name = "pocket_id", nullable = false)
    private Long pocketId;

    @Column(name = "structure_id", nullable = false)
    private Long structureId;

    @Column(name = "receptor_id", nullable = false)
    private Long receptorId;

    @Column(name = "pocket_number", nullable = false)
    private Integer pocketNumber;

    @Column(name = "source")
    private String pocketSource;

    @Column(name = "structure_source")
    private String structureSource;

    @Column(name = "source_accession")
    private String sourceAccession;

    @Column(name = "uniprot_id")
    private String uniProtId;

    @Column(name = "target_name")
    private String targetName;

    @Column(name = "protein_name")
    private String proteinName;

    @Column(name = "gene_name")
    private String geneName;

    @Column(name = "organism")
    private String organism;

    @Column(name = "volume")
    private Double volume;

    @Column(name = "score")
    private Double score;

    @Column(name = "druggability_score")
    private Double druggabilityScore;

    @Column(name = "residue_count")
    private Integer residueCount;

    @Column(name = "atom_count")
    private Integer atomCount;

    @Column(name = "alpha_sphere_count")
    private Integer alphaSphereCount;

    @Column(name = "geometry_basis")
    private String geometryBasis;

    @Column(name = "cysteine_count")
    private Integer cysteineCount;

    @Column(name = "aromatic_count")
    private Integer aromaticCount;

    @Column(name = "hydrophobic_count")
    private Integer hydrophobicCount;

    @Column(name = "polar_count")
    private Integer polarCount;

    @Column(name = "positive_count")
    private Integer positiveCount;

    @Column(name = "negative_count")
    private Integer negativeCount;

    @Column(name = "cysteine_fraction")
    private Double cysteineFraction;

    @Column(name = "aromatic_fraction")
    private Double aromaticFraction;

    @Column(name = "hydrophobic_fraction")
    private Double hydrophobicFraction;

    @Column(name = "polar_fraction")
    private Double polarFraction;

    @Column(name = "negative_fraction")
    private Double negativeFraction;

    @Column(name = "positive_fraction")
    private Double positiveFraction;

    protected PocketSummaryEntity() {
    }

    public Long getPocketId() {
        return pocketId;
    }

    public Long getStructureId() {
        return structureId;
    }

    public Long getReceptorId() {
        return receptorId;
    }

    public Integer getPocketNumber() {
        return pocketNumber;
    }

    public String getPocketSource() {
        return pocketSource;
    }

    public String getStructureSource() {
        return structureSource;
    }

    public String getSourceAccession() {
        return sourceAccession;
    }

    public String getUniProtId() {
        return uniProtId;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getProteinName() {
        return proteinName;
    }

    public String getGeneName() {
        return geneName;
    }

    public String getOrganism() {
        return organism;
    }

    public Double getVolume() {
        return volume;
    }

    public Double getScore() {
        return score;
    }

    public Double getDruggabilityScore() {
        return druggabilityScore;
    }

    public Integer getResidueCount() {
        return residueCount;
    }

    public Integer getAtomCount() {
        return atomCount;
    }

    public Integer getAlphaSphereCount() {
        return alphaSphereCount;
    }

    public String getGeometryBasis() {
        return geometryBasis;
    }

    public Integer getCysteineCount() {
        return cysteineCount;
    }

    public Integer getAromaticCount() {
        return aromaticCount;
    }

    public Integer getHydrophobicCount() {
        return hydrophobicCount;
    }

    public Integer getPolarCount() {
        return polarCount;
    }

    public Integer getPositiveCount() {
        return positiveCount;
    }

    public Integer getNegativeCount() {
        return negativeCount;
    }

    public Double getPolarFraction() {
        return polarFraction;
    }

    public Double getPositiveFraction() {
        return positiveFraction;
    }

    public Double getNegativeFraction() {
        return negativeFraction;
    }

    public Double getHydrophobicFraction() {
        return hydrophobicFraction;
    }

    public Double getAromaticFraction() {
        return aromaticFraction;
    }
}
