package totah.lab.report.analysis;

import totah.lab.pocket.Pocket;
import totah.lab.protein.Structure;
import totah.lab.report.config.PocketReportConfiguration;

public interface PocketGeometryAnalyzer {

    PocketAnalysisResult analyze(
            Pocket pocket,
            Structure structure,
            PocketReportConfiguration configuration
    );
}
