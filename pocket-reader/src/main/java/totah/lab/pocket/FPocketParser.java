package totah.lab.pocket;

import totah.lab.protein.Point3D;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class FPocketParser {

    private static final String INFO_SUFFIX = "_info.txt";

    private FPocketParser(){}

    public static List<Pocket> parse(Path pocketsDir) throws IOException {
        Path pathToInfo = findInfoFile(pocketsDir);
        if(!Files.exists(pathToInfo)) {
            throw new FileNotFoundException(pathToInfo.toString());
        }

        // 1. Fetch fluid, unbuilt builders from the info file text loop
        Map<Long, Pocket.PocketBuilder> pocketBuilders = parseInfoFileToBuilders(pathToInfo);
        List<Pocket> completedPockets = new ArrayList<>();

        for (Map.Entry<Long, Pocket.PocketBuilder> entry : pocketBuilders.entrySet()) {
            long pocketId = entry.getKey();
            Pocket.PocketBuilder builder = entry.getValue();

            // 2. Safely read and inject residue tracking data via the builder
            String fileName = "pocket" + pocketId + "_atm.pdb";
            List<ResidueRef> residues = readPocketResidues(pocketsDir.resolve("pockets").resolve(fileName));
            builder.residueRefs(residues);

            // 3. Read alpha spheres and append them safely into properties
            List<Sphere> spheres = readAlphaSpheres(pocketsDir.resolve("pockets").resolve("pocket" + pocketId + "_vert.pqr"));

            // Centroid of the alpha spheres must be set on the builder before build()
            if (!spheres.isEmpty()) {
                double totalX = 0, totalY = 0, totalZ = 0;
                for (Sphere s : spheres) {
                    totalX += s.x();
                    totalY += s.y();
                    totalZ += s.z();
                }
                int count = spheres.size();
                builder.center(new Point3D(totalX / count, totalY / count, totalZ / count));
            }

            // Build the final immutable object exactly when parsing wraps up
            Pocket pocket = builder.build();
            pocket.add("alpha_spheres", spheres);
            completedPockets.add(pocket);
        }

        return completedPockets;
    }

    public static Map<Long, Pocket.PocketBuilder> parseInfoFileToBuilders(Path path) throws IOException {
        Map<Long, Pocket.PocketBuilder> builders = new HashMap<>();
        Pattern pocketHeaderPattern = Pattern.compile("Pocket\\s+(\\d+)\\s*:");
        Pattern metricPattern = Pattern.compile("^\\s*([^:]+?)\\s*:\\s*([-+]?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)");

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            Pocket.PocketBuilder currentBuilder = null;
            Map<String, Object> currentProperties = new HashMap<>();

            while ((line = reader.readLine()) != null) {
                Matcher headerMatcher = pocketHeaderPattern.matcher(line);
                if (headerMatcher.find()) {
                    long pocketId = Long.parseLong(headerMatcher.group(1));

                    // Reset mapping trackers for the new pocket block
                    currentProperties = new HashMap<>();
                    currentBuilder = Pocket.builder()
                            .source(PocketSource.builder().source("FPOCKET").build())
                            .id(pocketId).attributes(currentProperties);

                    builders.put(pocketId, currentBuilder);
                    continue;
                }
                if (currentBuilder == null) {
                    // Tolerate any preamble text before the first pocket header
                    continue;
                }

                Matcher metricMatcher = metricPattern.matcher(line);
                if (metricMatcher.find()) {
                    String key = metricMatcher.group(1).trim().toLowerCase();
                    String valStr = metricMatcher.group(2).trim();

                    if ("score".equals(key)) {
                        // Correctly set your universal, top-level score without using setters
                        currentBuilder.score(Double.parseDouble(valStr));
                    } else {
                        currentProperties.put(key, valStr);
                    }
                }
            }
        }
        return builders;
    }

    public static List<ResidueRef> readPocketResidues(Path atmFile) throws IOException {
        Set<ResidueRef> residues = new LinkedHashSet<>();
        try (BufferedReader br = Files.newBufferedReader(atmFile)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("ATOM") || line.startsWith("HETATM")) {
                    String name = line.substring(17, 20).trim();
                    String chain = line.substring(21, 22).trim();
                    int number = Integer.parseInt(line.substring(22, 26).trim());
                    residues.add(new ResidueRef(chain, number, name));
                }
            }
        }
        return new ArrayList<>(residues);
    }

    public static List<Sphere> readAlphaSpheres(Path vertFile) throws IOException {
        List<Sphere> sphereList = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(vertFile)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("ATOM") || line.startsWith("HETATM")) {
                    // PQR layout is whitespace-delimited; the charge column
                    // precedes the radius, which is the final token
                    String[] tokens = line.trim().split("\\s+");
                    int id = Integer.parseInt(tokens[1]);
                    double x = Double.parseDouble(tokens[5]);
                    double y = Double.parseDouble(tokens[6]);
                    double z = Double.parseDouble(tokens[7]);
                    double radius = Double.parseDouble(tokens[tokens.length - 1]);
                    sphereList.add(new Sphere(id, x, y, z, radius));
                }
            }
        }
        return sphereList;
    }

    private static Path findInfoFile(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(INFO_SUFFIX))
                    .findFirst()
                    .orElseThrow(() -> new IOException("No fpocket info file found in " + folder));
        }
    }
}
