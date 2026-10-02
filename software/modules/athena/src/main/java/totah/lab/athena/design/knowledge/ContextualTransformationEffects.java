package totah.lab.athena.design.knowledge;

import java.util.*;

/** Exact-context paired differences. No universal confidence, benefit claim or planner authorization. */
public final class ContextualTransformationEffects {
    public static final String METHOD = "paired-interval-difference/exact-context/study-balanced/v2";
    /** Conditions must be an explicit reviewed protocol/conditions reference, never an unknown placeholder. */
    public record Context(String target, String endpoint, String method, String assay, String conditions,
                          String units, String scale, String qualification) {
        public Context {
            for (String s : List.of(target, endpoint, method, assay, conditions, units, scale, qualification))
                if (s.isBlank()) throw new IllegalArgumentException("qualified context fields required");
        }
    }
    /** Bounds may be infinite for censored observations; the original relation is retained verbatim. */
    public record Measurement(String reference, String moleculeIdentity, Context context, String study,
                              String replicate, double lower, double upper, String relation) {
        public Measurement {
            Objects.requireNonNull(context);
            for (String s : List.of(reference, moleculeIdentity, study, replicate, relation))
                if (s.isBlank()) throw new IllegalArgumentException("measurement provenance required");
            if (Double.isNaN(lower) || Double.isNaN(upper) || lower > upper
                    || lower == Double.POSITIVE_INFINITY || upper == Double.NEGATIVE_INFINITY)
                throw new IllegalArgumentException("invalid observation interval");
            boolean validRelation = switch (relation) {
                case "=" -> Double.isFinite(lower) && lower == upper;
                case "<", "<=" -> lower == Double.NEGATIVE_INFINITY && Double.isFinite(upper);
                case ">", ">=" -> Double.isFinite(lower) && upper == Double.POSITIVE_INFINITY;
                case "interval" -> lower < upper;
                default -> false;
            };
            if (!validRelation) throw new IllegalArgumentException("relation and interval disagree or are unsupported");
        }
    }
    public enum Direction { INCREASE, DECREASE, WITHIN_TOLERANCE, UNRESOLVED }
    public record Effect(MatchedPairExtractor.Pair pair, Measurement left, Measurement right,
                         double lower, double upper, Direction direction) { }
    public record Rejection(String leftObservation, String rightObservation, String reason) { }
    public record Analysis(List<Effect> effects, List<Rejection> rejections) {
        public Analysis { effects = List.copyOf(effects); rejections = List.copyOf(rejections); }
    }
    public record Summary(String transformation, Optional<String> chemicalContext, Context scientificContext,
                          int pairCount, int studyCount, int exactStudyCount, OptionalDouble mean,
                          OptionalDouble studyStandardDeviation, List<Effect> effects, String method) {
        public Summary { effects = List.copyOf(effects); }
    }

