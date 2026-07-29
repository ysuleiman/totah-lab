package totah.lab.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import totah.lab.web.service.StructureService;

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
}
