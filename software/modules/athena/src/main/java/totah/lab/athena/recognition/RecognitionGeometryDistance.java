package totah.lab.athena.recognition;

import java.util.OptionalDouble;

/** Adapter to existing verified pose-correspondence/RMSD evidence; does not calculate geometry. */
@FunctionalInterface
public interface RecognitionGeometryDistance {
    OptionalDouble distanceAngstroms(RecognitionStateObservation first,
            RecognitionStateObservation second);
}
