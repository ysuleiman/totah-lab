package totah.lab.web.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.athena.pocket.compare.residue.PocketResiduePoint;
import totah.lab.athena.pocket.compare.residue.PocketResiduePointFactory;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;
import totah.lab.web.persistence.StructureDetailsProjection;
import totah.lab.web.persistence.StructureRepository;

import java.io.IOException;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

/**
 * Loads the Athena residue points of a single pocket: the pocket
 * details and structure artifact are read from persistence/storage and
 * mapped through the same Gaia domain construction the report path
 * uses ({@link PocketReportApplicationService#toDomainPocket}).
 */
@Service
@Transactional(readOnly = true)
public class PocketResidueLoader {

    private final PocketService pocketService;
    private final StructureRepository structureRepository;
    private final StructureArtifactService structureArtifactService;
    private final PocketResiduePointFactory pointFactory =
            new PocketResiduePointFactory();

    public PocketResidueLoader(
            PocketService pocketService,
            StructureRepository structureRepository,
            StructureArtifactService structureArtifactService
    ) {
        this.pocketService = pocketService;
        this.structureRepository = structureRepository;
        this.structureArtifactService = structureArtifactService;
    }

    public List<PocketResiduePoint> load(long pocketId) {
        PocketService.PocketDetails details =
                pocketService.getPocket(pocketId);

        Structure structure = loadStructure(pocketId, details);
        Pocket pocket =
                PocketReportApplicationService.toDomainPocket(details);

        return pointFactory.create(structure, pocket);
    }

    private Structure loadStructure(
            long pocketId,
            PocketService.PocketDetails details
    ) {
        // The pocket's own artifact is the fpocket output file; the
        // full structure comes from the structure's artifact.
        StructureDetailsProjection structureDetails = structureRepository
                .findStructureDetails(details.structure().id())
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Structure not found: " + details.structure().id()
                ));

        try {
            return structureArtifactService.load(
                    structureDetails.getArtifactId(),
                    structureDetails.getArtifactStorageLocation()
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    UNPROCESSABLE_ENTITY,
                    "Pocket " + pocketId
                            + " structure artifact cannot be loaded: "
                            + exception.getMessage(),
                    exception
            );
        }
    }
}
