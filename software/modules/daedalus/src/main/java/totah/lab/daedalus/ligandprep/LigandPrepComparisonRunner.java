package totah.lab.daedalus.ligandprep;

import totah.lab.daedalus.ligandprep.LigandPrepComparator.LigandPrepComparison;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.hephaestus.client.HephaestusClient;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.LigandPreparationResult;
import totah.lab.hephaestus.model.PreparationIssue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Runs the hephaestus-vs-Meeko ligand preparation comparison over a
 * sample of reference ligands: prepares each source SDF with
 * hephaestus (into a scratch directory), parses both PDBQTs and
 * compares them. Per-ligand failures are recorded with their reason —
 * the known explicit-hydrogen limitation of the SDF path included —
 * and never abort the run.
 */
public final class LigandPrepComparisonRunner {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_FAILED = "FAILED";

    public record Outcome(
            LigandPrepSample sample,
            String status,
            String failureCategory,
            String failureDetail,
            LigandPrepComparison comparison
    ) {
        public boolean ok() {
            return STATUS_OK.equals(status);
        }
    }

    private final LigandPrepSampler sampler;
    private final HephaestusClient hephaestus;
    private final Path workDirectory;

    public LigandPrepComparisonRunner(
            LigandPrepSampler sampler,
            HephaestusClient hephaestus,
            Path workDirectory
    ) {
        this.sampler = Objects.requireNonNull(sampler, "sampler");
        this.hephaestus = Objects.requireNonNull(hephaestus, "hephaestus");
        this.workDirectory = Objects.requireNonNull(
                workDirectory, "workDirectory");
    }

    public List<Outcome> run(int count) throws Exception {
        List<LigandPrepSample> samples = sampler.sample(count);
        Files.createDirectories(workDirectory);

        List<Outcome> outcomes = new ArrayList<>(samples.size());
        int index = 0;
        for (LigandPrepSample sample : samples) {
            outcomes.add(process(sample, index));
            index++;
        }
        return List.copyOf(outcomes);
    }

    private Outcome process(LigandPrepSample sample, int index) {
        try {
            LigandPreparationResult preparation = hephaestus.prepareLigand(
                    sample.sdf(), LigandPreparationOptions.defaults());
            if (!preparation.successful()) {
                return failure(sample, "preparation",
                        preparation.issues().stream()
                                .filter(PreparationIssue::fatal)
                                .map(PreparationIssue::message)
                                .findFirst()
                                .orElse("preparation failed"));
            }

            Path ourPdbqt = workDirectory.resolve(
                    "ligand-" + index + ".pdbqt");
            hephaestus.writePreparedLigand(
                    preparation.preparedLigand(), ourPdbqt);

            PdbqtModel ours =
                    new PdbqtReader().read(ourPdbqt).firstModel();
            PdbqtModel meeko =
                    new PdbqtReader().read(sample.meekoPdbqt()).firstModel();

            return new Outcome(sample, STATUS_OK, null, null,
                    LigandPrepComparator.compare(ours, meeko));
        } catch (Exception exception) {
            return failure(sample,
                    classify(exception),
                    exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage());
        }
    }

    private static Outcome failure(
            LigandPrepSample sample,
            String category,
            String detail
    ) {
        return new Outcome(
                sample, STATUS_FAILED, category, detail, null);
    }

    /*
     * Failure buckets: the SDF path requires explicit hydrogens and
     * refuses to add them — most failures are expected to land here.
     */
    private static String classify(Exception exception) {
        String message = exception.getMessage() == null
                ? ""
                : exception.getMessage().toLowerCase(Locale.ROOT);
        if (exception instanceof java.io.IOException) {
            return "io";
        }
        if (message.contains("hydrogen")) {
            return "missing-hydrogens";
        }
        if (message.contains("v3000") || message.contains("multi-molecule")
                || message.contains("radical")
                || message.contains("sdf")) {
            return "sdf-parse";
        }
        return "preparation";
    }

    // --- reporting -----------------------------------------------------

    public static String csv(List<Outcome> outcomes) {
        StringBuilder csv = new StringBuilder(
                "id,name,status,failure_category,"
                        + "failure_detail,our_heavy,meeko_heavy,"
                        + "matched_heavy,atoms_match,our_total_charge,"
                        + "meeko_total_charge,total_charge_delta,"
                        + "charge_mad,type_agreement,torsdof_ours,"
                        + "torsdof_meeko,torsdof_delta,rotors_ours,"
                        + "rotors_meeko,rotors_matched,max_coord_delta\n");
        for (Outcome outcome : outcomes) {
            LigandPrepComparison comparison = outcome.comparison();
            List<String> fields = new ArrayList<>(List.of(
                    outcome.sample().id(),
                    outcome.sample().name(),
                    outcome.status(),
                    outcome.failureCategory() == null
                            ? "" : outcome.failureCategory(),
                    outcome.failureDetail() == null
                            ? "" : outcome.failureDetail()));
            if (comparison == null) {
                for (int index = 0; index < 16; index++) {
                    fields.add("");
                }
            } else {
                fields.add(String.valueOf(comparison.ourHeavyAtoms()));
                fields.add(String.valueOf(comparison.meekoHeavyAtoms()));
                fields.add(String.valueOf(comparison.matchedHeavyAtoms()));
                fields.add(String.valueOf(comparison.atomCountsMatch()));
                fields.add(format(comparison.ourTotalCharge()));
                fields.add(format(comparison.meekoTotalCharge()));
                fields.add(format(comparison.totalChargeDelta()));
                fields.add(format(comparison.meanAbsChargeDelta()));
                fields.add(format(comparison.ad4TypeAgreement()));
                fields.add(String.valueOf(comparison.ourTorsdof()));
                fields.add(String.valueOf(comparison.meekoTorsdof()));
                fields.add(String.valueOf(comparison.torsdofDelta()));
                fields.add(String.valueOf(comparison.rotorsOurs()));
                fields.add(String.valueOf(comparison.rotorsMeeko()));
                fields.add(String.valueOf(comparison.rotorsMatched()));
                fields.add(format(comparison.maxCoordinateDelta()));
            }
            csv.append(String.join(",",
                    fields.stream()
                            .map(LigandPrepComparisonRunner::escape)
                            .toList()))
                    .append('\n');
        }
        return csv.toString();
    }

