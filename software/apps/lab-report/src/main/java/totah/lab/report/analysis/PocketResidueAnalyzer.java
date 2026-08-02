package totah.lab.report.analysis;

import totah.lab.pocket.Pocket;
import totah.lab.gaia.structure.Structure;
import totah.lab.report.config.PocketReportConfiguration;

public interface PocketResidueAnalyzer {

    PocketAnalysisResult analyze(
            Pocket pocket,
            Structure structure,
            PocketAnalysisResult geometry,
            PocketReportConfiguration configuration
    );
}
