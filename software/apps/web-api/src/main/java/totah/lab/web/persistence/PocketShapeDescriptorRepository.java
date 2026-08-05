package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for the precomputed Stage 1 shape descriptors. The primary
 * key is the pocket id itself (assigned, not generated), so
 * {@code findById}/{@code findAllById} answer "does this pocket already
 * have a descriptor" and {@code save}/{@code saveAll} upsert.
 */
public interface PocketShapeDescriptorRepository
        extends JpaRepository<PocketShapeDescriptorEntity, Long> {
}
