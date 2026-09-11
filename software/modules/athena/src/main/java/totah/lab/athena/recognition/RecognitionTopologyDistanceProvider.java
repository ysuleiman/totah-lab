package totah.lab.athena.recognition;

import java.util.Optional;

/** Adapter to an explicitly configured edge-assignment/topology comparison path. */
@FunctionalInterface
public interface RecognitionTopologyDistanceProvider {
    Optional<RecognitionTopologyDistance> distance(RecognitionStateObservation first,
            RecognitionStateObservation second);
}
