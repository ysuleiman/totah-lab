package totah.lab.web.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Membership of a canonical {@link ResidueEntity} in a pocket.
 *
 * chain/residue_number/residue_name duplicate identifying values from the
 * canonical residue row (the schema requires them); they are always
 * populated from the canonical residue. chain is character(1), so chains
 * longer than one character are rejected by the importer.
 */
@Entity
@Table(
        name = "pocket_residue",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "pocket_residue_membership_unique",
                        columnNames = {"pocket_id", "residue_id"}
                ),
                @UniqueConstraint(
                        name = "pocket_residue_uk",
                        columnNames = {"pocket_id", "chain", "residue_number"}
                )
        }
)
public class PocketResidueEntity {

    @Id
    @SequenceGenerator(
            name = "pocket_residue_id_sequence",
            sequenceName = "pocket_residue_id_seq",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "pocket_residue_id_sequence"
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pocket_id", nullable = false)
    private PocketEntity pocket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "residue_id", nullable = false)
    private ResidueEntity residue;

    /*
     * docking.pocket_residue.chain is character(1) (bpchar), so it must be
     * bound as CHAR rather than the String default VARCHAR for
     * ddl-auto=validate to accept the mapping.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "chain", nullable = false, length = 1)
    private String chain;

    @Column(name = "residue_number", nullable = false)
    private int residueNumber;

    @Column(name = "residue_name", length = 3)
    private String residueName;

    @OneToMany(
            mappedBy = "pocketResidue",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private final List<PocketAtomEntity> atoms = new ArrayList<>();

    public PocketResidueEntity() {
    }

    public Long getId() {
        return id;
    }

    public PocketEntity getPocket() {
        return pocket;
    }

    public void setPocket(PocketEntity pocket) {
        this.pocket = pocket;
    }

    public ResidueEntity getResidue() {
        return residue;
    }

    public void setResidue(ResidueEntity residue) {
        this.residue = Objects.requireNonNull(residue, "residue");
    }

    public String getChain() {
        return chain;
    }

    public void setChain(String chain) {
        if (chain == null || chain.length() != 1) {
            throw new IllegalArgumentException(
                    "pocket_residue.chain is character(1), got: " + chain
            );
        }
        this.chain = chain;
    }

    public int getResidueNumber() {
        return residueNumber;
    }

    public void setResidueNumber(int residueNumber) {
        this.residueNumber = residueNumber;
    }

    public String getResidueName() {
        return residueName;
    }

    public void setResidueName(String residueName) {
        this.residueName = residueName;
    }

    public List<PocketAtomEntity> getAtoms() {
        return atoms;
    }

    public void addAtom(PocketAtomEntity atom) {
        atoms.add(atom);
        atom.setPocketResidue(this);
    }
}
