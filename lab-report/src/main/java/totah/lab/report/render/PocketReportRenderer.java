package totah.lab.report.render;

import totah.lab.report.model.CompletePocketReport;

public interface PocketReportRenderer<T> {

    T render(CompletePocketReport report);
}
