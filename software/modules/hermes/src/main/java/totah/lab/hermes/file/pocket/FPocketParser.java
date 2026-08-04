package totah.lab.hermes.file.pocket;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.*;
import totah.lab.gaia.structure.ResidueId;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class FPocketParser {

    private static final String INFO_SUFFIX = "_info.txt";
    private static final Pattern POCKET_HEADER =
            Pattern.compile("Pocket\\s+(\\d+)\\s*:");
    private static final Pattern METRIC = Pattern.compile(
            "^\\s*([^:]+?)\\s*:\\s*([-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)");
    private static final Map<String, PocketMetricType> METRIC_TYPES =
            metricTypes();

    private FPocketParser() {
    }

    public static List<Pocket> parse(Path pocketsDirectory)
            throws IOException {

        Map<Long, ParsedPocketInfo> info =
                parseInfoFile(findInfoFile(pocketsDirectory));
        List<Pocket> pockets = new ArrayList<>();

        for (Map.Entry<Long, ParsedPocketInfo> entry : info.entrySet()) {
            long pocketNumber = entry.getKey();
            Path sourceDirectory = pocketsDirectory.resolve("pockets");
            List<ResidueId> residues = readPocketResidues(
                    sourceDirectory.resolve(
                            "pocket" + pocketNumber + "_atm.pdb"));
            List<AlphaSphere> spheres = readAlphaSpheres(
                    sourceDirectory.resolve(
                            "pocket" + pocketNumber + "_vert.pqr"));

            Point3D center = centroid(spheres);
            ParsedPocketInfo parsed = entry.getValue();
            pockets.add(new Pocket(
                    PocketId.of(pocketNumber),
                    "Pocket " + pocketNumber,
                    PocketSource.FPOCKET,
                    center,
                    residues,
                    parsed.metrics(),
                    Optional.empty(),
                    spheres.isEmpty()
                            ? Optional.empty()
                            : Optional.of(new AlphaSphereSet(spheres)),
                    parsed.metadata()));
        }
        return List.copyOf(pockets);
    }

    static Map<Long, ParsedPocketInfo> parseInfoFile(Path path)
            throws IOException {

        Map<Long, ParsedPocketInfoBuilder> builders =
                new LinkedHashMap<>();
        ParsedPocketInfoBuilder current = null;

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher header = POCKET_HEADER.matcher(line);
                if (header.find()) {
                    long id = Long.parseLong(header.group(1));
                    current = new ParsedPocketInfoBuilder();
                    builders.put(id, current);
                    continue;
                }
                if (current == null) {
                    continue;
                }

                Matcher metric = METRIC.matcher(line);
                if (metric.find()) {
                    current.add(
                            normalizeMetricName(metric.group(1)),
                            Double.parseDouble(metric.group(2)));
                }
            }
        }

        Map<Long, ParsedPocketInfo> parsed = new LinkedHashMap<>();
        builders.forEach((id, builder) -> parsed.put(id, builder.build()));
        return Collections.unmodifiableMap(parsed);
    }

    public static List<ResidueId> readPocketResidues(Path atomFile)
            throws IOException {

        Set<ResidueId> residues = new LinkedHashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(atomFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("ATOM") || line.startsWith("HETATM")) {
                    String chain = line.substring(21, 22).trim();
                    int number = Integer.parseInt(
                            line.substring(22, 26).trim());
                    Character insertionCode = line.length() > 26
                            ? normalizeInsertionCode(line.charAt(26))
                            : null;
                    residues.add(new ResidueId(
                            chain,
                            number,
                            insertionCode));
                }
            }
        }
        return List.copyOf(residues);
    }

    public static List<AlphaSphere> readAlphaSpheres(Path vertexFile)
            throws IOException {

        List<AlphaSphere> spheres = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(vertexFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("ATOM") || line.startsWith("HETATM")) {
                    /*
                     * fpocket writes coordinates with fixed precision, so
                     * adjacent negative values can run together without a
                     * separating space (e.g. "-60.303-100.544"). Insert a
                     * space between a digit and a following minus sign;
                     * scientific notation ("1.0E-05") is unaffected because
                     * its '-' follows 'E', not a digit.
                     */
                    String[] tokens = line.trim()
                            .replaceAll("(\\d)(-)", "$1 $2")
                            .split("\\s+");
                    spheres.add(new AlphaSphere(
                            Long.parseLong(tokens[1]),
                            new Point3D(
                                    Double.parseDouble(tokens[5]),
                                    Double.parseDouble(tokens[6]),
                                    Double.parseDouble(tokens[7])),
                            Double.parseDouble(tokens[tokens.length - 1])));
                }
            }
        }
        return List.copyOf(spheres);
    }

    private static Point3D centroid(List<AlphaSphere> spheres) {

        if (spheres.isEmpty()) {
            return new Point3D(0.0, 0.0, 0.0);
        }
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (AlphaSphere sphere : spheres) {
            x += sphere.center().x();
            y += sphere.center().y();
            z += sphere.center().z();
        }
        return new Point3D(
                x / spheres.size(),
                y / spheres.size(),
                z / spheres.size());
    }

    private static Path findInfoFile(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(INFO_SUFFIX))
                    .findFirst()
                    .orElseThrow(() -> new IOException(
                            "No fpocket info file found in " + folder));
        }
    }

    private static Character normalizeInsertionCode(char value) {
        return Character.isWhitespace(value) || value == '\0'
                ? null
                : value;
    }

    private static String normalizeMetricName(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private static Map<String, PocketMetricType> metricTypes() {
        Map<String, PocketMetricType> types = new LinkedHashMap<>();
        types.put("score", PocketMetricType.FPOCKET_SCORE);
        types.put("druggability score", PocketMetricType.FPOCKET_DRUGGABILITY);
        types.put("number of alpha spheres", PocketMetricType.ALPHA_SPHERE_COUNT);
        types.put("total sasa", PocketMetricType.TOTAL_SASA);
        types.put("polar sasa", PocketMetricType.POLAR_SASA);
        types.put("apolar sasa", PocketMetricType.APOLAR_SASA);
        types.put("volume", PocketMetricType.VOLUME);
        types.put("mean local hydrophobic density", PocketMetricType.MEAN_LOCAL_HYDROPHOBIC_DENSITY);
        types.put("mean alpha sphere radius", PocketMetricType.MEAN_ALPHA_SPHERE_RADIUS);
        types.put("mean alp. sph. solvent access", PocketMetricType.MEAN_ALPHA_SPHERE_SOLVENT_ACCESS);
        types.put("apolar alpha sphere proportion", PocketMetricType.APOLAR_ALPHA_SPHERE_PROPORTION);
        types.put("hydrophobicity score", PocketMetricType.HYDROPHOBICITY_SCORE);
        types.put("volume score", PocketMetricType.VOLUME_SCORE);
        types.put("polarity score", PocketMetricType.POLARITY_SCORE);
        types.put("charge score", PocketMetricType.CHARGE_SCORE);
        types.put("proportion of polar atoms", PocketMetricType.POLAR_ATOM_PROPORTION);
        types.put("alpha sphere density", PocketMetricType.ALPHA_SPHERE_DENSITY);
        types.put("cent. of mass - alpha sphere max dist", PocketMetricType.ALPHA_SPHERE_MAX_DISTANCE);
        types.put("flexibility", PocketMetricType.FLEXIBILITY);
        return Map.copyOf(types);
    }

    record ParsedPocketInfo(
            List<PocketMetric> metrics,
            Map<String, String> metadata) {
    }

    private static final class ParsedPocketInfoBuilder {
        private final Map<PocketMetricType, PocketMetric> metrics =
                new EnumMap<>(PocketMetricType.class);
        private final Map<String, String> metadata = new LinkedHashMap<>();

        private void add(String sourceName, double value) {
            PocketMetricType type = METRIC_TYPES.get(sourceName);
            if (type == null) {
                metadata.put(sourceName, Double.toString(value));
            } else {
                metrics.put(type, new PocketMetric(type, value));
            }
        }

        private ParsedPocketInfo build() {
            return new ParsedPocketInfo(
                    List.copyOf(metrics.values()),
                    Map.copyOf(metadata));
        }
    }
}
