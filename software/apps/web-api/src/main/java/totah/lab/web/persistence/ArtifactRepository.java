package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtifactRepository
        extends JpaRepository<ArtifactEntity, Long> {

    /**
     * Idempotency lookup mirroring the NOT EXISTS pattern of
     * tools/scripts/generate_docking_resource_import.mjs.
     */
    Optional<ArtifactEntity> findByStorageLocationAndTargetId(
            String storageLocation,
            Long targetId
    );

    /**
     * Used to reuse the pipeline run already associated with a target
     * (one FINISHED run per target).
     */
    Optional<ArtifactEntity> findFirstByTargetId(Long targetId);
}
