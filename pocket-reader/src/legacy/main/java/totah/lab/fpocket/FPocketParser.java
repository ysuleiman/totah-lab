package totah.lab.fpocket;

import totah.lab.pocket.AlphaSphere;
import totah.lab.pocket.Pocket;
import totah.lab.pocket.Residue;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FPocketParser {

    private static final Pattern NUMBER =
            Pattern.compile("[-+]?[0-9]*\\.?[0-9]+");

    private FPocketParser(){}

    public static List<Pocket> parse(Path folder) throws IOException {

        Path parent = folder.getParent();
        String name = folder.getFileName().toString();
        if (name.endsWith("_out")) {
            name = name.substring(0, name.length() - "_out".length());
        }
        Path modified = folder.resolve(name+"_info.txt");
        List<Pocket> pockets = parseInfoFile(modified);
        for (Pocket pocket : pockets) {
            Path p = folder.resolve("pockets").resolve("pocket" + pocket.getId() + "_atm.pdb");
            List<Residue> residues = readResidues(p);
            pocket.addResidues(residues);
            List<AlphaSphere>spheres = readAlphaSpheres(folder.resolve("pockets").resolve("pocket" + pocket.getId() + "_vert.pqr"));
            pocket.addAlphaSpheres(spheres);
            //Path vert = folder.resolve("pockets").resolve("pocket" + pocket.getId() + "_vert.pqr");
            //List<SphereVertex> spheres = readSphereVertices(vert);
            //pocket.add(spheres);

            // Compute center and size with a standard 5.0 Å flexible padding
            //GridBox gridBox=GridBox.computeFromSpheres(spheres, 5);
            //pocket.setGridBox(gridBox);
            // Keep your original array setter synchronized with the new GridBox center
            //double[] center = pocket.getGridBox().getCenterCoordinates();
            //System.out.println("Calculated Center: " + Arrays.toString(center));
            //System.out.println(Arrays.toString(center));
            //pocket.setCenter(center);
        }
        return pockets;
    }
    public static List<Pocket> parse(Path infoFile, Path pocketsDir) throws IOException {
        List<Pocket> pockets = parseInfoFile(infoFile);
        for (Pocket pocket : pockets) {
            String fileName = "pocket" + pocket.getId() + "_atm.pdb";
            //File pdb = new File(pocketsDir, fileName);
            //System.out.println("reading: " + pdb.getAbsolutePath());
            List<Residue> residues = readResidues(pocketsDir.resolve(fileName));
            pocket.addResidues(residues);
            List<AlphaSphere>spheres = readAlphaSpheres(pocketsDir.resolve("pocket" + pocket.getId() + "_vert.pqr"));
            pocket.addAlphaSpheres(spheres);
            //File vert = new File(pocketsDir, "pocket" + pocket.getId() + "_vert.pqr");
            //Path vert = pocketsDir.resolve("pocket" + pocket.getId() + "_vert.pqr");
            //List<SphereVertex> spheres = readSphereVertices(vert);
            // Compute center and size with a standard 5.0 Å flexible padding
            //GridBox gridBox=GridBox.computeFromSpheres(spheres, 5);
            //pocket.setGridBox(gridBox);
            // Keep your original array setter synchronized with the new GridBox center
            //double[] center = pocket.getGridBox().getCenterCoordinates();
            //System.out.println("Calculated Center: " + Arrays.toString(center));
            //System.out.println(Arrays.toString(center));
            //pocket.setCenter(center);
        }
        return pockets;
    }

    public static List<Pocket> parseInfoFile(Path path) throws IOException {
        Map<Long, Pocket> pockets = new HashMap<>();
        Pattern pocketHeaderPattern = Pattern.compile("Pocket\\s+(\\d+)\\s*:");
        Pattern metricPattern = Pattern.compile("^\\s*([^:]+?)\\s*:\\s*([\\d.-]+)");
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            FPocket currentPocket = null;
            while ((line = reader.readLine()) != null) {
                Matcher headerMatcher = pocketHeaderPattern.matcher(line);
                if (headerMatcher.find()) {
                    long pocketId = Integer.parseInt(headerMatcher.group(1));
                    currentPocket = FPocket.builder().id(pocketId).build();
                    pockets.put(pocketId, currentPocket);
                    continue;
                }
                if (currentPocket == null) {
                    throw new IOException("Cannot find pocket header!");
                }
                Matcher metricMatcher = metricPattern.matcher(line);
                if (metricMatcher.find()) {
                    String key = metricMatcher.group(1).trim();
                    String valStr = metricMatcher.group(2).trim();
                    currentPocket.set(key, valStr);
                }
            }
        }
        return pockets.values().stream().toList();
    }

    public static List<AlphaSphere> readAlphaSpheres(Path vertFile) throws IOException {
        List<AlphaSphere> sphereList = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(vertFile)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("ATOM") || line.startsWith("HETATM")) {
                    int id = Integer.parseInt(line.substring(6, 11).trim());
                    double x = Double.parseDouble(line.substring(30, 38).trim());
                    double y = Double.parseDouble(line.substring(38, 46).trim());
                    double z = Double.parseDouble(line.substring(46, 54).trim());
                    double radius = Double.parseDouble(line.substring(54, 60).trim());

                    sphereList.add(new FPocketAlphaSphere(id, x, y, z, radius));
                }
            }
        }
        return sphereList;
    }

    public static List<Residue> readResidues(Path path) throws IOException {
        // FIX 1: Map tracks the specific builder type, not the final interface instance
        Map<String, FPocketResidue.FPocketResidueBuilder> residueMap = new LinkedHashMap<>();

        if(!Files.isRegularFile(path)){
            throw new IOException("Expected a path to a file but found: " + path);
        }

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("ATOM"))
                    continue;
                String atomName = line.substring(12, 16).trim();
                String resName = line.substring(17, 20).trim();
                String chain = line.substring(21, 22).trim();
                String position = line.substring(22, 26).trim();
                double x = Double.parseDouble(line.substring(30, 38).trim());
                double y = Double.parseDouble(line.substring(38, 46).trim());
                double z = Double.parseDouble(line.substring(46, 54).trim());
                int residueNumber  = Integer.parseInt(line.substring(22, 26).trim()); // 110
                String element = line.length() >= 78
                        ? line.substring(76, 78).trim()
                        : inferElement(atomName);
                String key = chain + ":" + residueNumber;


                FPocketResidue.FPocketResidueBuilder builder = residueMap.computeIfAbsent(
                        key,
                        k -> FPocketResidue.builder()
                                .chainId(chain)
                                .name(resName).number(residueNumber)
                                .position(position)
                );

                // FIX 2: Removed duplicate execution line tracking
                builder.atom(new FPocketAtom(atomName, element, x, y, z));
            }

            // FIX 3: Replaced bad direct manual instantiation with proper Lombok engine call
            return residueMap.values()
                    .stream()
                    .map(FPocketResidue.FPocketResidueBuilder::build)
                    .map(r -> (Residue) r) // Safe upcast to core framework domain API
                    .toList();
        }
    }

    private static String inferElement(String atomName) {
        if (atomName == null || atomName.isEmpty())
            return "";
        return String.valueOf(atomName.charAt(0));
    }
}
