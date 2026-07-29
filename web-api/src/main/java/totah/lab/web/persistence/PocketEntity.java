package totah.lab.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pocket", schema = "docking")
public class PocketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "structure_id", nullable = false)
    private Long structureId;

    @Column(name = "receptor_id")
    private Long receptorId;

    @Column(name = "pocket_number")
    private Integer pocketNumber;

    protected PocketEntity() {
    }

    public Long getId() {
        return id;
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
}
