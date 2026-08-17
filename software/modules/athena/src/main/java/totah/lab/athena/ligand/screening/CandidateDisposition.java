package totah.lab.athena.ligand.screening;

import java.util.List;
import java.util.Objects;

/** Final uncalibrated screening disposition with its explicit reason. */
public record CandidateDisposition(
        CandidateFilter.Candidate candidate,
        Status status,
        Stage stage,
        String reason,
        List<String> advisoryNotes,
        PhysicochemicalGate.Result physicochemical,
        DrugLikenessAssessment.Result drugLikeness,
        ChemicalLiabilityGate.Result liabilities,
        CanonicalPocketGate.Result canonicalPocket,
        PoseReproducibilityGate.Result poseReproducibility,
        SamCompatibilityGate.Result samCompatibility,
        IsoformSelectivityComparator.Result isoformSelectivity,
        TslInterferenceClassifier.Comparison tslInterference) {

    public enum Status {
        REJECTED,
        DCMB_NEIGHBORHOOD_CONTROL,
        SELECTIVITY_REQUIRES_7A_TSL_RESOLUTION,
        PREDICTED_TMT1B_SELECTIVE_CANDIDATE
    }

    public enum Stage {
        LIBRARY_REQUIREMENT,
        PHYSICOCHEMICAL,
        CHEMICAL_LIABILITY,
        DCMB_SIMILARITY,
        CANONICAL_POCKET,
        POSE_REPRODUCIBILITY,
        SAM_COMPATIBILITY,
        ISOFORM_SELECTIVITY,
        TSL_INTERFERENCE,
        ADVANCED
    }

    public CandidateDisposition {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(stage, "stage");
        reason = requireText(reason, "reason");
        advisoryNotes = List.copyOf(Objects.requireNonNull(
                advisoryNotes, "advisoryNotes"));
    }

    public String candidateId() {
        return candidate.candidateId();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
