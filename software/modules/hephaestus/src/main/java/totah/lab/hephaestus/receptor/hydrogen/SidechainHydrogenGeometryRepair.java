package totah.lab.hephaestus.receptor.hydrogen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Residue;
import totah.lab.hephaestus.receptor.protonation.ProtonationConfig;

/**
 * Opt-in repair of invalid non-aromatic sidechain H in explicitly bonded neutral
 * PHE/TYR/LEU/SER residues. Does not assign topology, charges or protonation.
 * Heavy atoms, aromatic H, passing H and source order are preserved.
 * The caller must additionally validate caps and the complete surrounding geometry.
 */
public final class SidechainHydrogenGeometryRepair {
    public static final String PROTOCOL = "sidechain-H-geometry-v2;CH1.09+-0.02;tetrahedral100-120;OH0.90-1.10;COH95-120;existing-residue-hydrogenator";
    private static final Set<String> BACKBONE_H = Set.of("H", "H1", "H2", "H3", "HA", "HA2", "HA3");

    private SidechainHydrogenGeometryRepair() {}

    public record Angle(String neighbor, double degrees) {}
    public record Check(String hydrogen, String parent, double lengthAngstrom,
                        List<Angle> angles, boolean valid) {
        public Check { angles = List.copyOf(angles); }
    }
    public record Result(Residue residue, List<Check> before, List<Check> after,
                         Set<String> reconstructedHydrogens) {
        public Result {
            before = List.copyOf(before);
            after = List.copyOf(after);
            reconstructedHydrogens = Set.copyOf(reconstructedHydrogens);
        }
    }

    public static Result repair(String chainId, Residue residue, List<Bond> bonds) {
        if (!Set.of("PHE", "TYR", "LEU", "SER").contains(residue.getName())) {
            throw new IllegalArgumentException("Unsupported neutral residue template");
        }
        var original = names(residue.getAtoms());
        var adjacency = adjacency(chainId, residue, bonds, original);
        var before = audit(original, adjacency);
        var invalid = new TreeSet<String>();
        before.stream().filter(c -> !c.valid()).forEach(c -> invalid.add(c.hydrogen()));
        // Coupled methylene/methyl placement must not move any otherwise passing H.
        var requested = new TreeSet<>(invalid);
        for (String h : invalid) {
            String parent = adjacency.get(h).getFirst();
            for (String neighbor : adjacency.get(parent)) {
                if (original.get(neighbor).isHydrogen() && !invalid.contains(neighbor)) {
                    throw new IllegalArgumentException("Repair would move a passing coupled H: " + neighbor);
                }
            }
        }
        if (requested.isEmpty()) return new Result(residue, before, before, Set.of());
        var kept = new ArrayList<>(residue.getAtoms().stream()
                .filter(a -> !requested.contains(a.getName())).toList());
        var clash = new SpatialClashChecker(.5);
        clash.addAll(kept);
        var partial = new Residue(residue.getName(), residue.getNumber(), residue.getInsertionCode(),
                residue.getClassificationEvidence(), kept);
        var context = new HydrogenationContext(ProtonationConfig.defaults(), chainId,
                List.of(partial), clash, new HydrogenPositionCalculator(), new HydrogenAtomFactory(),
                Set.of(), List.of(), Map.of(chainId + ":" + residue.getNumber(), residue.getName()));
        ResidueHydrogenator.hydrogenateSideChain(chainId, partial, kept, context);
        var generated = names(kept.stream().filter(a -> requested.contains(a.getName())).toList());
        var repaired = new ArrayList<Atom>();
        for (var atom : residue.getAtoms()) {
            if (requested.contains(atom.getName())) {
                var replacement = generated.get(atom.getName());
                if (replacement == null) throw new IllegalArgumentException("Missing generated H: " + atom.getName());
                repaired.add(atom.toBuilder().position(replacement.getPosition()).build());
            } else repaired.add(atom);
        }
        var after = audit(names(repaired), adjacency);
        if (after.stream().anyMatch(c -> !c.valid())) {
            throw new IllegalArgumentException("Non-aromatic sidechain H repair failed geometry validation");
        }
        // Preserve residue metadata and immutable atom order.
        var result = new Residue(residue.getName(), residue.getNumber(), residue.getInsertionCode(),
                residue.getClassificationEvidence(), repaired);
        return new Result(result, before, after, requested);
    }

