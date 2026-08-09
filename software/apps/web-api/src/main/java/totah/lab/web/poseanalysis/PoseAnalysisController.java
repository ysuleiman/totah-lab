package totah.lab.web.poseanalysis;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import totah.lab.web.poseanalysis.PoseAnalysisView.ContactProfileView;
import totah.lab.web.poseanalysis.PoseAnalysisView.CrossProteinPoseComparisonView;
import totah.lab.web.poseanalysis.PoseAnalysisView.DockingTargetView;
import totah.lab.web.poseanalysis.PoseAnalysisView.LigandAnalysisView;
import totah.lab.web.poseanalysis.PoseAnalysisView.LigandOptionView;
import totah.lab.web.poseanalysis.PoseAnalysisView.LigandRunOptionView;
import totah.lab.web.poseanalysis.PoseAnalysisView.PocketOccupancyView;
import totah.lab.web.poseanalysis.PoseAnalysisView.PosePocketAssignmentView;

import java.io.IOException;
import java.util.List;

/**
 * Generic receptor-ligand pose analysis: the ligands docked against a
 * structure, and the per-run pose analysis of one selected ligand.
 */
@RestController
@RequestMapping("/api")
public final class PoseAnalysisController {

    private final PoseAnalysisService service;

    public PoseAnalysisController(PoseAnalysisService service) {
        this.service = service;
    }

    @GetMapping("/docking-targets")
    public List<DockingTargetView> targets() {
        return service.targets();
    }

    @GetMapping("/targets/{receptorId}/docking-ligands")
    public List<LigandOptionView> ligands(
            @PathVariable("receptorId") long receptorId,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        return service.ligands(receptorId, query, limit);
    }

    @GetMapping("/targets/{receptorId}/docking-ligand-runs")
    public List<LigandRunOptionView> ligandRuns(
            @PathVariable("receptorId") long receptorId,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "100") int limit
    ) {
        return service.ligandRuns(receptorId, query, limit);
    }

    @GetMapping("/targets/{receptorId}/ligand-analysis")
    public LigandAnalysisView analysis(
            @PathVariable("receptorId") long receptorId,
            @RequestParam("ligandId") String ligandId
    ) throws IOException {
        return service.analysis(receptorId, ligandId);
    }

    @GetMapping(value = "/docking-poses/{poseId}/file",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> poseFile(
            @PathVariable("poseId") long poseId
    ) throws IOException {
        return ResponseEntity.ok(service.poseFileContent(poseId));
    }

    @GetMapping("/docking-poses/{poseId}/contact-profile")
    public ContactProfileView poseContactProfile(
            @PathVariable("poseId") long poseId
    ) {
        return service.poseContactProfile(poseId);
    }

    /**
     * The pocket a Vina pose is assigned to (never "binds"): status,
     * deciding reason, assigned pocket, assignment score kept separate
     * from the Vina affinity, and the component metrics.
     */
    @GetMapping("/docking-poses/{poseId}/pocket-assignment")
    public PosePocketAssignmentView pocketAssignment(
            @PathVariable("poseId") long poseId
    ) {
        return service.pocketAssignment(poseId);
    }

    /**
     * Pose-frequency occupancy of a run's pockets: every pose of the
     * run assigned to a candidate pocket, aggregated per pocket.
     */
    @GetMapping("/docking-runs/{runId}/pocket-occupancy")
    public PocketOccupancyView pocketOccupancy(
            @PathVariable("runId") long runId
    ) {
        return service.pocketOccupancy(runId);
    }

    /**
     * Whether two poses — each docked against its own receptor —
     * occupy structurally homologous sites. Poses of the same receptor
     * are a valid input.
     */
    @GetMapping("/docking-poses/{poseId}/cross-protein-comparison")
    public CrossProteinPoseComparisonView crossProteinComparison(
            @PathVariable("poseId") long poseId,
            @RequestParam("otherPoseId") long otherPoseId
    ) {
        return service.crossProteinComparison(poseId, otherPoseId);
    }

    @GetMapping(value = "/docking-runs/{runId}/receptor-file",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> receptorFile(
            @PathVariable("runId") long runId
    ) throws IOException {
        return ResponseEntity.ok(service.receptorFileContent(runId));
    }
}
