package totah.lab.http.biohub;

import totah.lab.protein.analysis.ResidueConstraintEvidence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class EsmcResidueConstraintCalculator {

    List<ResidueConstraintEvidence> calculate(
            String sequence,
            double[][] logits
    ) {
        if (logits.length != sequence.length() + 2) {
            throw new IllegalArgumentException(
                    "ESMC logits must include BOS and EOS positions"
            );
        }

        List<ResidueConstraintEvidence> evidence =
                new ArrayList<>(sequence.length());
        for (int index = 0; index < sequence.length(); index++) {
            evidence.add(calculatePosition(
                    index + 1,
                    sequence.charAt(index),
                    logits[index + 1]
            ));
        }
        return List.copyOf(evidence);
    }

    private ResidueConstraintEvidence calculatePosition(
            int position,
            char wildType,
            double[] logits
    ) {
        Map<Character, Integer> tokens = EsmcVocabulary.canonicalTokens();
        int largestToken = tokens.values().stream()
                .max(Integer::compareTo)
                .orElseThrow();
        if (logits.length <= largestToken) {
            throw new IllegalArgumentException(
                    "ESMC logits do not contain the canonical amino-acid tokens"
            );
        }

        double maximum = tokens.values().stream()
                .mapToDouble(token -> logits[token])
                .max()
                .orElseThrow();
        double normalization = maximum + Math.log(
                tokens.values().stream()
                        .mapToDouble(token -> Math.exp(logits[token] - maximum))
                        .sum()
        );

        List<AminoAcidScore> scores = tokens.entrySet().stream()
                .map(entry -> new AminoAcidScore(
                        entry.getKey(),
                        logits[entry.getValue()] - normalization
                ))
                .sorted(Comparator.comparingDouble(AminoAcidScore::logProbability)
                        .reversed())
                .toList();
        AminoAcidScore wildTypeScore = scores.stream()
                .filter(score -> score.aminoAcid() == wildType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported amino acid: " + wildType
                ));
        List<AminoAcidScore> alternatives = scores.stream()
                .filter(score -> score.aminoAcid() != wildType)
                .toList();
        AminoAcidScore bestAlternative = alternatives.getFirst();
        double meanAlternative = alternatives.stream()
                .mapToDouble(AminoAcidScore::logProbability)
                .average()
                .orElseThrow();
        double entropy = scores.stream()
                .mapToDouble(score -> {
                    double probability = Math.exp(score.logProbability());
                    return -probability * score.logProbability();
                })
                .sum();
        int wildTypeRank = scores.indexOf(wildTypeScore) + 1;

        return new ResidueConstraintEvidence(
                position,
                wildType,
                wildTypeScore.logProbability(),
                meanAlternative,
                bestAlternative.aminoAcid(),
                bestAlternative.logProbability(),
                wildTypeScore.logProbability() - meanAlternative,
                wildTypeScore.logProbability()
                        - bestAlternative.logProbability(),
                wildTypeRank,
                entropy
        );
    }

    private record AminoAcidScore(
            char aminoAcid,
            double logProbability
    ) {
    }
}
