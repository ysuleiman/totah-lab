package totah.lab.protein;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@Builder(toBuilder = true) // Allows clean, immutable rebuilding across pipeline stages
public class Residue {
    @ToString.Include
    private final String name;
    @ToString.Include
    private final int number;
    @ToString.Include
    private final String chain;
    @ToString.Include
    private final Character insertionCode;

    private final ResidueClassificationEvidence residueClassificationEvidence;

    @ToString.Exclude
    @Builder.Default
    private final List<Atom> atoms = new ArrayList<>();

    public Residue(String name, int number, String chain, Character insertionCode, List<Atom> atoms) {
        this(name, number, chain, insertionCode, null, atoms);
    }

    // Custom constructor ensures that standard builders receive an independent, mutable collection copy
    public Residue(String name, int number, String chain, Character insertionCode,
                   ResidueClassificationEvidence residueClassificationEvidence, List<Atom> atoms) {
        this.name = name;
        this.number = number;
        this.chain = chain;
        this.insertionCode = insertionCode;
        this.residueClassificationEvidence = residueClassificationEvidence;
        this.atoms = atoms != null ? new ArrayList<>(atoms) : new ArrayList<>();
    }

    @ToString.Include(name = "atomCount")
    public int getAtomCount() {
        return atoms == null ? 0 : atoms.size();
    }

    public Atom getAtom(String name) {
        if (atoms == null || name == null) return null;
        for (Atom atom : atoms) {
            if (name.equals(atom.getName())) {
                return atom;
            }
        }
        return null;
    }

    public Point3D getAlphaCarbonPosition() {
        if (atoms == null || atoms.isEmpty()) {
            return null;
        }
        for (Atom atom : atoms) {
            if ("CA".equals(atom.getName())) {
                return atom.getPosition();
            }
        }
        return atoms.get(0).getPosition();
    }
}
