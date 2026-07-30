package totah.lab.report.analysis;

import totah.lab.pocket.Pocket;
import totah.lab.protein.Structure;
import totah.lab.report.config.PocketReportConfiguration;
import totah.lab.report.evidence.ReportEvidence;
import totah.lab.report.model.PocketReport;
import totah.lab.report.model.PocketReportData;
import totah.lab.report.validation.PocketReportValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PocketReportService {

    private final PocketGeometryAnalyzer geometryAnalyzer;
    private final PocketResidueAnalyzer residueAnalyzer;
    private final PocketDockingAnalyzer dockingAnalyzer;
    private final PocketHotspotAnalyzer hotspotAnalyzer;
    private final PocketReportValidator validator;

    public PocketReportService(
            PocketGeometryAnalyzer geometryAnalyzer,
            PocketResidueAnalyzer residueAnalyzer,
            PocketDockingAnalyzer dockingAnalyzer,
            PocketHotspotAnalyzer hotspotAnalyzer,
            PocketReportValidator validator
    ) {
        this.geometryAnalyzer = Objects.requireNonNull(
                geometryAnalyzer,
                "geometryAnalyzer"
        );
        this.residueAnalyzer = Objects.requireNonNull(
                residueAnalyzer,
                "residueAnalyzer"
        );
        this.dockingAnalyzer = Objects.requireNonNull(
                dockingAnalyzer,
                "dockingAnalyzer"
        );
        this.hotspotAnalyzer = Objects.requireNonNull(
                hotspotAnalyzer,
                "hotspotAnalyzer"
        );
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public PocketReport generate(
            Pocket pocket,
            Structure structure,
            Map<String, Object> aggregatedDockingData,
            PocketReportConfiguration configuration
    ) {
        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(aggregatedDockingData, "aggregatedDockingData");
        Objects.requireNonNull(configuration, "configuration");

        PocketAnalysisResult geometry =
                geometryAnalyzer.analyze(pocket, structure, configuration);
        PocketAnalysisResult residues = residueAnalyzer.analyze(
                pocket,
                structure,
                geometry,
                configuration
        );
        PocketAnalysisResult docking = dockingAnalyzer.analyze(
                pocket,
                aggregatedDockingData,
                configuration
        );
        PocketAnalysisResult hotspots = hotspotAnalyzer.analyze(
                residues,
                docking,
                configuration
        );

        List<ReportEvidence> evidence = new ArrayList<>();
        evidence.addAll(geometry.evidence());
        evidence.addAll(residues.evidence());
        evidence.addAll(docking.evidence());
        evidence.addAll(hotspots.evidence());

        PocketReport report = new PocketReport(
                new PocketReportData(
                        pocket.getId(),
                        pocket.getName(),
                        pocket.getSource(),
                        geometry.values(),
                        residues.values(),
                        docking.values(),
                        hotspots.values()
                ),
                evidence
        );
        validator.validate(report);
        return report;
    }
}
