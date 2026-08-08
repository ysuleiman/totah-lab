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
                    findPocketAtomFile(sourceDirectory, pocketNumber));
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

        if (atomFile.getFileName().toString().toLowerCase(Locale.ROOT)
                .endsWith(".cif")) {
            return readMmcifPocketResidues(atomFile);
        }

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

    private static List<ResidueId> readMmcifPocketResidues(Path atomFile)
            throws IOException {
        Set<ResidueId> residues = new LinkedHashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(atomFile)) {
            String line;
            List<String> columns = null;
            List<String> tokens = new ArrayList<>();
            boolean loop = false;
            while ((line = reader.readLine()) != null) {
                String value = line.trim();
                if (columns == null) {
                    if ("loop_".equals(value)) {
                        loop = true;
                    } else if (loop && value.startsWith("_atom_site.")) {
                        columns = new ArrayList<>();
                        columns.add(value.split("\\s+", 2)[0]);
                    } else if (!value.isEmpty() && !value.startsWith("#")) {
                        loop = false;
                    }
                    continue;
                }
                if (value.startsWith("_atom_site.")) {
                    columns.add(value.split("\\s+", 2)[0]);
                    continue;
                }
                if (value.isEmpty()) {
                    continue;
                }
                if (value.startsWith("#") || value.startsWith("_")
                        || value.equals("loop_") || value.startsWith("data_")) {
                    emitMmcifResidues(columns, tokens, residues, atomFile);
                    break;
                }
                tokens.addAll(tokenizeMmcif(value));
                emitMmcifResidues(columns, tokens, residues, atomFile);
            }
            if (columns != null) {
                emitMmcifResidues(columns, tokens, residues, atomFile);
            }
            if (!tokens.isEmpty()) {
                throw new IOException("Incomplete _atom_site row in " + atomFile);
            }
        }
        return List.copyOf(residues);
    }

    private static void emitMmcifResidues(List<String> columns,
            List<String> tokens, Set<ResidueId> residues, Path path)
            throws IOException {
        while (tokens.size() >= columns.size()) {
            List<String> row = new ArrayList<>(tokens.subList(0, columns.size()));
            tokens.subList(0, columns.size()).clear();
            String group = cifValue(columns, row, "_atom_site.group_PDB");
            if (!"ATOM".equals(group) && !"HETATM".equals(group)) {
                continue;
            }
            String chain = firstDefined(
                    cifValue(columns, row, "_atom_site.auth_asym_id"),
                    cifValue(columns, row, "_atom_site.label_asym_id"));
            String number = firstDefined(
                    cifValue(columns, row, "_atom_site.auth_seq_id"),
                    cifValue(columns, row, "_atom_site.label_seq_id"));
            if (chain == null || number == null) {
                throw new IOException("Missing residue identity in " + path);
            }
            String insertion = cifValue(columns, row,
                    "_atom_site.pdbx_PDB_ins_code");
            try {
                residues.add(new ResidueId(chain, Integer.parseInt(number),
                        insertion == null || insertion.isBlank()
                                ? null : insertion.charAt(0)));
            } catch (NumberFormatException e) {
                throw new IOException("Invalid residue number in " + path
                        + ": " + number, e);
            }
        }
    }

    private static String cifValue(List<String> columns, List<String> row,
            String column) {
        int index = columns.indexOf(column);
        if (index < 0) {
            return null;
        }
        String value = row.get(index);
        return value.equals(".") || value.equals("?") ? null : value;
    }

    private static String firstDefined(String first, String second) {
        return first == null ? second : first;
    }

    private static List<String> tokenizeMmcif(String line) throws IOException {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < line.length();) {
            while (index < line.length()
                    && Character.isWhitespace(line.charAt(index))) {
                index++;
            }
            if (index >= line.length() || line.charAt(index) == '#') {
                break;
            }
            char quote = line.charAt(index);
            if (quote == '\'' || quote == '"') {
                int end = line.indexOf(quote, index + 1);
                if (end < 0) {
                    throw new IOException("Unterminated mmCIF quote: " + line);
                }
                result.add(line.substring(index + 1, end));
                index = end + 1;
            } else {
                int end = index;
                while (end < line.length()
                        && !Character.isWhitespace(line.charAt(end))) {
                    end++;
                }
                result.add(line.substring(index, end));
                index = end;
            }
        }
        return result;
    }

    private static Path findPocketAtomFile(Path sourceDirectory,
            long pocketNumber) throws IOException {
        String stem = "pocket" + pocketNumber + "_atm";
        Path pdb = sourceDirectory.resolve(stem + ".pdb");
        if (Files.isRegularFile(pdb)) {
            return pdb;
        }
        Path cif = sourceDirectory.resolve(stem + ".cif");
        if (Files.isRegularFile(cif)) {
            return cif;
        }
        throw new IOException("No PDB or mmCIF atom file for pocket "
                + pocketNumber + " in " + sourceDirectory);
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
