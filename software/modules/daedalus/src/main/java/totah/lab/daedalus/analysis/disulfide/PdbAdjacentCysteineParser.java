package totah.lab.daedalus.analysis.disulfide;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Small, dependency-free parser for finding sequence-adjacent cysteines in
 * PDB and PDB.GZ files. Residues retain their file order.
 */
public final class PdbAdjacentCysteineParser {

    private static final Map<String, Character> AMINO_ACID_CODES = aminoAcidCodes();

    public List<AdjacentCysteinePair> parse(Path pdbPath, int flankSize) throws IOException {
        Objects.requireNonNull(pdbPath, "pdbPath is null");
        if (flankSize < 0) {
            throw new IllegalArgumentException("flankSize must be non-negative");
        }

        Map<ResidueKey, MutableResidue> residues = new LinkedHashMap<>();
        try (InputStream fileInput = Files.newInputStream(pdbPath);
             InputStream structureInput = isGzip(pdbPath)
                     ? new GZIPInputStream(fileInput)
                     : fileInput;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(structureInput, StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseAtomLine(line, residues);
            }
        }

        Map<String, List<MutableResidue>> chains = new LinkedHashMap<>();
        for (MutableResidue residue : residues.values()) {
            chains.computeIfAbsent(residue.chain(), ignored -> new ArrayList<>()).add(residue);
        }

        List<AdjacentCysteinePair> pairs = new ArrayList<>();
        for (List<MutableResidue> chainResidues : chains.values()) {
            for (int index = 0; index + 1 < chainResidues.size(); index++) {
                MutableResidue first = chainResidues.get(index);
                MutableResidue second = chainResidues.get(index + 1);
                if (first.oneLetterCode() == 'C'
                        && second.oneLetterCode() == 'C'
                        && second.number() == first.number() + 1) {
                    pairs.add(toPair(chainResidues, index, flankSize));
                }
            }
        }
        return List.copyOf(pairs);
    }

    private static AdjacentCysteinePair toPair(
            List<MutableResidue> residues, int firstIndex, int flankSize) {
        MutableResidue first = residues.get(firstIndex);
        MutableResidue second = residues.get(firstIndex + 1);
        int from = Math.max(0, firstIndex - flankSize);
        int to = Math.min(residues.size(), firstIndex + 2 + flankSize);
        StringBuilder context = new StringBuilder(to - from);
        for (int index = from; index < to; index++) {
            context.append(residues.get(index).oneLetterCode());
        }

        Optional<Double> distance = first.sg().flatMap(left ->
                second.sg().map(right -> left.distance(right)));
        Optional<Double> chi3 = first.cb().flatMap(firstCb ->
                first.sg().flatMap(firstSg ->
                        second.sg().flatMap(secondSg ->
                                second.cb().flatMap(secondCb ->
                                        dihedralDegrees(firstCb, firstSg, secondSg, secondCb)))));

        return new AdjacentCysteinePair(
                first.chain(),
                first.number(),
                second.number(),
                context.toString(),
                firstIndex - from,
                distance,
                chi3,
                first.plddt(),
                second.plddt());
    }

    private static void parseAtomLine(
            String line, Map<ResidueKey, MutableResidue> residues) {
        if (!line.startsWith("ATOM  ") || line.length() < 66) {
            return;
        }
        char altLoc = charAt(line, 16);
        if (altLoc != ' ' && altLoc != 'A') {
            return;
        }

        String atomName = slice(line, 12, 16);
        String residueName = slice(line, 17, 20);
        char code = AMINO_ACID_CODES.getOrDefault(residueName, 'X');
        String chain = slice(line, 21, 22);
        int number;
        double x;
        double y;
        double z;
        double plddt;
        try {
            number = Integer.parseInt(slice(line, 22, 26));
            x = Double.parseDouble(slice(line, 30, 38));
            y = Double.parseDouble(slice(line, 38, 46));
            z = Double.parseDouble(slice(line, 46, 54));
            plddt = Double.parseDouble(slice(line, 60, 66));
        } catch (NumberFormatException ignored) {
            return;
        }

        ResidueKey key = new ResidueKey(chain, number, charAt(line, 26));
        MutableResidue residue = residues.computeIfAbsent(
                key, ignored -> new MutableResidue(chain, number, code, plddt));
        Point point = new Point(x, y, z);
        if ("SG".equals(atomName)) {
            residue.sg(point);
        } else if ("CB".equals(atomName)) {
            residue.cb(point);
        }
    }

