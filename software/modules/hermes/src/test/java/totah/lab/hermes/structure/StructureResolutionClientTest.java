package totah.lab.hermes.structure;

import org.junit.jupiter.api.Test;
import totah.lab.hermes.http.DefaultStructureResolutionClient;
import totah.lab.hermes.uniprot.RestUniProtClient;

import java.net.URISyntaxException;
import java.util.Optional;

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