    public Analysis analyze(List<MatchedPairExtractor.Pair> pairs, List<Measurement> measurements, double tolerance) {
        if (!Double.isFinite(tolerance) || tolerance < 0) throw new IllegalArgumentException("finite nonnegative tolerance required");
        var byIdentity = new HashMap<String, List<Measurement>>();
        var ids = new HashSet<String>();
        for (var m : measurements) {
            if (!ids.add(m.reference())) throw new IllegalArgumentException("duplicate observation reference");
            byIdentity.computeIfAbsent(m.moleculeIdentity(), k -> new ArrayList<>()).add(m);
        }
        var effects = new ArrayList<Effect>(); var rejected = new ArrayList<Rejection>();
        var seen = new HashSet<String>();
        var provenance = new HashMap<String, List<String>>();
        var conflicts = new HashSet<String>();
        for (var pair : pairs) {
            var left = byIdentity.getOrDefault(pair.leftIdentity(), List.of());
            var right = byIdentity.getOrDefault(pair.rightIdentity(), List.of());
            if (left.isEmpty() || right.isEmpty()) {
                rejected.add(new Rejection(pair.left().id(), pair.right().id(), "MISSING_MEASUREMENT")); continue;
            }
            for (var a : left) for (var b : right) {
                String reason = null;
                if (!pair.left().observationReferences().contains(a.reference()) || !pair.right().observationReferences().contains(b.reference())) reason = "OBSERVATION_NOT_BOUND_TO_SOURCE";
                else if (!a.context().equals(b.context()) || !a.study().equals(b.study())) reason = "INCOMPARABLE_CONTEXT_OR_STUDY";
                else if (!pair.leftFragment().chemicalContext().equals(pair.rightFragment().chemicalContext())) reason = "CHEMICAL_CONTEXT_MISMATCH";
                else if (count(left, a) != 1 || count(right, b) != 1) reason = "UNRESOLVED_REPLICATES";
                if (reason != null) { rejected.add(new Rejection(a.reference(), b.reference(), reason)); continue; }
                // Multiple symmetry-related cut paths must not multiply one empirical comparison.
                String key = a.reference() + "\n" + b.reference() + "\n" + pair.transformation() + "\n" + pair.leftFragment().chemicalContext();
                var version = List.of(pair.algorithm(), pair.left().dataset(), pair.right().dataset());
                var previous = provenance.putIfAbsent(key, version);
                if (previous != null && !previous.equals(version)) {
                    if (conflicts.add(key)) rejected.add(new Rejection(a.reference(), b.reference(), "CONFLICTING_PROVENANCE"));
                }
                if (conflicts.contains(key) || !seen.add(key)) continue;
                double lo = b.lower() - a.upper(), hi = b.upper() - a.lower();
                if (a.relation().equals("=") && b.relation().equals("=") && (!Double.isFinite(lo) || !Double.isFinite(hi))) {
                    rejected.add(new Rejection(a.reference(), b.reference(), "DELTA_OVERFLOW")); continue;
                }
                Direction direction = lo > tolerance ? Direction.INCREASE : hi < -tolerance ? Direction.DECREASE
                        : lo >= -tolerance && hi <= tolerance ? Direction.WITHIN_TOLERANCE : Direction.UNRESOLVED;
                effects.add(new Effect(pair, a, b, lo, hi, direction));
            }
        }
        // A later conflicting duplicate invalidates an earlier effect too. Never let
        // encounter order select a resource version or leave partial support behind.
        effects.removeIf(e -> conflicts.contains(e.left().reference() + "\n" + e.right().reference()
                + "\n" + e.pair().transformation() + "\n" + e.pair().leftFragment().chemicalContext()));
        effects.sort(Comparator.comparing((Effect e) -> e.left().reference()).thenComparing(e -> e.right().reference())
                .thenComparing(e -> e.pair().transformation()).thenComparing(e -> e.pair().leftFragment().chemicalContext()));
        rejected.sort(Comparator.comparing(Rejection::leftObservation).thenComparing(Rejection::rightObservation).thenComparing(Rejection::reason));
        return new Analysis(effects, rejected);
    }
    private static long count(List<Measurement> values, Measurement value) {
        return values.stream().filter(m -> m.context().equals(value.context()) && m.study().equals(value.study())).count();
    }
    /** Unconditional means chemical context omitted ONLY; scientific contexts never pool. */
    public Summary summarize(List<Effect> input, String transformation, Context context, Optional<String> chemicalContext) {
        var selected = input.stream().filter(e -> e.pair().transformation().equals(transformation)
                && e.left().context().equals(context) && (chemicalContext.isEmpty()
                || chemicalContext.get().equals(e.pair().leftFragment().chemicalContext()))).toList();
        var byStudy = new TreeMap<String, List<Double>>(); var studies = new TreeSet<String>();
        var empirical = new LinkedHashMap<String, Effect>();
        for (var e : selected) empirical.putIfAbsent(e.left().reference()+"\n"+e.right().reference(), e);
        for (var e : empirical.values()) {
            if (!e.left().study().startsWith("UNRESOLVED:")) studies.add(e.left().study());
            if (e.lower() == e.upper() && Double.isFinite(e.lower()))
                byStudy.computeIfAbsent(e.left().study(), k -> new ArrayList<>()).add(e.lower());
        }
        var means = byStudy.values().stream().mapToDouble(v -> v.stream().mapToDouble(Double::doubleValue).average().orElseThrow()).toArray();
        double mean = Arrays.stream(means).average().orElse(Double.NaN), squared = 0;
        for (double x : means) squared += (x - mean) * (x - mean);
        return new Summary(transformation, chemicalContext, context, empirical.size(), studies.size(), (int) byStudy.keySet().stream().filter(k -> !k.startsWith("UNRESOLVED:")).count(),
                means.length == 0 ? OptionalDouble.empty() : OptionalDouble.of(mean),
                means.length < 2 || byStudy.keySet().stream().anyMatch(k -> k.startsWith("UNRESOLVED:")) ? OptionalDouble.empty() : OptionalDouble.of(Math.sqrt(squared / (means.length - 1))), selected, METHOD);
    }
}
