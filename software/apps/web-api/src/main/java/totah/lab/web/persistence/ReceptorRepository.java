package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReceptorRepository
        extends JpaRepository<ReceptorEntity, Long> {

    Optional<ReceptorEntity> findByUniProtId(String uniProtId);

    @Query("""
        select distinct r.uniProtId
        from ReceptorEntity r
        where r.uniProtId is not null
        """)
    List<String> findDistinctUniProtIds();
}
