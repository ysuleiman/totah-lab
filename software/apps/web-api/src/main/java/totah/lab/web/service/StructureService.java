package totah.lab.web.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.web.persistence.PocketResidueProjection;
import totah.lab.web.persistence.StructureDetailsProjection;
import totah.lab.web.persistence.StructureRepository;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class StructureService {

    private final StructureRepository structureRepository;
    private final StructureArtifactService structureArtifactService;

    public StructureService(
            StructureRepository structureRepository,
            StructureArtifactService structureArtifactService
    ) {
        this.structureRepository = structureRepository;
        this.structureArtifactService = structureArtifactService;
    }

    @Transactional(readOnly = true)
    public ResidueNeighborhood getResidueNeighbors(
            long structureId,
            long residueId,
            double cutoff
    ) throws IOException {
        if (!Double.isFinite(cutoff) || cutoff <= 0.0 || cutoff > 20.0) {
            throw new IllegalArgumentException(
                    "Cutoff must be greater than zero and at most 20 Å"
            );
        }

        StructureDetailsProjection structure = structureRepository
                .findStructureDetails(structureId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Structure not found: " + structureId
                ));
        List<PocketResidueProjection> databaseResidues =
                structureRepository.findResiduesByStructureId(structureId);
        PocketResidueProjection selectedRow = databaseResidues.stream()
                .filter(residue -> residue.getId() == residueId)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Residue not found in structure: " + residueId
                ));

        Structure artifactStructure =
                structureArtifactService.load(
                        structure.getArtifactId(),
                        structure.getArtifactStorageLocation()
                );
        List<ResidueEntry> residues = residues(artifactStructure);
        ResidueEntry selected = findArtifactResidue(
                residues,
                selectedRow
        );
        Map<ResidueKey, PocketResidueProjection> rowsByKey =
                databaseResidues.stream().collect(Collectors.toMap(
                        this::key,
                        Function.identity()
                ));

        List<NeighborDetails> neighbors = residues.stream()
                .filter(candidate -> !candidate.key().equals(selected.key()))
                .filter(candidate -> areNeighbors(
                        selected.residue(), candidate.residue(), cutoff))
                .map(neighbor -> toNeighbor(
                        neighbor,
                        selected,
                        rowsByKey.get(neighbor.key())
                ))
                .filter(neighbor -> neighbor.id() != null)
                .sorted(Comparator.comparingDouble(
                        NeighborDetails::distance
                ))
                .toList();

        return new ResidueNeighborhood(
                toResidueDetails(selectedRow),
                atomNames(selected.residue()),
                cutoff,
                neighbors
        );
    }

    @Transactional(readOnly = true)
    public AtomDistance getAtomDistance(
            long structureId,
            long firstResidueId,
            long secondResidueId,
            String firstAtomName,
            String secondAtomName
    ) throws IOException {
        StructureDetailsProjection structure = structureRepository
                .findStructureDetails(structureId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Structure not found: " + structureId
                ));
        List<PocketResidueProjection> databaseResidues =
                structureRepository.findResiduesByStructureId(structureId);
        PocketResidueProjection firstRow = findDatabaseResidue(
                databaseResidues,
                firstResidueId
        );
        PocketResidueProjection secondRow = findDatabaseResidue(
                databaseResidues,
                secondResidueId
        );
        Structure artifactStructure =
                structureArtifactService.load(
                        structure.getArtifactId(),
                        structure.getArtifactStorageLocation()
                );
        List<ResidueEntry> residues = residues(artifactStructure);
        ResidueEntry first = findArtifactResidue(
                residues,
                firstRow
        );
        ResidueEntry second = findArtifactResidue(
                residues,
                secondRow
        );
        String normalizedFirstAtom = normalizeAtomName(firstAtomName);
        String normalizedSecondAtom = normalizeAtomName(secondAtomName);

        return new AtomDistance(
                toResidueDetails(firstRow),
                normalizedFirstAtom,
                toResidueDetails(secondRow),
                normalizedSecondAtom,
                atomDistance(
                        first.residue(),
                        normalizedFirstAtom,
                        second.residue(),
                        normalizedSecondAtom
                )
        );
    }

    private PocketResidueProjection findDatabaseResidue(
            List<PocketResidueProjection> residues,
            long residueId
    ) {
        return residues.stream()
                .filter(residue -> residue.getId() == residueId)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Residue not found in structure: " + residueId
                ));
    }

    private String normalizeAtomName(String atomName) {
        if (atomName == null || atomName.isBlank()) {
            throw new IllegalArgumentException("Atom name is required");
        }
        return atomName.trim().toUpperCase();
    }

    private ResidueEntry findArtifactResidue(
            List<ResidueEntry> residues,
            PocketResidueProjection selected
    ) {
        return residues.stream()
                .filter(residue -> residue.key().equals(key(selected)))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Residue coordinates not found in structure artifact"
                ));
    }

    private NeighborDetails toNeighbor(
            ResidueEntry neighbor,
            ResidueEntry selected,
            PocketResidueProjection row
    ) {
        return new NeighborDetails(
                row == null ? null : row.getId(),
                neighbor.chainId(),
                neighbor.residue().getNumber(),
                insertionCode(neighbor.residue()),
                neighbor.residue().getName(),
                atomNames(neighbor.residue()),
                residueDistance(selected.residue(), neighbor.residue())
        );
    }

    private List<String> atomNames(Residue residue) {
        return residue.getAtoms().stream()
                .map(atom -> atom.getName())
                .toList();
    }

    private ResidueKey key(PocketResidueProjection residue) {
        return new ResidueKey(
                residue.getChain(),
                residue.getResidueNumber(),
                normalizeInsertionCode(residue.getInsertionCode())
        );
    }

    private List<ResidueEntry> residues(Structure structure) {
        return structure.getChains().stream()
                .flatMap(chain -> chain.residues().stream()
                        .map(residue -> new ResidueEntry(chain.id(), residue)))
                .toList();
    }

    private String insertionCode(Residue residue) {
        return residue.getInsertionCode() == null
                ? ""
                : String.valueOf(residue.getInsertionCode());
    }

    private boolean areNeighbors(
            Residue first,
            Residue second,
            double cutoff) {
        double cutoffSquared = cutoff * cutoff;
        return first.getAtoms().stream()
                .filter(Atom::isHeavyAtom)
                .anyMatch(firstAtom -> second.getAtoms().stream()
                        .filter(Atom::isHeavyAtom)
                        .anyMatch(secondAtom -> firstAtom.getPosition()
                                .distanceSquared(secondAtom.getPosition())
                                <= cutoffSquared));
    }

    private double residueDistance(Residue first, Residue second) {
        return first.getAtoms().stream()
                .filter(Atom::isHeavyAtom)
                .flatMapToDouble(firstAtom -> second.getAtoms().stream()
                        .filter(Atom::isHeavyAtom)
                        .mapToDouble(secondAtom -> firstAtom.getPosition()
                                .distance(secondAtom.getPosition())))
                .min()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Residues must contain heavy atoms"));
    }

    private double atomDistance(
            Residue first,
            String firstAtomName,
            Residue second,
            String secondAtomName) {
        Atom firstAtom = first.findAtom(firstAtomName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Atom not found: " + firstAtomName));
        Atom secondAtom = second.findAtom(secondAtomName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Atom not found: " + secondAtomName));
        return firstAtom.getPosition().distance(secondAtom.getPosition());
    }

    private String normalizeInsertionCode(String insertionCode) {
        return insertionCode == null ? "" : insertionCode.trim();
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
                        structure.getTargetName(),
                        structure.getUniProtId(),
                        structure.getProteinName(),
                        structure.getGeneName(),
                        structure.getOrganism()
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
            String targetName,
            String uniProtId,
            String proteinName,
            String geneName,
            String organism
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

    public record ResidueNeighborhood(
            ResidueDetails selectedResidue,
            List<String> selectedAtomNames,
            double cutoff,
            List<NeighborDetails> neighbors
    ) {
        public ResidueNeighborhood {
            selectedAtomNames = List.copyOf(selectedAtomNames);
            neighbors = List.copyOf(neighbors);
        }
    }

    public record NeighborDetails(
            Long id,
            String chain,
            int residueNumber,
            String insertionCode,
            String residueName,
            List<String> atomNames,
            double distance
    ) {
        public NeighborDetails {
            atomNames = List.copyOf(atomNames);
        }
    }

    public record AtomDistance(
            ResidueDetails firstResidue,
            String firstAtom,
            ResidueDetails secondResidue,
            String secondAtom,
            double distance
    ) {
    }

    private record ResidueKey(
            String chain,
            int residueNumber,
            String insertionCode
    ) {
    }

    private record ResidueEntry(
            String chainId,
            Residue residue
    ) {
        private ResidueKey key() {
            Character insertionCode = residue.getInsertionCode();
            return new ResidueKey(
                    chainId,
                    residue.getNumber(),
                    insertionCode == null
                            ? ""
                            : String.valueOf(insertionCode).trim());
        }
    }
}
