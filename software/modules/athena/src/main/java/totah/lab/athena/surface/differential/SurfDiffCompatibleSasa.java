package totah.lab.athena.surface.differential;

import totah.lab.athena.sasa.ShrakeRupleySasa;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Biopython/SurfDiff-compatible 100-point SASA plus frozen rSASA references. */
public final class SurfDiffCompatibleSasa {
    public static final int SPHERE_POINT_COUNT = 100;

    private static final Map<String, Double> MID_REFERENCE = Map.ofEntries(
            Map.entry("CYS", 161.3921), Map.entry("ASP", 168.9211),
            Map.entry("SER", 144.8628), Map.entry("ASN", 183.3684),
            Map.entry("GLN", 216.1981), Map.entry("LYS", 255.8972),
            Map.entry("ILE", 210.7685), Map.entry("PRO", 173.0591),
            Map.entry("THR", 171.9532), Map.entry("PHE", 239.5635),
            Map.entry("ALA", 137.121), Map.entry("GLY", 105.9325),
            Map.entry("HIS", 221.906), Map.entry("LEU", 211.3939),
            Map.entry("ARG", 284.8122), Map.entry("TRP", 286.4021),
            Map.entry("VAL", 186.5904), Map.entry("GLU", 198.3556),
            Map.entry("TYR", 260.2168), Map.entry("MET", 227.7568));

    private static final Map<String, Double> TERMINAL_REFERENCE = Map.ofEntries(
            Map.entry("ALA", 192.31515), Map.entry("ARG", 342.05025),
            Map.entry("ASN", 241.27435), Map.entry("ASP", 225.04725),
            Map.entry("CYS", 218.13945), Map.entry("GLN", 273.7405),
            Map.entry("GLU", 255.54086), Map.entry("GLY", 162.14031),
            Map.entry("HIS", 276.853), Map.entry("ILE", 266.3236),
            Map.entry("LEU", 269.18755), Map.entry("LYS", 309.49775),
            Map.entry("MET", 283.6092), Map.entry("PHE", 297.78765),
            Map.entry("PRO", 228.9176), Map.entry("SER", 203.5328),
            Map.entry("THR", 230.9984), Map.entry("TRP", 342.86015),
            Map.entry("TYR", 316.31645), Map.entry("VAL", 240.94645));

    private SurfDiffCompatibleSasa() {
    }

    public static List<SurfaceResidue> calculate(
            Structure structure,
            DifferentialSurfaceOptions options) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(options, "options");
        ShrakeRupleySasa.SasaResult sasa = ShrakeRupleySasa.calculate(
                structure, options.probeRadiusAngstroms(), SPHERE_POINT_COUNT);
        List<SurfaceResidue> result = new ArrayList<>();
        for (Chain chain : structure.getChains()) {
            List<Residue> residues = chain.residues();
            for (int index = 0; index < residues.size(); index++) {
                Residue residue = residues.get(index);
                ResidueId id = new ResidueId(chain.id(), residue.getNumber(),
                        residue.getInsertionCode());
                double absolute = residueArea(sasa, chain.id(), residue);
                if (absolute < options.absoluteSasaThreshold()) {
                    absolute = 0.0;
                }
                boolean terminal = index == 0 || index == residues.size() - 1;
                double relative = relativeSasa(residue.getName(), terminal, absolute);
                result.add(new SurfaceResidue(id, residue, absolute, relative));
            }
        }
        return List.copyOf(result);
    }

    public static double relativeSasa(
            String residueName,
            boolean terminal,
            double absoluteSasa) {
        if (!Double.isFinite(absoluteSasa) || absoluteSasa < 0.0) {
            throw new IllegalArgumentException("absoluteSasa must be non-negative");
        }
        String normalized = Objects.requireNonNull(residueName, "residueName")
                .trim().toUpperCase();
        Double reference = (terminal ? TERMINAL_REFERENCE : MID_REFERENCE)
                .get(normalized);
        return reference == null ? 0.0 : absoluteSasa / reference;
    }

    private static double residueArea(
            ShrakeRupleySasa.SasaResult sasa,
            String chainId,
            Residue residue) {
        double total = 0.0;
        char insertionCode = residue.getInsertionCode() == null
                ? ' ' : residue.getInsertionCode();
        for (Atom atom : residue.getAtoms()) {
            if (atom == null || atom.getElement() == null) {
                continue;
            }
            AtomReference reference = new AtomReference(
                    chainId, residue.getNumber(), insertionCode, atom.getName());
            Double area = sasa.areaByAtom().get(reference);
            if (area != null) {
                total += area;
            }
        }
        return total;
    }
}
