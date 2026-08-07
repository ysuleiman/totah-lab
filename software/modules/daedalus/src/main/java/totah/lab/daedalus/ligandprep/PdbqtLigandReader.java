package totah.lab.daedalus.ligandprep;

import totah.lab.gaia.geometry.Point3D;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal PDBQT ligand reader for preparation comparison: ATOM/HETATM
 * records (name, coordinates, partial charge, AD4 atom type) and the
 * TORSDOF torsion count. Coordinates use the fixed PDB columns; charge
 * and AD4 type are the last two whitespace-separated tokens, which both
 * the hermes writer and Meeko's mk_prepare_ligand.py honor.
 */
public final class PdbqtLigandReader {

    private PdbqtLigandReader() {
    }

    public record PdbqtAtom(
            String name,
            Point3D position,
            double charge,
            String ad4Type,
            String element
    ) {
        public boolean hydrogen() {
            return "H".equals(element);
        }

        /**
         * The chemical element behind an AD4 atom type (A is aromatic
         * carbon; NA/OA/SA/HD map to N/O/S/H; everything else is the
         * type itself, e.g. Cl, Br, F, I).
         */
        static String elementOf(String ad4Type) {
            return switch (ad4Type) {
                case "A" -> "C";
                case "NA" -> "N";
                case "OA" -> "O";
                case "SA" -> "S";
                case "HD" -> "H";
                default -> ad4Type;
            };
        }
    }

    public record PdbqtLigand(
            List<PdbqtAtom> atoms,
            int torsdof
    ) {
        public PdbqtLigand {
            atoms = List.copyOf(atoms);
        }

        public List<PdbqtAtom> heavyAtoms() {
            return atoms.stream()
                    .filter(atom -> !atom.hydrogen())
                    .toList();
        }

        public double totalCharge() {
            return atoms.stream()
                    .mapToDouble(PdbqtAtom::charge)
                    .sum();
        }
    }

    public static PdbqtLigand read(Path path) throws IOException {
        return parse(Files.readAllLines(path));
    }

    static PdbqtLigand parse(List<String> lines) throws IOException {
        List<PdbqtAtom> atoms = new ArrayList<>();
        int torsdof = 0;

        for (String line : lines) {
            if (line.startsWith("ATOM") || line.startsWith("HETATM")) {
                atoms.add(parseAtom(line));
            } else if (line.startsWith("TORSDOF")) {
                torsdof = Integer.parseInt(line.substring(7).trim());
            }
        }

        if (atoms.isEmpty()) {
            throw new IOException("PDBQT contains no atoms");
        }
        return new PdbqtLigand(atoms, torsdof);
    }

    private static PdbqtAtom parseAtom(String line) throws IOException {
        if (line.length() < 54) {
            throw new IOException("Truncated PDBQT atom line: " + line);
        }

        String[] tokens = line.trim().split("\\s+");
        if (tokens.length < 2) {
            throw new IOException("Malformed PDBQT atom line: " + line);
        }

        final double charge;
        try {
            charge = Double.parseDouble(tokens[tokens.length - 2]);
        } catch (NumberFormatException exception) {
            throw new IOException(
                    "Malformed PDBQT charge: " + line, exception);
        }
        String ad4Type = tokens[tokens.length - 1];

        try {
            return new PdbqtAtom(
                    line.substring(12, 16).trim(),
                    new Point3D(
                            Double.parseDouble(
                                    line.substring(30, 38).trim()),
                            Double.parseDouble(
                                    line.substring(38, 46).trim()),
                            Double.parseDouble(
                                    line.substring(46, 54).trim())),
                    charge,
                    ad4Type,
                    PdbqtAtom.elementOf(ad4Type)
            );
        } catch (NumberFormatException | StringIndexOutOfBoundsException
                exception) {
            throw new IOException(
                    "Malformed PDBQT atom line: " + line, exception);
        }
    }
}
