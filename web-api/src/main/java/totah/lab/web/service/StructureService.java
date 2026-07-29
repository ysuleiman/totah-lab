package totah.lab.web.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.web.persistence.PocketResidueProjection;
import totah.lab.web.persistence.StructureDetailsProjection;
import totah.lab.web.persistence.StructureRepository;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class StructureService {

    private final StructureRepository structureRepository;

    public StructureService(StructureRepository structureRepository) {
        this.structureRepository = structureRepository;
    }

    @Transactional(readOnly = true)
    public StructureDetails getStructure(long structureId) {
        StructureDetailsProjection structure = structureRepository
                .findStructureDetails(structureId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Structure not found: " + structureId
                ));

        return new StructureDetails(
                structure.getId(),
                structure.getSource(),
                structure.getSourceAccession(),
                structure.getChain(),
                structure.getModelNumber(),
                structure.getPreparationState(),
                structure.getParentStructureId(),
                new ReceptorSummary(
                        structure.getReceptorId(),
                        structure.getTargetName()
                ),
                new ArtifactSummary(
                        structure.getArtifactId(),
                        structure.getArtifactFilename(),
                        structure.getArtifactLabel(),
                        structure.getArtifactStorageLocation()
                ),
                chosenPocket(structure),
                structureRepository.findResiduesByStructureId(structureId)
                        .stream()
                        .map(this::toResidueDetails)
                        .toList(),
                "/api/structures/" + structure.getId() + "/pockets"
        );
    }

    private ChosenPocketSummary chosenPocket(
            StructureDetailsProjection structure
    ) {
        if (structure.getChosenPocketId() == null) {
            return null;
        }
        return new ChosenPocketSummary(
                structure.getChosenPocketId(),
                structure.getChosenPocketNumber(),
                structure.getChosenPocketSource()
        );
    }

    private ResidueDetails toResidueDetails(
            PocketResidueProjection residue
    ) {
        return new ResidueDetails(
                residue.getId(),
                residue.getChain(),
                residue.getResidueNumber(),
                residue.getInsertionCode(),
                residue.getResidueName()
        );
    }

    public record StructureDetails(
            long id,
            String source,
            String sourceAccession,
            String chain,
            Integer modelNumber,
            String preparationState,
            Long parentStructureId,
            ReceptorSummary receptor,
            ArtifactSummary artifact,
            ChosenPocketSummary chosenPocket,
            List<ResidueDetails> residues,
            String pocketsUrl
    ) {
        public StructureDetails {
            residues = List.copyOf(residues);
        }
    }

    public record ReceptorSummary(
            long id,
            String targetName
    ) {
    }

    public record ArtifactSummary(
            long id,
            String filename,
            String label,
            String storageLocation
    ) {
    }

    public record ChosenPocketSummary(
            long id,
            int pocketNumber,
            String source
    ) {
    }

    public record ResidueDetails(
            long id,
            String chain,
            int residueNumber,
            String insertionCode,
            String residueName
    ) {
    }
}
