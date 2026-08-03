package totah.lab.hermes.uniprot;

import org.junit.jupiter.api.Test;

public class UniProtClientTest {

    @Test
    void parsesCoreEntryMetadata() throws Exception {
        UniProtClient uniProt = new RestUniProtClient();

        UniProtEntry entry = uniProt.fetch("Q6UX53")
                .orElseThrow(() -> new IllegalStateException(
                        "UniProt entry Q6UX53 was not found"
                ));
        System.out.println(entry);
    }
}
