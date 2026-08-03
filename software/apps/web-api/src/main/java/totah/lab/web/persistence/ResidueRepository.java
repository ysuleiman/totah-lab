package totah.lab.web.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResidueRepository
        extends JpaRepository<ResidueEntity, Long> {

    List<ResidueEntity> findAllByStructureId(long structureId);
}
