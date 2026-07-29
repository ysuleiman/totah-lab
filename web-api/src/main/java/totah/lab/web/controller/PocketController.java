package totah.lab.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import totah.lab.web.service.PocketService;

import java.util.List;

@RestController
@RequestMapping("/api")
public final class PocketController {

    private final PocketService pocketService;

    public PocketController(PocketService pocketService) {
        this.pocketService = pocketService;
    }

    @GetMapping("/pockets/{pocketId}")
    public PocketService.PocketDetails pocket(
            @PathVariable("pocketId") long pocketId
    ) {
        return pocketService.getPocket(pocketId);
    }

    @GetMapping("/pockets/{pocketId}/residues")
    public List<PocketService.ResidueDetails> residues(
            @PathVariable("pocketId") long pocketId
    ) {
        return pocketService.getResiduesForPocket(pocketId);
    }

    @GetMapping("/structures/{structureId}/pockets")
    public List<PocketService.PocketSummary> pocketsForStructure(
            @PathVariable("structureId") long structureId
    ) {
        return pocketService.getPocketsForStructure(structureId);
    }
}
