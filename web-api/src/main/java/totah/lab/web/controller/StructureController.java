package totah.lab.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import totah.lab.web.service.StructureService;

import java.io.IOException;

@RestController
@RequestMapping("/api/structures")
public final class StructureController {

    private final StructureService structureService;

    public StructureController(StructureService structureService) {
        this.structureService = structureService;
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
}
