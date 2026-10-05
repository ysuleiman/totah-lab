package totah.lab.daedalus.system;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class DirectAssessmentDiskRetryTest {
    @Test void retryExactFailedCheckVariantAfterTransientDiskExhaustion(@TempDir Path directory)throws Exception {
        var result=DirectAssessmentExecutionAcceptanceTest.run(directory,"failed-check");
        assertEquals(0,result.calls());assertTrue(DirectAssessmentExecutionAcceptanceTest.findings(result).isEmpty());
        assertTrue(result.history().interpretations().values().stream().anyMatch(i->i.status()==totah.lab.mnemosyne.EvidenceInterpretation.Status.FAILED||i.status()==totah.lab.mnemosyne.EvidenceInterpretation.Status.UNSUPPORTED));
    }
}
