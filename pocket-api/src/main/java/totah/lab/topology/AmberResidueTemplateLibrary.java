package totah.lab.topology;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Singleton Amber residue template library.
 * Loads all .lib/.off template files and Amber PREPI residues from a directory.
 */
public class AmberResidueTemplateLibrary implements ResidueTemplateProvider {

    private static final AmberResidueTemplateLibrary INSTANCE = new AmberResidueTemplateLibrary();

    private final Map<String, ResidueTemplate> templates = new HashMap<>();
    private volatile boolean loaded = false;

    private AmberResidueTemplateLibrary() {}

    public static AmberResidueTemplateLibrary getInstance() {
        if (!INSTANCE.loaded) {
            synchronized (INSTANCE) {
                if (!INSTANCE.loaded) {
                    loadFromClasspath();
                }
            }
        }
        return INSTANCE;
    }

    private static void loadFromClasspath() {
        INSTANCE.loadClasspathDirectory("/amber/lib", true);
        INSTANCE.loadClasspathDirectory("/amber/prep", false);
        INSTANCE.loaded = true;
    }

    private void loadClasspathDirectory(String resourceName, boolean required) {
        java.net.URL url = AmberResidueTemplateLibrary.class.getResource(resourceName);
        if (url == null) {
            if (!required) return;
            throw new IllegalStateException(
                    "Default Amber templates directory (" + resourceName + ") not found in classpath. " +
                            "Call load(Path) or loadDirectory(Path) with explicit template location."
            );
        }

        URI uri;
        try {
            uri = url.toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URI for " + resourceName + ": " + url, e);
        }

        Path dir;
        FileSystem fs = null;
        boolean closeFs = false;

        try {
            if ("jar".equals(uri.getScheme())) {
                try {
                    fs = FileSystems.getFileSystem(uri);
                    dir = fs.getPath(resourceName);
                } catch (FileSystemNotFoundException e) {
                    fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                    closeFs = true;
                    dir = fs.getPath(resourceName);
                }
            } else {
                dir = Paths.get(uri);
            }

            if (dir == null) {
                throw new IllegalStateException("Could not resolve " + resourceName + " path");
            }

            loadDirectory(dir);

        } catch (IOException e) {
            throw new RuntimeException("Failed to load default Amber templates from " + resourceName, e);
        } finally {
            if (closeFs && fs != null) {
                try {
                    fs.close();
                } catch (IOException e) {
                    System.err.println("Warning: Failed to close JAR filesystem: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Load all .lib/.off/.prepi files from a directory.
     */
    public void loadDirectory(Path dir) throws IOException {
        if (dir == null) {
            throw new IllegalArgumentException("Directory path cannot be null");
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a directory: " + dir);
        }

        synchronized (this) {
            try (Stream<Path> files = Files.walk(dir)) {
                files.filter(Objects::nonNull).filter(Files::isRegularFile).filter(p -> {
                    Path fileName = p.getFileName();
                    if (fileName == null) return false;
                    String name = fileName.toString().toLowerCase();
                    return name.endsWith(".lib") || name.endsWith(".off") || name.endsWith(".prepi");
                }).sorted().forEach(this::loadFileUnchecked);
            }
            loaded = true;
        }
    }

    /**
     * Load a single template file.
     */
    public void load(Path file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }

        synchronized (this) {
            loadFile(file);
            loaded = true;
        }
    }

    private void loadFileUnchecked(Path file) {
        try {
            loadFile(file);
        } catch (IOException e) {
            System.err.println("Warning: Failed to load template file " + file + ": " + e.getMessage());
        }
    }

    private void loadFile(Path file) throws IOException {
        String fileName = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".prepi")) {
            loadPrepiFile(file);
            return;
        }

        List<String> lines = Files.readAllLines(file);
        ResidueTemplate current = null;
        String section = "";

        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("!entry.") && line.contains(".unit.atoms ")) {
                String[] parts = line.split("\\.");
                if (parts.length < 2) continue;
                String residue = parts[1];
                // A residue can be defined in several .lib files (e.g.
                // all_amino94.lib and amino12.lib): the latest definition
                // replaces the earlier one instead of appending duplicate
                // atoms and bonds.
                current = new ResidueTemplate(residue);
                templates.put(residue, current);
                section = "atoms";
                continue;
            }
            if (line.startsWith("!entry.") && line.contains(".unit.connectivity")) {
                section = "bonds";
                continue;
            }
            if (line.startsWith("!entry.")) {
                section = "";
                continue;
            }

            if (current == null) continue;
            switch (section) {
                case "atoms":
                    parseAtom(line, current);
                    break;
                case "bonds":
                    parseBond(line, current);
                    break;
            }
        }
    }

    public ResidueTemplate getTemplate(String residueName) {
        return templates.get(residueName);
    }

    public Map<String, ResidueTemplate> getTemplates() {
        return new HashMap<>(templates);
    }

