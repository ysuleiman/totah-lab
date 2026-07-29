package totah.lab.web.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.web.persistence.PocketDetailsProjection;
import totah.lab.web.persistence.PocketRepository;
import totah.lab.web.persistence.PocketResidueProjection;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Application service for pocket retrieval, investigation, and comparison.
 *
 * <p>Geometry algorithms remain in the domain model. This service coordinates
 * persistence, artifact loading, and domain operations for web requests.</p>
 */
@Service
public class PocketService {

    private final PocketRepository pocketRepository;

    public PocketService(PocketRepository pocketRepository) {
        this.pocketRepository = pocketRepository;
    }

    @Transactional(readOnly = true)
    public PocketDetails getPocket(long pocketId) {
        PocketDetailsProjection pocket = pocketRepository
                .findPocketDetails(pocketId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Pocket not found: " + pocketId
                ));
        return toDetails(pocket, residues(pocketId));
    }

    @Transactional(readOnly = true)
    public List<PocketSummary> getPocketsForStructure(long structureId) {
        return pocketRepository.findPocketDetailsByStructureId(structureId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ResidueDetails> getResiduesForPocket(long pocketId) {
        if (!pocketRepository.existsById(pocketId)) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "Pocket not found: " + pocketId
            );
        }
        return residues(pocketId);
    }

    private PocketDetails toDetails(
            PocketDetailsProjection pocket,
            List<ResidueDetails> residues
    ) {
        return new PocketDetails(
                pocket.getId(),
                pocket.getPocketNumber(),
                pocket.getSource(),
                pocket.getVolume(),
                pocket.getDruggabilityScore(),
                new StructureSummary(
                        pocket.getStructureId(),
                        pocket.getStructureSource(),
                        pocket.getStructureAccession(),
                        pocket.getChain(),
                        pocket.getModelNumber()
                ),
                new ReceptorSummary(
                        pocket.getReceptorId(),
                        pocket.getTargetName()
                ),
                new ArtifactSummary(
                        pocket.getArtifactId(),
                        pocket.getArtifactFilename(),
                        pocket.getArtifactLabel(),
                        pocket.getArtifactStorageLocation()
                ),
                residues
        );
    }

    private PocketSummary toSummary(PocketDetailsProjection pocket) {
        return new PocketSummary(
                pocket.getId(),
                pocket.getPocketNumber(),
                pocket.getSource(),
                pocket.getVolume(),
                pocket.getDruggabilityScore(),
                pocket.getArtifactId()
        );
    }

    private List<ResidueDetails> residues(long pocketId) {
        return pocketRepository.findResiduesByPocketId(pocketId)
                .stream()
                .map(this::toResidueDetails)
                .toList();
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

    public record PocketDetails(
            long id,
            int pocketNumber,
            String source,
            Double volume,
            Double druggabilityScore,
            StructureSummary structure,
            ReceptorSummary receptor,
            ArtifactSummary artifact,
            List<ResidueDetails> residues
    ) {
        public PocketDetails {
            residues = List.copyOf(residues);
        }
    }

    public record PocketSummary(
            long id,
            int pocketNumber,
            String source,
            Double volume,
            Double druggabilityScore,
            long artifactId
    ) {
    }

    public record StructureSummary(
            long id,
            String source,
            String accession,
            String chain,
            Integer modelNumber
    ) {
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

    public record ResidueDetails(
            long id,
            String chain,
            int residueNumber,
            String insertionCode,
            String residueName
    ) {
    }
}
