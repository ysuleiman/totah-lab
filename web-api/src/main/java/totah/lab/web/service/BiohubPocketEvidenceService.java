package totah.lab.web.service;

import org.springframework.stereotype.Service;
import totah.lab.http.biohub.artifact.BiohubPocketEvidenceReader;
import totah.lab.http.biohub.model.BiohubPocketEvidence;
import totah.lab.web.persistence.PocketDetailsProjection;
import totah.lab.web.persistence.PocketRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public final class BiohubPocketEvidenceService {

    private final PocketRepository pocketRepository;
    private final BiohubPocketEvidenceReader reader =
            new BiohubPocketEvidenceReader();

    public BiohubPocketEvidenceService(PocketRepository pocketRepository) {
        this.pocketRepository = pocketRepository;
    }

    public PocketService.PocketEvidence read(
            PocketDetailsProjection pocket,
            List<PocketService.ResidueDetails> residues
    ) {
        if (!"BIOHUB".equals(pocket.getSource())) {
            return null;
        }
        try {
            BiohubPocketEvidence evidence = reader.read(Path.of(
                    pocket.getArtifactStorageLocation()
            ));
            return map(
                    evidence,
                    residues,
                    Set.copyOf(
                            pocketRepository.findChosenPocketResidueIds(
                                    pocket.getStructureId()
                            )
                    )
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot read BioHub pocket artifact "
                            + pocket.getArtifactStorageLocation(),
                    exception
            );
        }
    }

    static PocketService.PocketEvidence map(
            BiohubPocketEvidence evidence,
            List<PocketService.ResidueDetails> residues,
            Set<Long> chosenResidueIds
    ) {
        Map<ResidueKey, Long> residueIds = new HashMap<>();
        for (PocketService.ResidueDetails residue : residues) {
            residueIds.put(
                    new ResidueKey(
                            residue.chain().trim(),
                            residue.residueNumber()
                    ),
                    residue.id()
            );
        }
        Set<Long> directResidueIds = new HashSet<>();
        for (BiohubPocketEvidence.ResidueContact contact
                : evidence.residues()) {
            Long residueId = residueIds.get(new ResidueKey(
                    contact.chain(),
                    contact.residueNumber()
            ));
            if (residueId == null) {
                throw new IllegalStateException(
                        "BioHub residue is not present in pocket membership: "
                                + contact.chain()
                                + contact.residueNumber()
                );
            }
            if (contact.directContact()) {
                directResidueIds.add(residueId);
            }
        }

        List<Long> overlapResidueIds = residues.stream()
                .map(PocketService.ResidueDetails::id)
                .filter(chosenResidueIds::contains)
                .toList();
        List<Long> directOverlapResidueIds = directResidueIds.stream()
                .filter(chosenResidueIds::contains)
                .sorted()
                .toList();
        return new PocketService.PocketEvidence(
                evidence.ligandCcd(),
                evidence.model(),
                evidence.shellCutoff(),
                evidence.directContactCutoff(),
                evidence.ptm(),
                evidence.interfacePtm(),
                residues.size(),
                directResidueIds.size(),
                overlapResidueIds.size(),
                directOverlapResidueIds.size(),
                residues.stream()
                        .map(PocketService.ResidueDetails::id)
                        .toList(),
                directResidueIds.stream().sorted().toList(),
                overlapResidueIds,
                directOverlapResidueIds
        );
    }

    private record ResidueKey(String chain, int residueNumber) {
    }
}
