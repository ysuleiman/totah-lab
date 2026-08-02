package totah.lab.web.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketMetric;
import totah.lab.gaia.pocket.PocketMetricType;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.report.config.PocketReportConfiguration;
import totah.lab.report.config.PocketReportServiceFactory;
import totah.lab.report.model.PocketReport;
import totah.lab.report.narrative.EvidenceLinkedPocketNarrativeGenerator;
import totah.lab.report.narrative.PocketNarrative;
import totah.lab.report.validation.NarrativeEvidenceValidator;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PocketReportApplicationService {

    private final PocketService pocketService;
    private final StructureService structureService;
    private final StructureArtifactService structureArtifactService;
    private final DockingAnalysisService dockingAnalysisService;
    private final DockingReportAggregateMapper aggregateMapper;

    public PocketReportApplicationService(
            PocketService pocketService,
            StructureService structureService,
            StructureArtifactService structureArtifactService,
            DockingAnalysisService dockingAnalysisService,
            DockingReportAggregateMapper aggregateMapper
    ) {
        this.pocketService = pocketService;
        this.structureService = structureService;
        this.structureArtifactService = structureArtifactService;
        this.dockingAnalysisService = dockingAnalysisService;
        this.aggregateMapper = aggregateMapper;
    }

    @Transactional(readOnly = true)
    public PocketReport generate(long pocketId, long runId)
            throws IOException {
        PocketService.PocketDetails details =
                pocketService.getPocket(pocketId);
        StructureService.StructureDetails structureDetails =
                structureService.getStructure(details.structure().id());
        DockingAnalysisService.DockingRunSummary run =
                dockingAnalysisService
                        .getRunsForStructure(details.structure().id())
                        .stream()
                        .filter(candidate -> candidate.id() == runId)
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(
                                NOT_FOUND,
                                "Docking run " + runId
                                        + " does not belong to structure "
                                        + details.structure().id()
                        ));
        List<DockingAnalysisService.ResidueAnalysis> residueSummary =
                dockingAnalysisService.getResidueSummary(runId);
        List<DockingAnalysisService.ResidueScoreBand> scoreBands =
                dockingAnalysisService.getResidueScoreBands(runId, null);
        totah.lab.gaia.structure.Structure structure =
                structureArtifactService.load(
                        structureDetails.artifact().id(),
                        structureDetails.artifact().storageLocation()
                );

        return PocketReportServiceFactory.createDefault().generate(
                toDomainPocket(details),
                structure,
                aggregateMapper.map(run, residueSummary, scoreBands),
                PocketReportConfiguration.defaults()
        );
    }

    @Transactional(readOnly = true)
    public PocketReportDocument generateDocument(long pocketId, long runId)
            throws IOException {
        PocketReport report = generate(pocketId, runId);
        PocketNarrative narrative =
                new EvidenceLinkedPocketNarrativeGenerator()
                        .generate(report);
        new NarrativeEvidenceValidator().validate(report, narrative);
        return new PocketReportDocument(report, narrative);
    }

    private Pocket toDomainPocket(PocketService.PocketDetails details) {
        PocketSource source = source(details.source());

        List<PocketMetric> metrics = new java.util.ArrayList<>();
        putMetric(metrics, PocketMetricType.VOLUME, details.volume());
        putMetric(metrics, PocketMetricType.FPOCKET_DRUGGABILITY,
                details.druggabilityScore());
        putMetric(metrics, PocketMetricType.P2RANK_PROBABILITY,
                details.probability());
        if (details.score() != null && Double.isFinite(details.score())) {
            metrics.add(new PocketMetric(
                    source == PocketSource.P2RANK
                            ? PocketMetricType.P2RANK_PROBABILITY
                            : PocketMetricType.FPOCKET_SCORE,
                    details.score()
            ));
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("sourcePocketNumber",
                String.valueOf(details.pocketNumber()));
        metadata.put("internalPocketId", String.valueOf(details.id()));

        List<ResidueId> residues = details.residues().stream()
                .map(residue -> new ResidueId(
                        residue.chain(),
                        residue.residueNumber(),
                        insertionCode(residue.insertionCode())
                ))
                .toList();

        return new Pocket(
                PocketId.of(details.id()),
                details.source() + " pocket " + details.pocketNumber(),
                source,
                // Center is not stored with the source pocket and is not
                // used by the report analyzers; a fixed origin placeholder
                // satisfies the gaia model.
                new Point3D(0.0, 0.0, 0.0),
                residues,
                metrics,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                metadata
        );
    }

    private PocketSource source(String value) {
        try {
            return PocketSource.valueOf(
                    value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Unsupported pocket source: " + value,
                    exception
            );
        }
    }

    private Character insertionCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.charAt(0);
    }

    private void putMetric(
            List<PocketMetric> metrics,
            PocketMetricType type,
            Double value
    ) {
        if (value != null && Double.isFinite(value)) {
            metrics.add(new PocketMetric(type, value));
        }
    }

    public record PocketReportDocument(
            PocketReport report,
            PocketNarrative narrative
    ) {
    }
}
