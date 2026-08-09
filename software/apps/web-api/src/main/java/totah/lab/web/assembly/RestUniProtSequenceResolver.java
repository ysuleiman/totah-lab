package totah.lab.web.assembly;

import org.springframework.stereotype.Component;
import totah.lab.hermes.uniprot.RestUniProtClient;

import java.util.Optional;

/** Resolves canonical sequences directly from UniProt source truth. */
@Component
public final class RestUniProtSequenceResolver implements UniProtSequenceResolver {
    private final RestUniProtClient client = new RestUniProtClient();

    @Override
    public Optional<String> resolve(String accession) throws Exception {
        return client.fetch(accession).map(entry -> entry.sequence());
    }
}
