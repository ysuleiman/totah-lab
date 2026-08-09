package totah.lab.hermes.structure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import totah.lab.hermes.uniprot.RestUniProtClient;

import java.net.URISyntaxException;
import java.util.Optional;

@Tag("integration")
public class StructureResolutionClientTest {

    @Test
    public void test() throws Exception {
        StructureResolutionClient client =
                new DefaultStructureResolutionClient(
                        new RestUniProtClient()
                );

        Optional<ResolvedProteinStructure> resolved =
                client.resolve("Q6UX53");
        System.out.println(resolved);
    }
}
