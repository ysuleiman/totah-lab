package totah.lab.athena.recognition;

@FunctionalInterface
public interface RecognitionPairAdmissionProvider {
    RecognitionPairAdmission assess(RecognitionStateObservation first,
            RecognitionStateObservation second, double symmetricGeometryDistanceAngstroms,
            double symmetricTopologyDistance, RecognitionBasinPolicy policy);
}