    public static String summary(List<Outcome> outcomes) {
        StringBuilder out = new StringBuilder();
        long ok = outcomes.stream().filter(Outcome::ok).count();
        out.append("Sampled: ").append(outcomes.size()).append('\n');
        out.append("Prepared and compared: ").append(ok).append('\n');
        out.append("Failed: ").append(outcomes.size() - ok).append('\n');

        Map<String, Long> failures = new LinkedHashMap<>();
        for (Outcome outcome : outcomes) {
            if (!outcome.ok()) {
                failures.merge(outcome.failureCategory(), 1L, Long::sum);
            }
        }
        failures.forEach((category, count) ->
                out.append("  ").append(category)
                        .append(": ").append(count).append('\n'));

        List<LigandPrepComparison> comparisons = outcomes.stream()
                .filter(Outcome::ok)
                .map(Outcome::comparison)
                .toList();
        if (!comparisons.isEmpty()) {
            out.append("Atom-count mismatches: ")
                    .append(comparisons.stream()
                            .filter(c -> !c.atomCountsMatch()).count())
                    .append('\n');
            out.append("Heavy atoms unmatched by coordinates: ")
                    .append(comparisons.stream()
                            .mapToInt(c -> c.meekoHeavyAtoms()
                                    - c.matchedHeavyAtoms())
                            .sum())
                    .append('\n');
            statistics(out, "Charge mean-abs-delta",
                    comparisons.stream()
                            .map(LigandPrepComparison::meanAbsChargeDelta)
                            .filter(Objects::nonNull)
                            .toList());
            statistics(out, "AD4 type agreement",
                    comparisons.stream()
                            .map(LigandPrepComparison::ad4TypeAgreement)
                            .filter(Objects::nonNull)
                            .toList());
            statistics(out, "TORSDOF delta",
                    comparisons.stream()
                            .map(c -> (double) c.torsdofDelta())
                            .toList());
            out.append("Rotor-set mismatches (same count, different"
                            + " bonds): ")
                    .append(comparisons.stream()
                            .filter(LigandPrepComparator
                                    ::rotorSetsDiffer)
                            .count())
                    .append('\n');
            statistics(out, "Max coordinate delta",
                    comparisons.stream()
                            .map(LigandPrepComparison::maxCoordinateDelta)
                            .filter(Objects::nonNull)
                            .toList());

            out.append("Worst mismatches (up to 10):\n");
            worst(outcomes).forEach(outcome -> {
                LigandPrepComparison c = outcome.comparison();
                out.append("  ")
                        .append(outcome.sample().id())
                        .append(' ')
                        .append(outcome.sample().name())
                        .append("  type=")
                        .append(format(c.ad4TypeAgreement()))
                        .append(" chargeMAD=")
                        .append(format(c.meanAbsChargeDelta()))
                        .append(" torsdofDelta=")
                        .append(c.torsdofDelta())
                        .append('\n');
            });
        }
        return out.toString();
    }

    private static List<Outcome> worst(List<Outcome> outcomes) {
        return outcomes.stream()
                .filter(Outcome::ok)
                .filter(outcome ->
                        outcome.comparison().ad4TypeAgreement() != null)
                .sorted(Comparator
                        .comparingDouble((Outcome outcome) ->
                                outcome.comparison().ad4TypeAgreement())
                        .thenComparing(Comparator.comparingDouble(
                                (Outcome outcome) -> outcome.comparison()
                                        .meanAbsChargeDelta())
                                .reversed()))
                .limit(10)
                .toList();
    }

    private static void statistics(
            StringBuilder out, String label, List<Double> values) {
        if (values.isEmpty()) {
            return;
        }
        List<Double> sorted = values.stream().sorted().toList();
        double mean = sorted.stream()
                .mapToDouble(Double::doubleValue).sum() / sorted.size();
        double median = sorted.size() % 2 == 1
                ? sorted.get(sorted.size() / 2)
                : (sorted.get(sorted.size() / 2 - 1)
                        + sorted.get(sorted.size() / 2)) / 2.0;
        out.append(label).append(": mean=")
                .append(format(mean))
                .append(" median=").append(format(median))
                .append(" min=").append(format(sorted.get(0)))
                .append('\n');
    }

    private static String format(Double value) {
        return value == null
                ? ""
                : String.format(Locale.ROOT, "%.4f", value);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String escape(String value) {
        if (value.contains(",") || value.contains("\"")
                || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
