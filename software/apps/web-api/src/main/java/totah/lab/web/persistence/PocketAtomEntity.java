package totah.lab.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * One pocket atom of a pocket membership.
 *
 * Schema assumption: the coords column (PostgreSQL cube type) is
 * deliberately not mapped — Hibernate validate tolerates unmapped columns,
 * and rows inserted by this application leave coords NULL.
 */
@Entity
@Table(name = "pocket_atom")
public class PocketAtomEntity {

    @Id
    @SequenceGenerator(
            name = "pocket_atom_id_sequence",
            sequenceName = "pocket_atom_id_seq",
            allocationSize = 1
    )
    @GeneratedValue(
            strategy = GenerationType.SEQUENCE,
            generator = "pocket_atom_id_sequence"
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pocket_residue_id", nullable = false)
    private PocketResidueEntity pocketResidue;

    @Column(name = "atom_name", length = 8)
    private String atomName;

    @Column(name = "x")
    private Double x;

    @Column(name = "y")
    private Double y;

    @Column(name = "z")
    private Double z;

    @Column(name = "element", length = 4)
    private String element;

    public PocketAtomEntity() {
    }

    public Long getId() {
        return id;
    }

    public PocketResidueEntity getPocketResidue() {
        return pocketResidue;
    }

    public void setPocketResidue(PocketResidueEntity pocketResidue) {
        this.pocketResidue = pocketResidue;
    }

    public String getAtomName() {
        return atomName;
    }

    public void setAtomName(String atomName) {
        this.atomName = atomName;
    }

    public Double getX() {
        return x;
    }

    public void setX(Double x) {
        this.x = x;
    }

    public Double getY() {
        return y;
    }

    public void setY(Double y) {
        this.y = y;
    }

    public Double getZ() {
        return z;
    }

    public void setZ(Double z) {
        this.z = z;
    }

    public String getElement() {
        return element;
    }

    public void setElement(String element) {
        this.element = element;
    }
}
