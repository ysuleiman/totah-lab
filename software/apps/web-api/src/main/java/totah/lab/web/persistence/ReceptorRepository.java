package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReceptorRepository
        extends JpaRepository<ReceptorEntity, Long> {

    Optional<ReceptorEntity> findByUniProtId(String uniProtId);
}
