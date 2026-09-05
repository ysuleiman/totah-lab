package totah.lab.mettl7.triage;

import java.util.List;

public record CofactorStateAssessment(
        List<CofactorState> priority,
        String reason,
        boolean generalizationBeyondDcmbUntested) {
    public CofactorStateAssessment {
        priority = List.copyOf(priority);
        if (priority.isEmpty()) throw new IllegalArgumentException("priority must not be empty");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
    }
}
