package totah.lab.prometheus.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Command-line boundary used by audited scientific campaigns. */
public final class ConservativeLinearFitCli {
    private ConservativeLinearFitCli() { }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3) {
            throw new IllegalArgumentException("usage: <fit-request.json> <artifact-directory> <receipt.json>");
        }
        Path requestPath = Path.of(arguments[0]);
        Path artifactDirectory = Path.of(arguments[1]);
        Path receiptPath = Path.of(arguments[2]);
        if (Files.exists(receiptPath)) throw new IllegalArgumentException("receipt path already exists: " + receiptPath);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        ConservativeLinearFitRequest request = mapper.readValue(requestPath.toFile(), ConservativeLinearFitRequest.class);
        ConservativeLinearFitter.SuccessfulFit fit = new ConservativeLinearFitter().fitAndPersist(artifactDirectory, request);
        mapper.writerWithDefaultPrettyPrinter().writeValue(receiptPath.toFile(), Map.of(
                "artifact_directory", fit.receipt().directory().toString(),
                "artifact_sha256", fit.receipt().artifactSha256(),
                "convergence_status", fit.receipt().artifact().convergenceStatus().name(),
                "parameter_count", fit.parameters().length,
                "receipt_verified", true));
    }
}
