package totah.lab.hermes.file.sdf.writer;

import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.structure.Atom;
import totah.lab.hermes.file.sdf.SdfLigand;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

/** Writes one {@link SdfLigand} as an MDL V2000 SDF record. */
public final class SdfLigandWriter {

    private static final int V2000_MAX_COUNT = 999;
    private static final int CHARGES_PER_LINE = 8;

    public void write(Path path, SdfLigand model) throws IOException {
        Objects.requireNonNull(path, "path");
        try (BufferedWriter writer = Files.newBufferedWriter(
                path.toAbsolutePath().normalize(), StandardCharsets.UTF_8)) {
            write(writer, model);
        }
    }

    /** Writes without closing the caller-owned writer. */
    public void write(Writer writer, SdfLigand model) throws IOException {
        Objects.requireNonNull(writer, "writer");
        Objects.requireNonNull(model, "model");
        List<Atom> atoms = model.ligand().structure().getChains().getFirst()
                .residues().getFirst().getAtoms();
        requireV2000Count("atoms", atoms.size());
        requireV2000Count("bonds", model.bonds().size());

        line(writer, model.title().isBlank() ? "LIG" : model.title());
        line(writer, "  TotahLab Hermes");
        line(writer, "");
        line(writer, String.format(Locale.ROOT,
                "%3d%3d  0  0  0  0            999 V2000",
                atoms.size(), model.bonds().size()));

        for (Atom atom : atoms) {
            writeAtom(writer, atom);
        }
        for (ChemicalBond bond : model.bonds()) {
            line(writer, String.format(Locale.ROOT, "%3d%3d%3d  0  0  0  0",
                    bond.atomIndexA() + 1, bond.atomIndexB() + 1,
                    bondType(bond)));
        }
        writeCharges(writer, model.formalCharges());
        line(writer, "M  END");
        line(writer, "$$$$");
    }

    private void writeAtom(Writer writer, Atom atom) throws IOException {
        if (atom.getElement() == null) {
            throw new IOException("Cannot write an SDF atom without an element.");
        }
        double x = atom.getPosition().x();
        double y = atom.getPosition().y();
        double z = atom.getPosition().z();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IOException("Cannot write non-finite SDF coordinates.");
        }
        String symbol = atom.getElement().symbol();
        if (symbol.isBlank() || symbol.length() > 3) {
            throw new IOException("Invalid SDF element symbol: " + symbol);
        }
        line(writer, String.format(Locale.ROOT,
                "%10.4f%10.4f%10.4f %-3s 0  0  0  0  0  0  0  0  0  0  0  0",
                x, y, z, symbol));
    }

    private int bondType(ChemicalBond bond) throws IOException {
        BondOrder order = bond.aromatic() ? BondOrder.AROMATIC : bond.order();
        return switch (order) {
            case SINGLE -> 1;
            case DOUBLE -> 2;
            case TRIPLE -> 3;
            case AROMATIC -> 4;
            case UNKNOWN -> throw new IOException("Cannot write an unknown SDF bond order.");
        };
    }

    private void writeCharges(Writer writer, List<Integer> charges) throws IOException {
        List<Integer> chargedAtoms = new ArrayList<>();
        for (int index = 0; index < charges.size(); index++) {
            if (charges.get(index) != 0) {
                chargedAtoms.add(index);
            }
        }
        for (int start = 0; start < chargedAtoms.size(); start += CHARGES_PER_LINE) {
            int count = Math.min(CHARGES_PER_LINE, chargedAtoms.size() - start);
            StringBuilder line = new StringBuilder(String.format(
                    Locale.ROOT, "M  CHG%3d", count));
            for (int offset = 0; offset < count; offset++) {
                int atomIndex = chargedAtoms.get(start + offset);
                line.append(String.format(Locale.ROOT, "%4d%4d",
                        atomIndex + 1, charges.get(atomIndex)));
            }
            line(writer, line.toString());
        }
    }

    private void requireV2000Count(String name, int count) throws IOException {
        if (count < 1 || count > V2000_MAX_COUNT) {
            throw new IOException("V2000 " + name + " count must be between 1 and 999.");
        }
    }

    private void line(Writer writer, String value) throws IOException {
        writer.write(value);
        writer.write('\n');
    }
}
