package totah.lab.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Canonical residue of a structure, created once per structure and shared
 * by every pocket membership that references it.
 *
 * Identity: (structure_id, chain, residue_number, insertion_code).
 * Insertion code normalization: null or whitespace -> "".
 */
@Entity
@Table(
        name = "residue",
        uniqueConstraints = @UniqueConstraint(
                name = "residue_structure_position_unique",
                columnNames = {
                        "structure_id",
                        "chain",
                        "residue_number",
                        "insertion_code"
                }
        ),
        indexes = @Index(
                name = "residue_structure_idx",
                columnList = "structure_id, chain, residue_number"
        )
)
public class ResidueEntity {

    @Id
    @SequenceGenerator(
            name = "residue_id_sequence",
            sequenceName = "residue_id_seq",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "residue_id_sequence"
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "structure_id", nullable = false)
    private StructureEntity structure;

    @Column(name = "chain", nullable = false, length = 10)
    private String chain;

    @Column(name = "residue_number", nullable = false)
    private int residueNumber;

    @Column(name = "insertion_code", nullable = false, length = 1)
    private String insertionCode = "";

    @Column(name = "residue_name", nullable = false, length = 3)
    private String residueName;

    protected ResidueEntity() {
    }

    public ResidueEntity(
            StructureEntity structure,
            String chain,
            int residueNumber,
            String insertionCode,
            String residueName
    ) {
        this.structure = structure;
        this.chain = normalizeChain(chain);
        this.residueNumber = residueNumber;
        this.insertionCode = normalizeInsertionCode(insertionCode);
        this.residueName = normalizeResidueName(residueName);
    }

    public Long getId() {
        return id;
    }

    public StructureEntity getStructure() {
        return structure;
    }

    public String getChain() {
        return chain;
    }

    public int getResidueNumber() {
        return residueNumber;
    }

    public String getInsertionCode() {
        return insertionCode;
    }

    public String getResidueName() {
        return residueName;
    }

    private static String normalizeChain(String chain) {
        if (chain == null || chain.isBlank()) {
            throw new IllegalArgumentException("Chain is required");
        }

        String normalized = chain.trim();

        if (normalized.length() > 10) {
            throw new IllegalArgumentException(
                    "Chain must be at most 10 characters: " + chain
            );
        }

        return normalized;
    }

    private static String normalizeInsertionCode(String insertionCode) {
        if (insertionCode == null || insertionCode.isBlank()) {
            return "";
        }

        String normalized = insertionCode.trim();

        if (normalized.length() > 1) {
            throw new IllegalArgumentException(
                    "Insertion code must be at most one character"
            );
        }

        return normalized;
    }

    private static String normalizeResidueName(String residueName) {
        if (residueName == null || residueName.isBlank()) {
            throw new IllegalArgumentException(
                    "Residue name is required"
            );
        }

        String normalized = residueName.trim().toUpperCase();

        if (normalized.length() > 3) {
            throw new IllegalArgumentException(
                    "Residue name must be at most three characters: "
                            + residueName
            );
        }

        return normalized;
    }
}
