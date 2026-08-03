package totah.lab.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Minimal mapping of public.targets.
 *
 * The physical schema is "public" in production; tests remap it through
 * {@link SchemaRemappingPhysicalNamingStrategy}.
 */
@Entity
@Table(name = "targets", schema = "public")
public class TargetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "uniprot_id", nullable = false, length = 255)
    private String uniProtId;

    protected TargetEntity() {
    }

    public TargetEntity(String name, String uniProtId) {
        this.name = name;
        this.uniProtId = uniProtId;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUniProtId() {
        return uniProtId;
    }
}
