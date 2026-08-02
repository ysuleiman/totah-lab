package totah.lab.report.analysis;

import totah.lab.report.config.PocketReportConfiguration;

public interface PocketHotspotAnalyzer {

    PocketAnalysisResult analyze(
            PocketAnalysisResult residues,
            PocketAnalysisResult docking,
            PocketReportConfiguration configuration
    );
}
