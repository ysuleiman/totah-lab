package totah.lab.web.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.web.persistence.ResidueEvidenceProjection;
import totah.lab.web.persistence.ResidueEvidenceRepository;
import totah.lab.web.persistence.StructureRepository;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ResidueEvidenceService {

    public static final String ESMC_CONSTRAINT = "ESMC_CONSTRAINT";

    private final StructureRepository structureRepository;
    private final ResidueEvidenceRepository residueEvidenceRepository;

    public ResidueEvidenceService(
            StructureRepository structureRepository,
            ResidueEvidenceRepository residueEvidenceRepository
    ) {
        this.structureRepository = structureRepository;
        this.residueEvidenceRepository = residueEvidenceRepository;
    }

    @Transactional(readOnly = true)
    public List<ResidueEvidence> getEvidence(
            long structureId,
            String analysisType
    ) {
        if (!structureRepository.existsById(structureId)) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "Structure not found: " + structureId
            );
        }
        String normalizedType = normalizeAnalysisType(analysisType);
        return residueEvidenceRepository
                .findLatestByStructureAndType(structureId, normalizedType)
                .stream()
                .map(this::toEvidence)
                .toList();
    }

    private String normalizeAnalysisType(String analysisType) {
        if (analysisType == null || analysisType.isBlank()) {
            return ESMC_CONSTRAINT;
        }
        return analysisType.trim().toUpperCase();
    }

    private ResidueEvidence toEvidence(ResidueEvidenceProjection row) {
        return new ResidueEvidence(
                row.getResidueId(),
                row.getAnalysisType(),
                row.getScore(),
                row.getRank(),
                row.getProvider(),
                row.getModel(),
                row.getBestAlternative(),
                row.getWildTypeMinusBestAlternative(),
                row.getAminoAcidEntropy(),
                row.getArtifactId()
        );
    }

    public record ResidueEvidence(
            long residueId,
            String analysisType,
            Double score,
            Integer rank,
            String provider,
            String model,
            String bestAlternative,
            Double wildTypeMinusBestAlternative,
            Double aminoAcidEntropy,
            long artifactId
    ) {
    }
}