    private void parseAtom(String line, ResidueTemplate residue) {
        if (line == null || residue == null) return;
        String[] t = line.replace("\"", "").split("\\s+");
        if (t.length < 8) return;

        String name = t[0];
        String type = t[1];
        double charge;
        try {
            charge = Double.parseDouble(t[7]);
        } catch (Exception e) {
            charge = 0.0;
        }
        residue.addAtom(AtomTemplate.builder()
                .name(name)
                .amberType(type)
                .charge(charge)
                .build());
    }

    private void parseBond(String line, ResidueTemplate residue) {
        if (line == null || residue == null) return;
        String[] t = line.split("\\s+");
        if (t.length < 2) return;

        try {
            int i1 = Integer.parseInt(t[0]);
            int i2 = Integer.parseInt(t[1]);
            // Amber OFF connectivity indices are 1-based
            String name1 = residue.getAtomNameByIndex(i1);
            String name2 = residue.getAtomNameByIndex(i2);
            if (name1 == null || name2 == null) return;
            residue.addBond(BondTemplate.builder()
                    .atom1(name1)
                    .atom2(name2)
                    .build());
        } catch (NumberFormatException e) {
            // malformed line
        }
    }

    private void loadPrepiFile(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        ResidueTemplate current = null;
        Map<String, ResidueTemplate> parsedTemplates = new HashMap<>();
        Map<Integer, String> atomNamesByPrepiIndex = new HashMap<>();
        String section = "";

        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty()) continue;

            String[] fields = line.split("\\s+");
            if (fields.length >= 3 && "INT".equals(fields[1])) {
                current = new ResidueTemplate(fields[0]);
                parsedTemplates.put(fields[0], current);
                atomNamesByPrepiIndex = new HashMap<>();
                section = "atoms";
                continue;
            }
            if (current == null) continue;
            if ("LOOP".equals(line)) {
                section = "loop";
                continue;
            }
            if ("IMPROPER".equals(line) || "DONE".equals(line) || "STOP".equals(line)) {
                section = "";
                continue;
            }

            if ("atoms".equals(section)) {
                parsePrepiAtom(line, current, atomNamesByPrepiIndex);
            } else if ("loop".equals(section)) {
                parsePrepiLoop(line, current);
            }
        }

        validatePublishedTysTemplate(parsedTemplates.get("TYS"));
        templates.putAll(parsedTemplates);
    }

    private void parsePrepiAtom(String line, ResidueTemplate residue, Map<Integer, String> atomNamesByPrepiIndex) {
        String[] t = line.split("\\s+");
        if (t.length < 11) return;

        int index;
        int parentIndex;
        double charge;
        try {
            index = Integer.parseInt(t[0]);
            parentIndex = Integer.parseInt(t[4]);
            charge = Double.parseDouble(t[10]);
        } catch (NumberFormatException e) {
            return;
        }

        String name = t[1];
        if ("DUMM".equals(name)) {
            return;
        }

        String type = t[2];
        residue.addAtom(AtomTemplate.builder()
                .name(name)
                .amberType(type)
                .charge(charge)
                .build());
        atomNamesByPrepiIndex.put(index, name);

        String parentName = atomNamesByPrepiIndex.get(parentIndex);
        if (parentName != null) {
            residue.addBond(BondTemplate.builder()
                    .atom1(parentName)
                    .atom2(name)
                    .build());
        }
    }

    private void parsePrepiLoop(String line, ResidueTemplate residue) {
        String[] t = line.split("\\s+");
        if (t.length < 2) return;
        if (residue.getAtom(t[0]) == null || residue.getAtom(t[1]) == null) return;
        residue.addBond(BondTemplate.builder()
                .atom1(t[0])
                .atom2(t[1])
                .build());
    }

    private void validatePublishedTysTemplate(ResidueTemplate tys) {
        if (tys == null) return;

        double totalCharge = tys.getAtoms().stream()
                .mapToDouble(AtomTemplate::getCharge)
                .sum();
        if (Math.abs(totalCharge - (-1.0)) > 1e-4) {
            throw new IllegalStateException("TYS charge must be -1.0, found: " + totalCharge);
        }

        requireBond(tys, "CZ", "OH");
        requireBond(tys, "OH", "S");
        requireBond(tys, "S", "O1");
        requireBond(tys, "S", "O2");
        requireBond(tys, "S", "O3");
    }

    private void requireBond(ResidueTemplate template, String atom1, String atom2) {
        boolean found = template.getBonds().stream().anyMatch(bond ->
                (bond.getAtom1().equals(atom1) && bond.getAtom2().equals(atom2))
                        || (bond.getAtom1().equals(atom2) && bond.getAtom2().equals(atom1)));
        if (!found) {
            throw new IllegalStateException(template.getName() + " template must contain bond "
                    + atom1 + "-" + atom2);
        }
    }
}
