package totah.lab.mettl7.triage;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RetrospectiveCalibrationTest {
    @Test
    void allFourteenReferencePanelCasesPassWithoutChangingRules() throws Exception {
        Path csv = Path.of(System.getProperty("user.dir"), "RETROSPECTIVE_CALIBRATION.csv");
        var results = new RetrospectiveCalibration().evaluate(csv, new Mettl7LigandTriageService());
        assertThat(results).hasSize(14);
        assertThat(results).allSatisfy(result -> assertThat(result.passed())
                .as(result.identifier() + " " + result.mismatches()).isTrue());
    }
}
