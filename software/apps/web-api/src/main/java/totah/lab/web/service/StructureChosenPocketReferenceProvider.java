package totah.lab.web.service;

import org.springframework.stereotype.Component;
import totah.lab.web.persistence.StructureRepository;

import java.util.List;
import java.util.Objects;

/**
 * Chosen-reference channel backed by
 * {@code docking.structure.chosen_pocket_id}.
 */
@Component
class StructureChosenPocketReferenceProvider
        implements ChosenPocketReferenceProvider {

    private final StructureRepository structureRepository;

    StructureChosenPocketReferenceProvider(
            StructureRepository structureRepository
    ) {
        this.structureRepository =
                Objects.requireNonNull(structureRepository);
    }

    @Override
    public List<Long> chosenPocketIds(long receptorId, long structureId) {
        return structureRepository.findChosenPocketIdsByReceptorId(
                receptorId
        );
    }
}
