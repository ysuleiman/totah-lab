package totah.lab.athena.ligand.screening;

import java.util.Objects;

/** Classifies interference with a productive TSL/SAM state ensemble. */
public final class TslInterferenceClassifier {

    public enum Classification {
        BROAD_TSL_INTERFERENCE,
        STATE_DEPENDENT_INTERFERENCE,
        TSL_ESCAPE,
        UNRESOLVED
    }

    public record Evidence(int productiveStatesEvaluated,
                           int productiveStatesInterfered,
                           boolean evaluationResolved) {
        public Evidence {
            if (productiveStatesEvaluated < 0 || productiveStatesInterfered < 0) {
                throw new IllegalArgumentException("state counts must be non-negative");
            }
            if (productiveStatesInterfered > productiveStatesEvaluated) {
                throw new IllegalArgumentException(
                        "interfered states cannot exceed evaluated states");
            }
        }
    }

    public record Comparison(Classification tmt1b, Classification tmt1a) {
        public Comparison {
            Objects.requireNonNull(tmt1b, "tmt1b");
            Objects.requireNonNull(tmt1a, "tmt1a");
        }

        public boolean stronglySupportsTmt1bSelectivity() {
            return interferes(tmt1b) && tmt1a == Classification.TSL_ESCAPE;
        }

        public boolean provisionallySupportsTmt1bSelectivity() {
            return interferes(tmt1b) && tmt1a == Classification.UNRESOLVED;
        }

        private static boolean interferes(Classification value) {
            return value == Classification.BROAD_TSL_INTERFERENCE
                    || value == Classification.STATE_DEPENDENT_INTERFERENCE;
        }
    }

    public Classification classify(Evidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (!evidence.evaluationResolved() || evidence.productiveStatesEvaluated() == 0) {
            return Classification.UNRESOLVED;
        }
        if (evidence.productiveStatesInterfered() == 0) {
            return Classification.TSL_ESCAPE;
        }
        if (evidence.productiveStatesInterfered()
                == evidence.productiveStatesEvaluated()) {
            return Classification.BROAD_TSL_INTERFERENCE;
        }
        return Classification.STATE_DEPENDENT_INTERFERENCE;
    }

    public Comparison compare(Evidence tmt1b, Evidence tmt1a) {
        return new Comparison(classify(tmt1b), classify(tmt1a));
    }
}
