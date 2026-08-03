package totah.lab.proteus.protein.mutation.rotamer;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.proteus.protein.mutation.geometry.SideChainBuilder;
import totah.lab.proteus.protein.mutation.geometry.SideChainTemplate;

import java.util.List;
import java.util.Objects;

/**
 * Selects a rotamer for a rebuilt side chain by least steric clash.
 * Deterministic: candidates are evaluated in library order and the first
 * candidate with the strictly lowest score wins (ties keep the earlier
 * candidate).
 */
public final class RotamerSelector {
    private final SideChainBuilder builder;
    private final RotamerEvaluator evaluator;

    public RotamerSelector() {
        this(new SideChainBuilder(), new RotamerEvaluator());
    }

    public RotamerSelector(SideChainBuilder builder, RotamerEvaluator evaluator) {
        this.builder = Objects.requireNonNull(builder, "builder");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    public Selection select(Structure structure, ResidueId target, Residue source,
                            SideChainTemplate template, List<Rotamer> rotamers) {
        if (rotamers == null || rotamers.isEmpty()) {
            throw new IllegalArgumentException("rotamers must not be empty");
        }
        // New atoms must not collide with serials anywhere in the structure,
        // so number them above the structure-wide maximum.
        int firstSerial = maxSerial(structure) + 1;
        Selection best = null;
        for (Rotamer rotamer : rotamers) {
            List<Atom> atoms = renumber(builder.build(source, template, rotamer), firstSerial);
            Selection candidate = new Selection(rotamer, atoms,
                    evaluator.score(structure, target, atoms));
            if (best == null || candidate.score() < best.score()) best = candidate;
        }
        return best;
    }

    private static int maxSerial(Structure structure) {
        int max = 0;
        for (var chain : structure.getChains()) {
            for (var residue : chain.residues()) {
                for (Atom atom : residue.getAtoms()) {
                    max = Math.max(max, atom.getPdbSerial());
                }
            }
        }
        return max;
    }

    private static List<Atom> renumber(List<Atom> atoms, int firstSerial) {
        var result = new java.util.ArrayList<Atom>(atoms.size());
        int serial = firstSerial;
        for (Atom atom : atoms) {
            result.add(atom.toBuilder().pdbSerial(serial++).build());
        }
        return List.copyOf(result);
    }

    public record Selection(Rotamer rotamer, List<Atom> atoms, double score) {
        public Selection { atoms = List.copyOf(atoms); }
    }
}
