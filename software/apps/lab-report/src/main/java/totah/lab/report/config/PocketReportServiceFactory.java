package totah.lab.report.config;

import totah.lab.report.analysis.DefaultPocketDockingAnalyzer;
import totah.lab.report.analysis.DefaultPocketGeometryAnalyzer;
import totah.lab.report.analysis.DefaultPocketHotspotAnalyzer;
import totah.lab.report.analysis.DefaultPocketResidueAnalyzer;
import totah.lab.report.analysis.PocketReportService;
import totah.lab.report.validation.PocketReportValidator;

/**
 * Creates the deterministic report service without coupling this module to a
 * Spring application context.
 */
public final class PocketReportServiceFactory {

    private PocketReportServiceFactory() {
    }

    public static PocketReportService createDefault() {
        return new PocketReportService(
                new DefaultPocketGeometryAnalyzer(),
                new DefaultPocketResidueAnalyzer(),
                new DefaultPocketDockingAnalyzer(),
                new DefaultPocketHotspotAnalyzer(),
                new PocketReportValidator()
        );
    }
}
