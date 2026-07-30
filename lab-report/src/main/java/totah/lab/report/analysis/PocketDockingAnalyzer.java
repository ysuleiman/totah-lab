package totah.lab.report.analysis;

import totah.lab.pocket.Pocket;
import totah.lab.report.config.PocketReportConfiguration;

import java.util.Map;

public interface PocketDockingAnalyzer {

    PocketAnalysisResult analyze(
            Pocket pocket,
            Map<String, Object> aggregatedDockingData,
            PocketReportConfiguration configuration
    );
}
