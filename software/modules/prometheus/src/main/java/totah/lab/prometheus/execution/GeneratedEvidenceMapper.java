package totah.lab.prometheus.execution;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import totah.lab.prometheus.store.GeneratedEvidenceCandidate;

/** Converts raw executor output into scientifically validated registry candidates. */
@FunctionalInterface
public interface GeneratedEvidenceMapper {
    List<GeneratedEvidenceCandidate> validateAndMap(RawCalculationResult result, Path artifactBase)
            throws IOException;
}
