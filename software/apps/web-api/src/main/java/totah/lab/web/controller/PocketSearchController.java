package totah.lab.web.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import totah.lab.web.service.PocketComparisonDetails;
import totah.lab.web.service.PocketComparisonReportService;
import totah.lab.web.service.PocketComparisonReportView;
import totah.lab.web.service.PocketGeometryView;
import totah.lab.web.service.PocketSimilarityDiagnostic;
import totah.lab.web.service.PocketSimilarityService;

import java.util.List;

@RestController
@RequestMapping("/api/pockets")
public class PocketSearchController {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final PocketSimilarityService pocketSimilarityService;
    private final PocketComparisonReportService pocketComparisonReportService;

    public PocketSearchController(
            PocketSimilarityService pocketSimilarityService,
            PocketComparisonReportService pocketComparisonReportService
    ) {
        this.pocketSimilarityService = pocketSimilarityService;
        this.pocketComparisonReportService =
                pocketComparisonReportService;
    }

    @GetMapping("/{pocketId}/similar")
    public List<PocketCandidateResponse> findSimilarPockets(
            @PathVariable("pocketId") long pocketId,
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {

        int normalizedLimit = normalizeLimit(limit);

        return pocketSimilarityService.findSimilar(
                        pocketId,
                        normalizedLimit
                )
                .stream()
                .map(candidate -> new PocketCandidateResponse(
                        candidate.pocketId(),
                        candidate.structureId(),
                        candidate.sourceAccession(),
                        candidate.pocketNumber(),
                        candidate.descriptorDistance(),
                        candidate.volumeDistance(),
                        candidate.residueDistance(),
                        candidate.chemistryDistance()
                ))
                .toList();
    }

    @GetMapping("/{pocketId}/similar/diagnostic")
    public List<PocketSimilarityDiagnostic> diagnoseSimilarPockets(
            @PathVariable("pocketId") long pocketId,
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {

        int normalizedLimit = normalizeLimit(limit);

        return pocketSimilarityService.diagnoseSimilar(
                pocketId,
                normalizedLimit
        );
    }

    @GetMapping("/{pocketId}/geometry")
    public PocketGeometryView getGeometry(
            @PathVariable("pocketId") long pocketId
    ) {
        return pocketSimilarityService.getGeometry(pocketId);
    }

    @GetMapping("/{queryPocketId}/compare/{candidatePocketId}")
    public PocketComparisonDetails compareGeometries(
            @PathVariable("queryPocketId") long queryPocketId,
            @PathVariable("candidatePocketId") long candidatePocketId
    ) {

        return pocketSimilarityService.compareGeometries(
                queryPocketId,
                candidatePocketId
        );
    }

    /**
     * The structured evidence report of the same pairwise comparison:
     * the seven sections assembled from the live comparison path,
     * with the rules verdict and its reason as the interpretation.
     */
    @GetMapping("/{queryPocketId}/compare/{candidatePocketId}/report")
    public PocketComparisonReportView comparisonReport(
            @PathVariable("queryPocketId") long queryPocketId,
            @PathVariable("candidatePocketId") long candidatePocketId
    ) {

        return pocketComparisonReportService.report(
                queryPocketId,
                candidatePocketId
        );
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException(
                    "Limit must be greater than zero"
            );
        }

        return Math.min(limit, MAX_LIMIT);
    }
}