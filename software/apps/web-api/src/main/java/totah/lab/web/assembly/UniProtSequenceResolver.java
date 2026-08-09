package totah.lab.web.assembly;

import java.util.Optional;

public interface UniProtSequenceResolver {
    Optional<String> resolve(String accession) throws Exception;
}
