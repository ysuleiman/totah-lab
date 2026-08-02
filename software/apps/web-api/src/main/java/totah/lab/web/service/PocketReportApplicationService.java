package totah.lab.web.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.pocket.Pocket;
import totah.lab.pocket.PocketSource;
import totah.lab.pocket.ResidueRef;
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
        Map<String, Object> attributes = new LinkedHashMap<>();
        putIfPresent(attributes, "volume", details.volume());
        putIfPresent(attributes, "druggability_score",
                details.druggabilityScore());
        putIfPresent(attributes, "probability", details.probability());
        attributes.put("sourcePocketNumber", details.pocketNumber());
        attributes.put("internalPocketId", details.id());

        List<ResidueRef> residueReferences = details.residues().stream()
                .map(residue -> new ResidueRef(
                        residue.chain(),
                        residue.residueNumber(),
                        residue.residueName()
                ))
                .toList();
        return Pocket.builder()
                .id(details.id())
                .name(details.source() + " pocket "
                        + details.pocketNumber())
                .score(details.score())
                .source(source(details.source()))
                .residueRefs(residueReferences)
                .attributes(attributes)
                .build();
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

    private void putIfPresent(
            Map<String, Object> attributes,
            String name,
            Object value
    ) {
        if (value != null) {
            attributes.put(name, value);
        }
    }

    public record PocketReportDocument(
            PocketReport report,
            PocketNarrative narrative
    ) {
    }
}
