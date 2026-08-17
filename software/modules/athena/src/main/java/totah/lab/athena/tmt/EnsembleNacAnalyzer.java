package totah.lab.athena.tmt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.TreeMap;

public final class EnsembleNacAnalyzer {
    public EnsembleNacSummary summarize(String stateId, List<EnsembleFrame> frames) {
        Objects.requireNonNull(stateId, "stateId");
        Objects.requireNonNull(frames, "frames");
        if (frames.isEmpty()) {
            return new EnsembleNacSummary(stateId, EnsembleEvidenceStatus.NO_FRAMES, List.of(),
                    OptionalDouble.empty(), OptionalDouble.empty(), "No trajectory frames were evaluated.");
        }
        Map<Integer, List<EnsembleFrame>> byReplica = new TreeMap<>();
        for (EnsembleFrame frame : frames) {
            if (!frame.stateId().equals(stateId)) {
                throw new IllegalArgumentException("all frames must match stateId");
            }
            byReplica.computeIfAbsent(frame.replica(), ignored -> new ArrayList<>()).add(frame);
        }
        List<ReplicaNacSummary> summaries = byReplica.entrySet().stream()
                .map(entry -> summarizeReplica(stateId, entry.getKey(), entry.getValue()))
                .toList();
        if (summaries.size() < 2) {
            return new EnsembleNacSummary(stateId, EnsembleEvidenceStatus.INSUFFICIENT_REPLICAS,
                    summaries, OptionalDouble.empty(), OptionalDouble.empty(),
                    "At least two independent replicas are required for between-replica evidence.");
        }
        double mean = summaries.stream().mapToDouble(ReplicaNacSummary::nacFraction).average().orElseThrow();
        double variance = summaries.stream()
                .mapToDouble(value -> Math.pow(value.nacFraction() - mean, 2.0))
                .sum() / (summaries.size() - 1);
        return new EnsembleNacSummary(stateId, EnsembleEvidenceStatus.EVALUATED, summaries,
                OptionalDouble.of(mean), OptionalDouble.of(Math.sqrt(variance)),
                "Replica-resolved NAC population; observables remain separate.");
    }

    private ReplicaNacSummary summarizeReplica(String stateId, int replica, List<EnsembleFrame> input) {
        List<EnsembleFrame> frames = input.stream()
                .sorted(Comparator.comparingLong(EnsembleFrame::frameIndex))
                .toList();
        int nacFrames = 0;
        int into = 0;
        int out = 0;
        int runs = 0;
        boolean previous = false;
        double runStart = 0.0;
        double longest = 0.0;
        for (int index = 0; index < frames.size(); index++) {
            EnsembleFrame frame = frames.get(index);
            boolean current = frame.candidateNac();
            if (current) {
                nacFrames++;
            }
            if (current && !previous) {
                runs++;
                if (index > 0) {
                    into++;
                }
                runStart = frame.timePicoseconds();
            } else if (!current && previous) {
                out++;
                longest = Math.max(longest, frame.timePicoseconds() - runStart);
            }
            previous = current;
        }
        if (previous) {
            longest = Math.max(longest, frames.getLast().timePicoseconds() - runStart);
        }
        return new ReplicaNacSummary(stateId, replica, frames.size(),
                (double) nacFrames / frames.size(), into, out, Math.max(0, runs - 1), longest);
    }
}
