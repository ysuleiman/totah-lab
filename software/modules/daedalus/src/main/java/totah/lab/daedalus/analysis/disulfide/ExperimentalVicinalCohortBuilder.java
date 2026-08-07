package totah.lab.daedalus.analysis.disulfide;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Builds strict experimental cohorts from a UniProt TSV export and the output
 * of {@link AdjacentCysteineScan}. AlphaFold geometry is never used as
 * experimental evidence.
 */
public final class ExperimentalVicinalCohortBuilder {

    private static final Pattern DISULFIDE = Pattern.compile(
            "DISULFID (\\d+)\\.\\.(\\d+);(.*?)(?=; DISULFID|$)");

    private ExperimentalVicinalCohortBuilder() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: ExperimentalVicinalCohortBuilder "
                            + "<all-cc-pairs.csv> <uniprot.tsv[.gz]> <output-directory>");
        }
        CohortSummary summary = build(
                Path.of(arguments[0]), Path.of(arguments[1]), Path.of(arguments[2]));
        System.out.printf(
                Locale.ROOT,
                "Found %d experimental vicinal CC pairs and %d strict non-vicinal "
                        + "controls; wrote %d matched pairs to %s%n",
                summary.positiveCount(),
                summary.controlPoolCount(),
                summary.matchedPairCount(),
                summary.outputDirectory());
    }

    public static CohortSummary build(
            Path scanCsv, Path uniProtTsv, Path outputDirectory) throws IOException {
        Objects.requireNonNull(scanCsv, "scanCsv is null");
        Objects.requireNonNull(uniProtTsv, "uniProtTsv is null");
        Objects.requireNonNull(outputDirectory, "outputDirectory is null");

        Map<MotifKey, ScanRow> scanRows = readScanRows(scanCsv);
        List<LabeledMotif> positives = new ArrayList<>();
        List<LabeledMotif> controls = new ArrayList<>();
        readExperimentalLabels(uniProtTsv, scanRows, positives, controls);
        positives.sort(LabeledMotif.ORDER);
        controls.sort(LabeledMotif.ORDER);
        List<Match> matches = matchWithoutReplacement(positives, controls);

        Files.createDirectories(outputDirectory);
        writeLabeled(outputDirectory.resolve("experimentally-confirmed-vicinal-cc.csv"),
                positives);
        writeLabeled(outputDirectory.resolve("experimental-non-vicinal-cc-control-pool.csv"),
                controls);
        writeMatches(outputDirectory.resolve("matched-vicinal-vs-non-vicinal-cc.csv"),
                matches);
        return new CohortSummary(
                positives.size(), controls.size(), matches.size(), outputDirectory);
    }

    private static Map<MotifKey, ScanRow> readScanRows(Path path) throws IOException {
        Map<MotifKey, ScanRow> rows = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || !header.startsWith("uniprot_accession,")) {
                throw new IOException("Unexpected scan CSV header: " + path);
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = parseCsvLine(line);
                if (values.length != 13) {
                    throw new IOException("Malformed scan CSV row: " + line);
                }
                ScanRow row = new ScanRow(
                        values[0],
                        values[1],
                        Integer.parseInt(values[2]),
                        Integer.parseInt(values[3]),
                        values[4],
                        Integer.parseInt(values[5]),
                        values[6].isBlank() ? Double.NaN : Double.parseDouble(values[6]),
                        values[7],
                        values[8].isBlank() ? null : Double.parseDouble(values[8]),
                        Double.parseDouble(values[9]),
                        Double.parseDouble(values[10]),
                        Double.parseDouble(values[11]),
                        values[12]);
                rows.put(new MotifKey(row.accession(), row.cys1(), row.cys2()), row);
            }
        }
        return rows;
    }

    private static String[] parseCsvLine(String line) throws IOException {
        List<String> values = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == ',') {
                values.add(field.toString());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        if (quoted) {
            throw new IOException("Unterminated quoted field in scan CSV row: " + line);
        }
        values.add(field.toString());
        return values.toArray(String[]::new);
    }

    private static void readExperimentalLabels(
            Path path,
            Map<MotifKey, ScanRow> scanRows,
            List<LabeledMotif> positives,
            List<LabeledMotif> controls) throws IOException {
        try (InputStream fileInput = Files.newInputStream(path);
             InputStream input = path.getFileName().toString().endsWith(".gz")
                     ? new GZIPInputStream(fileInput)
                     : fileInput;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                throw new IOException("Empty UniProt TSV: " + path);
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split("\\t", -1);
                if (values.length < 5) {
                    continue;
                }
                String accession = values[0];
                String gene = firstWord(values[1]);
                String proteinName = values[2];
                String sequence = values[3];
                List<ExperimentalBond> bonds = experimentalBonds(values[4]);
                Map<Integer, Set<Integer>> partners = partners(bonds);

                for (int index = 0; index + 1 < sequence.length(); index++) {
                    if (sequence.charAt(index) != 'C' || sequence.charAt(index + 1) != 'C') {
                        continue;
                    }
                    int first = index + 1;
                    int second = index + 2;
                    ScanRow scan = scanRows.get(new MotifKey(accession, first, second));
                    if (scan == null) {
                        continue;
                    }
                    List<ExperimentalBond> relevant = bonds.stream()
                            .filter(bond -> bond.involves(first) || bond.involves(second))
                            .toList();
                    boolean vicinal = bonds.stream()
                            .anyMatch(bond -> bond.first() == first && bond.second() == second);
                    boolean separatelyBonded = hasOtherPartner(partners, first, second)
                            && hasOtherPartner(partners, second, first);
                    if (vicinal) {
                        positives.add(labeled(
                                "VICINAL", gene, proteinName, sequence, scan, relevant));
                    } else if (separatelyBonded) {
                        controls.add(labeled(
                                "NON_VICINAL", gene, proteinName, sequence, scan, relevant));
                    }
                }
            }
        }
    }

    private static LabeledMotif labeled(
            String label,
            String gene,
            String proteinName,
            String sequence,
            ScanRow scan,
            List<ExperimentalBond> evidence) {
        return new LabeledMotif(
                label,
                scan,
                gene,
                proteinName,
                sequence.length(),
                (double) scan.cys1() / sequence.length(),
                scan.sequenceContext().chars().filter(value -> value == 'C').count(),
                evidence.stream().map(ExperimentalBond::description)
                        .sorted().reduce((left, right) -> left + " | " + right).orElse(""));
    }

    private static List<ExperimentalBond> experimentalBonds(String features) {
        List<ExperimentalBond> bonds = new ArrayList<>();
        Matcher matcher = DISULFIDE.matcher(features);
        while (matcher.find()) {
            String evidence = matcher.group(3);
            if (evidence.contains("ECO:0000269") || evidence.contains("ECO:0007744")) {
                int first = Integer.parseInt(matcher.group(1));
                int second = Integer.parseInt(matcher.group(2));
                bonds.add(new ExperimentalBond(
                        first, second, "DISULFID " + first + ".." + second + ";"
                        + evidence));
            }
        }
        return List.copyOf(bonds);
    }

    private static Map<Integer, Set<Integer>> partners(List<ExperimentalBond> bonds) {
        Map<Integer, Set<Integer>> partners = new HashMap<>();
        for (ExperimentalBond bond : bonds) {
            partners.computeIfAbsent(bond.first(), ignored -> new HashSet<>())
                    .add(bond.second());
            partners.computeIfAbsent(bond.second(), ignored -> new HashSet<>())
                    .add(bond.first());
        }
        return partners;
    }

    private static boolean hasOtherPartner(
            Map<Integer, Set<Integer>> partners, int position, int excluded) {
        return partners.getOrDefault(position, Set.of()).stream()
                .anyMatch(partner -> partner != excluded);
    }

    private static List<Match> matchWithoutReplacement(
            List<LabeledMotif> positives, List<LabeledMotif> controls) {
        List<MatchCandidate> candidates = new ArrayList<>();
        for (LabeledMotif positive : positives) {
            for (LabeledMotif control : controls) {
                candidates.add(new MatchCandidate(
                        positive, control, matchScore(positive, control)));
            }
        }
        candidates.sort(Comparator
                .comparingDouble(MatchCandidate::score)
                .thenComparing(candidate -> candidate.positive().scan().accession())
                .thenComparing(candidate -> candidate.control().scan().accession()));

        Set<MotifKey> usedPositives = new HashSet<>();
        Set<MotifKey> usedControls = new HashSet<>();
        List<Match> matches = new ArrayList<>();
        for (MatchCandidate candidate : candidates) {
            MotifKey positiveKey = candidate.positive().key();
            MotifKey controlKey = candidate.control().key();
            if (usedPositives.add(positiveKey)) {
                if (usedControls.add(controlKey)) {
                    matches.add(new Match(
                            candidate.positive(), candidate.control(), candidate.score()));
                } else {
                    usedPositives.remove(positiveKey);
                }
            }
        }
        matches.sort(Comparator.comparing(match -> match.positive().scan().accession()));
        return List.copyOf(matches);
    }

    private static double matchScore(LabeledMotif positive, LabeledMotif control) {
        ScanRow left = positive.scan();
        ScanRow right = control.scan();
        double score = Math.abs(left.meanPlddt() - right.meanPlddt()) / 10.0;
        if (!Double.isNaN(left.sgDistance()) && !Double.isNaN(right.sgDistance())) {
            score += Math.abs(left.sgDistance() - right.sgDistance()) / 2.0;
        }
        score += Math.abs(positive.relativePosition() - control.relativePosition()) * 3.0;
        score += Math.abs(positive.contextCysteineCount()
                - control.contextCysteineCount());
        if (!left.distanceClass().equals(right.distanceClass())) {
            score += 2.0;
        }
        return score;
    }

    private static void writeLabeled(Path path, List<LabeledMotif> motifs)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(labeledHeader());
            writer.newLine();
            for (LabeledMotif motif : motifs) {
                writer.write(labeledRow(motif));
                writer.newLine();
            }
        }
    }

    private static void writeMatches(Path path, List<Match> matches) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("match_id,match_score,case_control," + labeledHeader());
            writer.newLine();
            for (int index = 0; index < matches.size(); index++) {
                Match match = matches.get(index);
                String prefix = Integer.toString(index + 1) + ","
                        + format("%.4f", match.score()) + ",";
                writer.write(prefix + "CASE," + labeledRow(match.positive()));
                writer.newLine();
                writer.write(prefix + "CONTROL," + labeledRow(match.control()));
                writer.newLine();
            }
        }
    }

    private static String labeledHeader() {
        return "experimental_label,uniprot_accession,gene,protein_name,chain,cys1,cys2,"
                + "sequence_context,motif_offset,sg_distance_angstrom,distance_class,"
                + "chi3_degrees,cys1_plddt,cys2_plddt,mean_plddt,protein_length,"
                + "relative_position,context_cysteine_count,experimental_evidence,filename";
    }

    private static String labeledRow(LabeledMotif motif) {
        ScanRow scan = motif.scan();
        return String.join(",",
                motif.label(),
                csv(scan.accession()),
                csv(motif.gene()),
                csv(motif.proteinName()),
                csv(scan.chain()),
                Integer.toString(scan.cys1()),
                Integer.toString(scan.cys2()),
                csv(scan.sequenceContext()),
                Integer.toString(scan.motifOffset()),
                Double.isNaN(scan.sgDistance()) ? "" : format("%.3f", scan.sgDistance()),
                scan.distanceClass(),
                scan.chi3() == null ? "" : format("%.2f", scan.chi3()),
                format("%.2f", scan.cys1Plddt()),
                format("%.2f", scan.cys2Plddt()),
                format("%.2f", scan.meanPlddt()),
                Integer.toString(motif.proteinLength()),
                format("%.6f", motif.relativePosition()),
                Long.toString(motif.contextCysteineCount()),
                csv(motif.evidence()),
                csv(scan.filename()));
    }

    private static String firstWord(String value) {
        int space = value.indexOf(' ');
        return space < 0 ? value : value.substring(0, space);
    }

    private static String csv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String format(String pattern, double value) {
        return String.format(Locale.ROOT, pattern, value);
    }

    public record CohortSummary(
            int positiveCount,
            int controlPoolCount,
            int matchedPairCount,
            Path outputDirectory) {
    }

    private record MotifKey(String accession, int cys1, int cys2) {
    }

    private record ScanRow(
            String accession,
            String chain,
            int cys1,
            int cys2,
            String sequenceContext,
            int motifOffset,
            double sgDistance,
            String distanceClass,
            Double chi3,
            double cys1Plddt,
            double cys2Plddt,
            double meanPlddt,
            String filename) {
    }

    private record ExperimentalBond(int first, int second, String description) {
        private boolean involves(int position) {
            return first == position || second == position;
        }
    }

    private record LabeledMotif(
            String label,
            ScanRow scan,
            String gene,
            String proteinName,
            int proteinLength,
            double relativePosition,
            long contextCysteineCount,
            String evidence) {

        private static final Comparator<LabeledMotif> ORDER = Comparator
                .comparing((LabeledMotif motif) -> motif.scan().accession())
                .thenComparingInt(motif -> motif.scan().cys1());

        private MotifKey key() {
            return new MotifKey(scan.accession(), scan.cys1(), scan.cys2());
        }
    }

    private record MatchCandidate(
            LabeledMotif positive, LabeledMotif control, double score) {
    }

    private record Match(LabeledMotif positive, LabeledMotif control, double score) {
    }
}
