package totah.lab.prometheus.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.reporting.StrategyPlanningReportRenderer.CostComparisonRow;
import totah.lab.prometheus.reporting.StrategyPlanningReportRenderer.MissingEvidenceRow;
import totah.lab.prometheus.reporting.StrategyPlanningReportRenderer.RequirementRow;
import totah.lab.prometheus.reporting.StrategyPlanningReportRenderer.ReuseRow;
import totah.lab.prometheus.reporting.StrategyPlanningReportRenderer.StrategyPlanningReport;

class StrategyPlanningReportRendererTest {
    @TempDir Path temporary;

    @Test
    void rendersDeterministicCompletePackage() throws Exception {
        RequirementRow requirement = new RequirementRow("method", "hessian", "PBE", true,
                "DEVELOPMENT", "REUSE_EXISTING", 3, "projection", "authoritative", "angles");
        StrategyPlanningReport report = new StrategyPlanningReport("model", List.of(requirement),
                List.of(requirement), List.of(requirement),
                List.of(new ReuseRow("method", "hessian", "HESSIAN", "REUSE_EXISTING", 3,
                        "PBE", "abc", "authoritative")), "holdout",
                List.of(new MissingEvidenceRow("method", "forces", "gradients", "missing", "PBE", 2,
                        "QM", "2 h", "1 h", "$1", "CPU", false, "split")),
                List.of(new CostComparisonRow("method", "suitable", 3, 1, 2, 0, "QM", "2 h", "$1",
                        "DIRECT", "DIRECT", "strong", "risk", "READY_AFTER_MINIMAL_NEW_EVIDENCE")),
                "recommendation", "{}\n");
        Path first = temporary.resolve("first");
        Path second = temporary.resolve("second");
        StrategyPlanningReportRenderer renderer = new StrategyPlanningReportRenderer();
        renderer.render(first, report);
        renderer.render(second, report);

        List<String> names = Files.list(first).map(path -> path.getFileName().toString()).sorted().toList();
        assertThat(names).hasSize(11).contains("SHA256SUMS", "PROMETHEUS_TSL_STRATEGY_DECISION.json");
        for (String name : names) {
            assertThat(Files.readAllBytes(first.resolve(name))).isEqualTo(Files.readAllBytes(second.resolve(name)));
        }
        assertThat(Files.readString(first.resolve("SHA256SUMS"))).hasLineCount(10);
    }
}