    private static boolean isGzip(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(".gz");
    }

    private static String slice(String value, int from, int to) {
        return value.substring(from, Math.min(to, value.length())).trim();
    }

    private static char charAt(String value, int index) {
        return index < value.length() ? value.charAt(index) : ' ';
    }

    private static Optional<Double> dihedralDegrees(Point a, Point b, Point c, Point d) {
        Point b1 = b.subtract(a);
        Point b2 = c.subtract(b);
        Point b3 = d.subtract(c);
        double b2Length = b2.length();
        if (b2Length == 0.0) {
            return Optional.empty();
        }
        Point n1 = b1.cross(b2);
        Point n2 = b2.cross(b3);
        double x = n1.dot(n2);
        double y = n1.cross(n2).dot(b2) / b2Length;
        return Optional.of(Math.toDegrees(Math.atan2(y, x)));
    }

    private static Map<String, Character> aminoAcidCodes() {
        return Map.ofEntries(
                Map.entry("ALA", 'A'), Map.entry("ARG", 'R'),
                Map.entry("ASN", 'N'), Map.entry("ASP", 'D'),
                Map.entry("CYS", 'C'), Map.entry("GLN", 'Q'),
                Map.entry("GLU", 'E'), Map.entry("GLY", 'G'),
                Map.entry("HIS", 'H'), Map.entry("ILE", 'I'),
                Map.entry("LEU", 'L'), Map.entry("LYS", 'K'),
                Map.entry("MET", 'M'), Map.entry("PHE", 'F'),
                Map.entry("PRO", 'P'), Map.entry("SER", 'S'),
                Map.entry("THR", 'T'), Map.entry("TRP", 'W'),
                Map.entry("TYR", 'Y'), Map.entry("VAL", 'V'),
                Map.entry("SEC", 'U'));
    }

    public record AdjacentCysteinePair(
            String chain,
            int firstResidue,
            int secondResidue,
            String sequenceContext,
            int motifOffset,
            Optional<Double> sgDistanceAngstrom,
            Optional<Double> chi3Degrees,
            double firstPlddt,
            double secondPlddt) {

        public double meanPlddt() {
            return (firstPlddt + secondPlddt) / 2.0;
        }
    }

    private record ResidueKey(String chain, int number, char insertionCode) {
    }

    private record Point(double x, double y, double z) {
        private Point subtract(Point other) {
            return new Point(x - other.x, y - other.y, z - other.z);
        }

        private Point cross(Point other) {
            return new Point(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x);
        }

        private double dot(Point other) {
            return x * other.x + y * other.y + z * other.z;
        }

        private double length() {
            return Math.sqrt(dot(this));
        }

        private double distance(Point other) {
            return subtract(other).length();
        }
    }

    private static final class MutableResidue {
        private final String chain;
        private final int number;
        private final char oneLetterCode;
        private final double plddt;
        private Point sg;
        private Point cb;

        private MutableResidue(String chain, int number, char oneLetterCode, double plddt) {
            this.chain = chain;
            this.number = number;
            this.oneLetterCode = oneLetterCode;
            this.plddt = plddt;
        }

        private String chain() {
            return chain;
        }

        private int number() {
            return number;
        }

        private char oneLetterCode() {
            return oneLetterCode;
        }

        private double plddt() {
            return plddt;
        }

        private Optional<Point> sg() {
            return Optional.ofNullable(sg);
        }

        private Optional<Point> cb() {
            return Optional.ofNullable(cb);
        }

        private void sg(Point value) {
            sg = value;
        }

        private void cb(Point value) {
            cb = value;
        }
    }
}
