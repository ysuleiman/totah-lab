package totah.lab.mettl7.triage;

public record CofactorEvidence(
        boolean apoEvaluated,
        boolean samEvaluated,
        boolean sahEvaluated,
        boolean stateDependentRecognitionObserved) {
    public static CofactorEvidence none() {
        return new CofactorEvidence(false, false, false, false);
    }
}
