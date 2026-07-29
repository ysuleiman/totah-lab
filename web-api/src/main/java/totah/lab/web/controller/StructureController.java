package totah.lab.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import totah.lab.web.service.ResidueEvidenceService;
import totah.lab.web.service.StructureService;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/structures")
public final class StructureController {

    private final StructureService structureService;
    private final ResidueEvidenceService residueEvidenceService;

    public StructureController(
            StructureService structureService,
            ResidueEvidenceService residueEvidenceService
    ) {
        this.structureService = structureService;
        this.residueEvidenceService = residueEvidenceService;
    }

    @GetMapping("/{structureId}/residue-evidence")
    public List<ResidueEvidenceService.ResidueEvidence> residueEvidence(
            @PathVariable("structureId") long structureId,
            @RequestParam(
                    name = "analysisType",
                    defaultValue = ResidueEvidenceService.ESMC_CONSTRAINT
            )
            String analysisType
    ) {
        return residueEvidenceService.getEvidence(structureId, analysisType);
    }

    @GetMapping("/{structureId}")
    public StructureService.StructureDetails structure(
            @PathVariable("structureId") long structureId
    ) {
        return structureService.getStructure(structureId);
    }

    @GetMapping("/{structureId}/residues/{residueId}/neighbors")
    public StructureService.ResidueNeighborhood residueNeighbors(
            @PathVariable("structureId") long structureId,
            @PathVariable("residueId") long residueId,
            @RequestParam(name = "cutoff", defaultValue = "6.0")
            double cutoff
    ) throws IOException {
        return structureService.getResidueNeighbors(
                structureId,
                residueId,
                cutoff
        );
    }

    @GetMapping("/{structureId}/residues/{residueId}/distance")
    public StructureService.AtomDistance atomDistance(
            @PathVariable("structureId") long structureId,
            @PathVariable("residueId") long residueId,
            @RequestParam("toResidueId") long toResidueId,
            @RequestParam("fromAtom") String fromAtom,
            @RequestParam("toAtom") String toAtom
    ) throws IOException {
        return structureService.getAtomDistance(
                structureId,
                residueId,
                toResidueId,
                fromAtom,
                toAtom
        );
    }
}
