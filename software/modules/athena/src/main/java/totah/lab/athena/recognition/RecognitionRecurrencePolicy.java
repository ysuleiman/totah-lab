package totah.lab.athena.recognition;

/** Explicit empirical recurrence requirements; family provenance is annotation only. */
public record RecognitionRecurrencePolicy(int minimumMembers, int minimumDistinctSeeds,
        int minimumDistinctRuns, String provenance) {
    public RecognitionRecurrencePolicy {
        if (minimumMembers < 1 || minimumDistinctSeeds < 1 || minimumDistinctRuns < 1)
            throw new IllegalArgumentException("recurrence minima must be positive");
        if (provenance == null || provenance.isBlank()) throw new IllegalArgumentException("provenance required");
    }
    public boolean recurrent(RecognitionBasin basin) {
        return basin.members().size() >= minimumMembers && basin.seeds().size() >= minimumDistinctSeeds
                && basin.runs().size() >= minimumDistinctRuns;
    }
}
