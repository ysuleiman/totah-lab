package totah.lab.prometheus.ingest;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.gaia.geometry.Point3D;

/**
 * Parser for {@code .xyz} geometry files: first line atom count, second line
 * comment, then one {@code symbol x y z} record per atom. The file atom order
 * is preserved as evidence order — no reordering against any canonical map is
 * attempted here.
 */
public final class XyzParser {

    private XyzParser() {
    }

    /** An xyz geometry: element symbols and coordinates, both in file (evidence) order. */
    public record XyzGeometry(List<String> elementSymbols, List<Point3D> coordinates) {

        public XyzGeometry {
            elementSymbols = List.copyOf(Objects.requireNonNull(elementSymbols, "elementSymbols"));
            coordinates = List.copyOf(Objects.requireNonNull(coordinates, "coordinates"));
            if (elementSymbols.size() != coordinates.size()) {
                throw new IllegalArgumentException(
                        "symbol count " + elementSymbols.size()
                                + " != coordinate count " + coordinates.size());
            }
        }

        public int atomCount() {
            return coordinates.size();
        }
    }

    /** Parses the xyz file at {@code file}. */
    public static XyzGeometry parse(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String countLine = reader.readLine();
            if (countLine == null) {
                throw new IOException("empty xyz file: " + file);
            }
            final int declared;
            try {
                declared = Integer.parseInt(countLine.trim());
            } catch (NumberFormatException e) {
                throw new IOException("xyz atom count not an integer in " + file + ": " + countLine, e);
            }
            if (declared < 1) {
                throw new IOException("xyz atom count must be >= 1 in " + file + ", got " + declared);
            }
            String comment = reader.readLine();
            if (comment == null) {
                throw new IOException("xyz file missing comment line: " + file);
            }
            List<String> symbols = new ArrayList<>(declared);
            List<Point3D> coordinates = new ArrayList<>(declared);
            String line;
            int parsed = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length < 4) {
                    throw new IOException("malformed xyz atom line " + (parsed + 3)
                            + " in " + file + ": " + line);
                }
                final double x;
                final double y;
                final double z;
                try {
                    x = Double.parseDouble(tokens[1]);
                    y = Double.parseDouble(tokens[2]);
                    z = Double.parseDouble(tokens[3]);
                } catch (NumberFormatException e) {
                    throw new IOException("non-numeric coordinate on line " + (parsed + 3)
                            + " in " + file + ": " + line, e);
                }
                symbols.add(tokens[0]);
                coordinates.add(new Point3D(x, y, z));
                parsed++;
            }
            if (parsed != declared) {
                throw new IOException("xyz atom count mismatch in " + file
                        + ": header declares " + declared + " but " + parsed + " atom lines found");
            }
            return new XyzGeometry(symbols, coordinates);
        }
    }

    /**
     * Reads only the declared atom count from the first line of an xyz file —
     * safe for large files since nothing else is loaded.
     */
    public static int declaredAtomCount(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String countLine = reader.readLine();
            if (countLine == null) {
                throw new IOException("empty xyz file: " + file);
            }
            try {
                return Integer.parseInt(countLine.trim());
            } catch (NumberFormatException e) {
                throw new IOException("xyz atom count not an integer in " + file + ": " + countLine, e);
            }
        }
    }
}
