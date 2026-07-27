package totah.lab.pipeline.stage;

import java.util.Map;

public record AD4AtomTypingReport(
        int residueCount,
        int atomCount,
        Map<String, Integer> typeCounts) {

    public AD4AtomTypingReport {
        typeCounts = Map.copyOf(typeCounts);
    }
}
