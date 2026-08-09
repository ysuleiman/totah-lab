package totah.lab.web.ligandcontact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Extracts residue-to-ligand-moiety contacts from a BioHub complex
 * PDB: for every protein residue, the minimum distance to each
 * {@link SamMoiety} of the ligand (SAM or SAH).
 *
 * <p>The complex PDBs are parsed with a small column-based reader
 * rather than the structure pipeline because only atom names,
 * residue identity, and coordinates are needed, and ligand HETATM
 * records must be retained.</p>
 */
public final class ComplexLigandContactExtractor {

    /**
     * Per-residue ligand-moiety contact summary: the minimum distance
     * to each moiety, the nearest moiety, and whether that nearest
     * contact is within the direct-contact cutoff.
     */
    public record ResidueMoietyContact(
            String chain,
            int residueNumber,
            String residueName,
            Map<SamMoiety, Double> minimumDistances,
            SamMoiety facingMoiety,
            double facingDistance,
            boolean directContact
    ) {
        public ResidueMoietyContact {
            minimumDistances =
                    new EnumMap<>(Map.copyOf(minimumDistances));
        }
    }

    /**
     * Reads a complex PDB and computes per-residue moiety contacts.
     *
     * @param complexPdb complex model containing protein ATOM records
     *                   and the ligand as HETATM records
     * @param ligandCcd ligand residue name to select (e.g. SAM, SAH)
     * @param directContactCutoffAngstroms cutoff used for the direct
     *                   contact flag (the evidence uses 4.5)
     */
    public List<ResidueMoietyContact> extract(
            Path complexPdb,
            String ligandCcd,
            double directContactCutoffAngstroms
    ) throws IOException {
        Objects.requireNonNull(complexPdb, "complexPdb");
        Objects.requireNonNull(ligandCcd, "ligandCcd");

        Map<ResidueKey, List<double[]>> proteinAtoms =
                new LinkedHashMap<>();
        Map<ResidueKey, String> residueNames = new LinkedHashMap<>();
        Map<SamMoiety, List<double[]>> ligandAtoms = new EnumMap<>(
                SamMoiety.class
        );
        for (SamMoiety moiety : SamMoiety.values()) {
            ligandAtoms.put(moiety, new ArrayList<>());
        }

        for (String line : Files.readAllLines(complexPdb)) {
            if (line.startsWith("ATOM")) {
                ResidueKey key = new ResidueKey(
                        column(line, 21, 22).trim(),
                        parseInt(column(line, 22, 26))
                );
                residueNames.putIfAbsent(
                        key,
                        column(line, 17, 20).trim()
                );
                proteinAtoms.computeIfAbsent(
                        key,
                        ignored -> new ArrayList<>()
                ).add(coordinates(line));
            } else if (line.startsWith("HETATM")
                    && ligandCcd.equalsIgnoreCase(
                    column(line, 17, 20).trim())) {
                SamMoiety.classify(column(line, 12, 16))
                        .ifPresent(moiety -> ligandAtoms
                                .get(moiety)
                                .add(coordinates(line)));
            }
        }

        if (ligandAtoms.values().stream()
                .allMatch(List::isEmpty)) {
            throw new IOException(
                    "No " + ligandCcd + " ligand atoms classified in "
                            + complexPdb
            );
        }

        List<ResidueMoietyContact> contacts = new ArrayList<>();
        for (Map.Entry<ResidueKey, List<double[]>> entry :
                proteinAtoms.entrySet()) {
            Map<SamMoiety, Double> minima = new EnumMap<>(
                    SamMoiety.class
            );
            for (SamMoiety moiety : SamMoiety.values()) {
                double minimum = minimumDistance(
                        entry.getValue(),
                        ligandAtoms.get(moiety)
                );
                if (!Double.isNaN(minimum)) {
                    minima.put(moiety, minimum);
                }
            }
            if (minima.isEmpty()) {
                continue;
            }

            SamMoiety facing = null;
            double facingDistance = Double.MAX_VALUE;
            for (Map.Entry<SamMoiety, Double> minimum :
                    minima.entrySet()) {
                if (minimum.getValue() < facingDistance) {
                    facing = minimum.getKey();
                    facingDistance = minimum.getValue();
                }
            }

            contacts.add(new ResidueMoietyContact(
                    entry.getKey().chain(),
                    entry.getKey().residueNumber(),
                    residueNames.get(entry.getKey()),
                    minima,
                    facing,
                    facingDistance,
                    facingDistance <= directContactCutoffAngstroms
            ));
        }
        return List.copyOf(contacts);
    }

    private static double minimumDistance(
            List<double[]> proteinAtoms,
            List<double[]> ligandAtoms
    ) {
        if (ligandAtoms.isEmpty()) {
            return Double.NaN;
        }
        double minimum = Double.MAX_VALUE;
        for (double[] protein : proteinAtoms) {
            for (double[] ligand : ligandAtoms) {
                double dx = protein[0] - ligand[0];
                double dy = protein[1] - ligand[1];
                double dz = protein[2] - ligand[2];
                double distance =
                        Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance < minimum) {
                    minimum = distance;
                }
            }
        }
        return minimum;
    }

    private static String column(String line, int from, int to) {
        if (line.length() <= from) {
            return "";
        }
        return line.substring(from, Math.min(to, line.length()));
    }

    private static double[] coordinates(String line) {
        return new double[]{
                parseDouble(column(line, 30, 38)),
                parseDouble(column(line, 38, 46)),
                parseDouble(column(line, 46, 54))
        };
    }

    private static int parseInt(String value) {
        return Integer.parseInt(value.trim());
    }

    private static double parseDouble(String value) {
        return Double.parseDouble(value.trim());
    }

    private record ResidueKey(String chain, int residueNumber) {
    }
}
