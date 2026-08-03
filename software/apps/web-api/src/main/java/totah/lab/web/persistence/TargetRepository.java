package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TargetRepository
        extends JpaRepository<TargetEntity, Long> {

    Optional<TargetEntity> findByUniProtId(String uniProtId);
}
