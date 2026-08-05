package totah.lab.hermes.file.reader;

import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.chemistry.FormalCharge;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.LigandReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Reads a single-molecule V2000 SDF (or MOL) record into a {@link Ligand}
 * with one chain ('L') and one residue, preserving explicit 3D
 * coordinates, bond orders (order 4 becomes {@link BondOrder#AROMATIC})
 * and formal charges (atom-block charge codes and {@code M  CHG} lines).
 * V3000 records, multi-molecule files and radicals are rejected.
 */
public final class SdfLigandReader implements LigandReader {

    private static final String CHAIN_ID = "L";
    private static final int RESIDUE_NUMBER = 1;

    @Override
    public Ligand read(Path path) throws IOException {
        return readModel(path).ligand();
    }

    public SdfLigand readModel(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        List<String> lines = Files.readAllLines(
                path.toAbsolutePath().normalize(), StandardCharsets.UTF_8);
        return parse(lines);
    }

    public boolean supports(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".sdf") || name.endsWith(".mol");
    }

    private SdfLigand parse(List<String> lines) throws IOException {
        if (lines.size() < 4) {
            throw malformed("An SDF record requires a 3-line header and a counts line.");
        }
        String title = lines.get(0).trim();
        if (title.isEmpty()) {
            title = "LIG";
        }
        String counts = lines.get(3);
        if (counts.contains("V3000")) {
            throw malformed("V3000 SDF records are not supported.");
        }
        if (!counts.contains("V2000")) {
            throw malformed("Counts line is missing the V2000 marker: " + counts);
        }
        int atomCount = integer(field(counts, 0, 3), "atom count");
        int bondCount = integer(field(counts, 3, 6), "bond count");
        if (atomCount < 1) {
            throw malformed("An SDF record must contain at least one atom.");
        }
        if (lines.size() < 4 + atomCount + bondCount) {
            throw malformed("SDF record is truncated in the atom or bond block.");
        }

        List<Atom> atoms = new ArrayList<>();
        List<Integer> formalCharges = new ArrayList<>();
        for (int index = 0; index < atomCount; index++) {
            atoms.add(atom(lines.get(4 + index), index));
            formalCharges.add(chargeCode(field(lines.get(4 + index), 36, 39)));
        }

        List<ChemicalBond> bonds = new ArrayList<>();
        Set<Long> endpoints = new HashSet<>();
        for (int index = 0; index < bondCount; index++) {
            String line = lines.get(4 + atomCount + index);
            int first = integer(field(line, 0, 3), "bond atom") - 1;
            int second = integer(field(line, 3, 6), "bond atom") - 1;
            int type = integer(field(line, 6, 9), "bond type");
            if (first < 0 || first >= atomCount || second < 0 || second >= atomCount) {
                throw malformed("Bond references an atom outside the atom block: " + line);
            }
            if (first == second) {
                throw malformed("Bond connects an atom to itself: " + line);
            }
            int low = Math.min(first, second);
            int high = Math.max(first, second);
            if (!endpoints.add(((long) low << 32) | (high & 0xffffffffL))) {
                throw malformed("Duplicate bond between atoms "
                        + (low + 1) + " and " + (high + 1) + ".");
            }
            BondOrder order = switch (type) {
                case 1 -> BondOrder.SINGLE;
                case 2 -> BondOrder.DOUBLE;
                case 3 -> BondOrder.TRIPLE;
                case 4 -> BondOrder.AROMATIC;
                default -> throw malformed("Unsupported SDF bond type " + type + ".");
            };
            bonds.add(new ChemicalBond(first, second, order, order == BondOrder.AROMATIC));
        }

        applyProperties(lines, 4 + atomCount + bondCount, atomCount, formalCharges);

        Ligand ligand = ligand(title, atoms, bonds, formalCharges);
        return new SdfLigand(ligand, bonds, formalCharges, title);
    }

    /** Applies {@code M  CHG} lines and locates the end of the record. */
    private void applyProperties(
            List<String> lines, int cursor, int atomCount,
            List<Integer> formalCharges) throws IOException {
        boolean terminated = false;
        while (cursor < lines.size()) {
            String line = lines.get(cursor);
            if (line.startsWith("M  END")) {
                terminated = true;
                cursor++;
                break;
            }
            if (line.startsWith("M  CHG")) {
                String[] tokens = line.trim().split("\\s+");
                int pairs = integer(tokens[2], "M  CHG entry count");
                if (tokens.length != 3 + pairs * 2) {
                    throw malformed("Malformed M  CHG line: " + line);
                }
                for (int pair = 0; pair < pairs; pair++) {
                    int atom = integer(tokens[3 + pair * 2], "M  CHG atom") - 1;
                    int charge = integer(tokens[4 + pair * 2], "M  CHG charge");
                    if (atom < 0 || atom >= atomCount) {
                        throw malformed("M  CHG references an atom outside the atom block: " + line);
                    }
                    formalCharges.set(atom, charge);
                }
            }
            cursor++;
        }
        if (!terminated) {
            throw malformed("SDF record is missing the M  END terminator.");
        }
        for (int index = cursor; index < lines.size(); index++) {
            String line = lines.get(index).trim();
            if (line.isEmpty() || "$$$$".equals(line)) {
                continue;
            }
            throw malformed("Multi-molecule SDF files are not supported; "
                    + "found content after the first record.");
        }
    }

    private Atom atom(String line, int index) throws IOException {
        double x = decimal(field(line, 0, 10), "x coordinate");
        double y = decimal(field(line, 10, 20), "y coordinate");
        double z = decimal(field(line, 20, 30), "z coordinate");
        String symbol = field(line, 31, 34).trim();
        if (symbol.isEmpty()) {
            throw malformed("Atom line is missing an element symbol: " + line);
        }
        return Atom.builder()
                .pdbSerial(index + 1)
                .name(symbol.toUpperCase(Locale.ROOT) + (index + 1))
                .position(new Point3D(x, y, z))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(Element.fromSymbol(symbol))
                .build();
    }

    private Ligand ligand(
            String title, List<Atom> atoms, List<ChemicalBond> bonds,
            List<Integer> formalCharges) {
        List<Bond> structureBonds = new ArrayList<>();
        for (ChemicalBond bond : bonds) {
            structureBonds.add(new Bond(
                    reference(atoms.get(bond.atomIndexA())),
                    reference(atoms.get(bond.atomIndexB())),
                    bond.order()));
        }
        Residue residue = new Residue(residueName(title), RESIDUE_NUMBER, atoms);
        Structure structure = new Structure(
                List.of(new Chain(CHAIN_ID, List.of(residue))), structureBonds);
        int total = formalCharges.stream().mapToInt(Integer::intValue).sum();
        String componentCode = title.matches("[A-Za-z0-9]{1,3}")
                ? title.toUpperCase(Locale.ROOT)
                : null;
        return new Ligand(title, title, componentCode, null, null,
                FormalCharge.of(total), structure);
    }

    private AtomReference reference(Atom atom) {
        return new AtomReference(CHAIN_ID, RESIDUE_NUMBER, ' ', atom.getName());
    }

    private String residueName(String title) {
        String normalized = title.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
        if (normalized.isEmpty()) {
            return "LIG";
        }
        return normalized.substring(0, Math.min(3, normalized.length()));
    }

    private int chargeCode(String field) throws IOException {
        if (field.isBlank()) {
            return 0;
        }
        int code = integer(field, "atom charge code");
        return switch (code) {
            case 0 -> 0;
            case 1 -> 3;
            case 2 -> 2;
            case 3 -> 1;
            case 5 -> -1;
            case 6 -> -2;
            case 7 -> -3;
            case 4 -> throw malformed("Radical atoms (charge code 4) are not supported.");
            default -> throw malformed("Unsupported SDF atom charge code " + code + ".");
        };
    }

    private String field(String line, int start, int end) {
        if (line.length() <= start) {
            return "";
        }
        return line.substring(start, Math.min(end, line.length()));
    }

    private int integer(String value, String description) throws IOException {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw malformed("Cannot parse " + description + " from '" + value + "'.");
        }
    }

    private double decimal(String value, String description) throws IOException {
        try {
            double parsed = Double.parseDouble(value.trim());
            if (!Double.isFinite(parsed)) {
                throw malformed("Non-finite " + description + ": '" + value + "'.");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw malformed("Cannot parse " + description + " from '" + value + "'.");
        }
    }

    private IOException malformed(String message) {
        return new IOException("Invalid SDF input: " + message);
    }
}
