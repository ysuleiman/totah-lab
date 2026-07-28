package totah.lab.pocket.visualization;

import totah.lab.pocket.Pocket;
import totah.lab.pocket.PocketSource;
import totah.lab.protein.Protein;

import java.util.Comparator;

final class PocketRanking {
    private static final String DRUGGABILITY_SCORE = "druggability score";

    private PocketRanking() {
    }

    static Pocket preferredPocket(Protein protein) {
        return protein.getPockets().stream()
                .filter(pocket -> pocket.getSpheres() != null
                        && !pocket.getSpheres().isEmpty())
                .max(Comparator.comparingDouble(PocketRanking::rankingScore))
                .orElseGet(protein::getBestPocket);
    }

    static double rankingScore(Pocket pocket) {
        if (pocket.getSource() == PocketSource.FPOCKET) {
            Double druggability = druggabilityScore(pocket);
            if (druggability != null) {
                return druggability;
            }
        }
        return pocket.getScore() == null ? 0.0 : pocket.getScore();
    }

    static Double druggabilityScore(Pocket pocket) {
        return numericAttribute(pocket, DRUGGABILITY_SCORE);
    }

    private static Double numericAttribute(Pocket pocket, String key) {
        Object value = pocket.getAttribute(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