    private static Map<String, Atom> names(List<Atom> atoms) {
        var result = new LinkedHashMap<String, Atom>();
        for (var atom : atoms) {
            if (result.put(atom.getName(), atom) != null) throw new IllegalArgumentException("Duplicate atom name");
        }
        return result;
    }

    private static Map<String, List<String>> adjacency(String chainId, Residue residue,
                                                       List<Bond> bonds, Map<String, Atom> atoms) {
        var result = new HashMap<String, List<String>>();
        atoms.keySet().forEach(n -> result.put(n, new ArrayList<>()));
        var unique = new java.util.HashSet<Set<String>>();
        char insertion = residue.getInsertionCode() == null ? ' ' : residue.getInsertionCode();
        for (var bond : bonds) {
            var a = bond.atom1(); var b = bond.atom2();
            if (!a.chainId().equals(chainId) || !b.chainId().equals(chainId)
                    || a.residueNumber() != residue.getNumber() || b.residueNumber() != residue.getNumber()
                    || a.insertionCode() != insertion || b.insertionCode() != insertion) continue;
            if (!atoms.containsKey(a.atomName()) || !atoms.containsKey(b.atomName())) {
                throw new IllegalArgumentException("Unknown topology endpoint");
            }
            if (a.equals(b) || !unique.add(Set.of(a.atomName(), b.atomName()))) {
                throw new IllegalArgumentException("Duplicate/self topology bond");
            }
            if ((atoms.get(a.atomName()).isHydrogen() || atoms.get(b.atomName()).isHydrogen())
                    && bond.order() != BondOrder.SINGLE) {
                throw new IllegalArgumentException("Non-single hydrogen bond");
            }
            result.get(a.atomName()).add(b.atomName());
            result.get(b.atomName()).add(a.atomName());
        }
        return result;
    }

    private static List<Check> audit(Map<String, Atom> atoms, Map<String, List<String>> adjacency) {
        var checks = new ArrayList<Check>();
        for (var atom : atoms.values()) {
            String h = atom.getName();
            if (!atom.isHydrogen() || BACKBONE_H.contains(h)) continue;
            if (adjacency.get(h).size() != 1) throw new IllegalArgumentException("Invalid explicit H valence: " + h);
            String parent = adjacency.get(h).getFirst();
            // These named ring sites are unchanged; the caller performs the separate aromatic audit.
            if (Set.of("CG", "CD1", "CD2", "CE1", "CE2", "CZ").contains(parent)
                    && Set.of("HD1", "HD2", "HE1", "HE2", "HZ").contains(h)) continue;
            var center = atoms.get(parent);
            int z = center.getElement().getAtomicNumber();
            if (z != 6 && z != 8) throw new IllegalArgumentException("Unsupported H parent");
            if (adjacency.get(parent).size() != (z == 6 ? 4 : 2)) {
                throw new IllegalArgumentException("Incomplete explicit parent topology: " + parent);
            }
            double length = center.getPosition().distance(atom.getPosition());
            var angles = new ArrayList<Angle>();
            for (String n : adjacency.get(parent)) if (!n.equals(h)) {
                angles.add(new Angle(n, angle(center.getPosition(), atom.getPosition(), atoms.get(n).getPosition())));
            }
            boolean valid = Double.isFinite(length) && (z == 6 ? Math.abs(length - 1.09) <= .02 : length >= .90 && length <= 1.10)
                    && angles.stream().allMatch(a -> Double.isFinite(a.degrees())
                    && a.degrees() >= (z == 6 ? 100 : 95) && a.degrees() <= 120);
            checks.add(new Check(h, parent, length, angles, valid));
        }
        return List.copyOf(checks);
    }

    private static double angle(Point3D c, Point3D a, Point3D b) {
        var x = c.vectorTo(a); var y = c.vectorTo(b);
        double product = x.magnitude() * y.magnitude();
        if (product < 1e-12) return Double.NaN;
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, x.dot(y) / product))));
    }
}
