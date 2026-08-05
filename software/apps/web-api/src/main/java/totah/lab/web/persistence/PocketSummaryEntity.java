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

    /*
     * Precomputed Stage 1 shape-descriptor columns (LEFT JOINed from
     * pocket_shape_descriptor): NULL for pockets without a descriptor.
     * elongation/flatness are the normalized forms (middle/major,
     * minor/major); h0..h11 are the radial-histogram bins.
     */
    @Column(name = "shape_point_count")
    private Integer shapePointCount;

    @Column(name = "radius_of_gyration")
    private Double radiusOfGyration;

    @Column(name = "extent_major")
    private Double extentMajor;

    @Column(name = "extent_middle")
    private Double extentMiddle;

    @Column(name = "extent_minor")
    private Double extentMinor;

    @Column(name = "elongation")
    private Double elongation;

    @Column(name = "flatness")
    private Double flatness;

    @Column(name = "h0")
    private Double h0;

    @Column(name = "h1")
    private Double h1;

    @Column(name = "h2")
    private Double h2;

    @Column(name = "h3")
    private Double h3;

    @Column(name = "h4")
    private Double h4;

    @Column(name = "h5")
    private Double h5;

    @Column(name = "h6")
    private Double h6;

    @Column(name = "h7")
    private Double h7;

    @Column(name = "h8")
    private Double h8;

    @Column(name = "h9")
    private Double h9;

    @Column(name = "h10")
    private Double h10;

    @Column(name = "h11")
    private Double h11;

    @Column(name = "descriptor_version")
    private Integer descriptorVersion;

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

    public Integer getShapePointCount() {
        return shapePointCount;
    }

    public Double getRadiusOfGyration() {
        return radiusOfGyration;
    }

    public Double getExtentMajor() {
        return extentMajor;
    }

    public Double getExtentMiddle() {
        return extentMiddle;
    }

    public Double getExtentMinor() {
        return extentMinor;
    }

    public Double getElongation() {
        return elongation;
    }

    public Double getFlatness() {
        return flatness;
    }

    public Double getH0() {
        return h0;
    }

    public Double getH1() {
        return h1;
    }

    public Double getH2() {
        return h2;
    }

    public Double getH3() {
        return h3;
    }

    public Double getH4() {
        return h4;
    }

    public Double getH5() {
        return h5;
    }

    public Double getH6() {
        return h6;
    }

    public Double getH7() {
        return h7;
    }

    public Double getH8() {
        return h8;
    }

    public Double getH9() {
        return h9;
    }

    public Double getH10() {
        return h10;
    }

    public Double getH11() {
        return h11;
    }

    public Integer getDescriptorVersion() {
        return descriptorVersion;
    }

    /**
     * The 12 radial-histogram bins, or null when this row has no
     * precomputed descriptor.
     */
    public double[] getRadialHistogram() {
        if (h0 == null) {
            return null;
        }
        return new double[]{
                h0, h1, h2, h3, h4, h5, h6, h7, h8, h9, h10, h11
        };
    }
}
