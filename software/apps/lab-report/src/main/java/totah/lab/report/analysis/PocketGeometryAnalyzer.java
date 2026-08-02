package totah.lab.report.analysis;

import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;
import totah.lab.report.config.PocketReportConfiguration;

public interface PocketGeometryAnalyzer {

    PocketAnalysisResult analyze(
            Pocket pocket,
            Structure structure,
            PocketReportConfiguration configuration
    );
}
