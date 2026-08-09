package totah.lab.web.service;

import totah.lab.web.chemistry.ResidueChemistryView;
import totah.lab.web.chemistry.ResidueChemistryViewMapper;

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
    private final BiohubPocketEvidenceService evidenceService;

    public PocketService(
            PocketRepository pocketRepository,
            BiohubPocketEvidenceService evidenceService
    ) {
        this.pocketRepository = pocketRepository;
        this.evidenceService = evidenceService;
    }

    @Transactional(readOnly = true)
    public PocketDetails getPocket(long pocketId) {
        PocketDetailsProjection pocket = pocketRepository
                .findPocketDetails(pocketId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Pocket not found: " + pocketId
                ));
        List<ResidueDetails> residues = residues(pocketId);
        return toDetails(
                pocket,
                residues,
                evidenceService.read(pocket, residues)
        );
    }

    @Transactional(readOnly = true)
    public List<PocketSummary> getPocketsForStructure(long structureId) {
        return pocketRepository.findPocketDetailsByStructureId(structureId)
                .stream()
                .map(pocket -> {
                    if (!"BIOHUB".equals(pocket.getSource())) {
                        return toSummary(pocket, null);
                    }
                    List<ResidueDetails> residues =
                            residues(pocket.getId());
                    return toSummary(
                            pocket,
                            evidenceService.read(pocket, residues)
                    );
                })
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
            List<ResidueDetails> residues,
            PocketEvidence evidence
    ) {
        return new PocketDetails(
                pocket.getId(),
                pocket.getPocketNumber(),
                pocket.getSource(),
                pocket.getVolume(),
                pocket.getScore(),
                pocket.getDruggabilityScore(),
                pocket.getProbability(),
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
                residues,
                evidence
        );
    }

    private PocketSummary toSummary(
            PocketDetailsProjection pocket,
            PocketEvidence evidence
    ) {
        return new PocketSummary(
                pocket.getId(),
                pocket.getPocketNumber(),
                pocket.getSource(),
                pocket.getVolume(),
                pocket.getScore(),
                pocket.getDruggabilityScore(),
                pocket.getProbability(),
                pocket.getArtifactId(),
                evidence
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
                residue.getResidueName(),
                ResidueChemistryViewMapper.map(residue.getResidueName())
        );
    }

    public record PocketDetails(
            long id,
            int pocketNumber,
            String source,
            Double volume,
            Double score,
            Double druggabilityScore,
            Double probability,
            StructureSummary structure,
            ReceptorSummary receptor,
            ArtifactSummary artifact,
            List<ResidueDetails> residues,
            PocketEvidence evidence
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
            Double score,
            Double druggabilityScore,
            Double probability,
            long artifactId,
            PocketEvidence evidence
    ) {
    }

    public record PocketEvidence(
            String ligandCcd,
            String model,
            double shellCutoff,
            double directContactCutoff,
            Double ptm,
            Double interfacePtm,
            int shellResidueCount,
            int directContactResidueCount,
            int chosenPocketOverlapCount,
            int directChosenPocketOverlapCount,
            List<Long> shellResidueIds,
            List<Long> directContactResidueIds,
            List<Long> chosenPocketOverlapResidueIds,
            List<Long> directChosenPocketOverlapResidueIds,
            List<PocketResidueEvidence> residueEvidence
    ) {
        public PocketEvidence {
            shellResidueIds = List.copyOf(shellResidueIds);
            directContactResidueIds =
                    List.copyOf(directContactResidueIds);
            chosenPocketOverlapResidueIds =
                    List.copyOf(chosenPocketOverlapResidueIds);
            directChosenPocketOverlapResidueIds =
                    List.copyOf(directChosenPocketOverlapResidueIds);
            residueEvidence = List.copyOf(residueEvidence);
        }
    }

    public record PocketResidueEvidence(
            long residueId,
            String chain,
            int residueNumber,
            String residueName,
            double minimumDistance,
            int contactingAtomPairCount,
            boolean directContact,
            boolean chosenPocketMember
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
            String residueName,
            ResidueChemistryView chemistry
    ) {
        public ResidueDetails(
                long id,
                String chain,
                int residueNumber,
                String insertionCode,
                String residueName
        ) {
            this(id, chain, residueNumber, insertionCode, residueName,
                    ResidueChemistryViewMapper.map(residueName));
        }
    }
}
