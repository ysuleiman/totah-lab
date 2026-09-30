package totah.lab.aether;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AetherInteractionRegressionTest {
    @Test void nativeDiisReceiptsRemainByteIdenticalToMilestone102() throws Exception {
        for(String name:new String[]{"h2","h4","n2"}) {
            var historical=Files.readString(Path.of("validation/milestone-10.2",name+"-fixed.receipt"));
            assertEquals(historical,DiisReceiptReplay.solve(name).receipt().toString(),name);
        }
    }
}
