package totah.lab.hephaestus.validation.internal;

import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.flexibility.AtomReference;
import totah.lab.hephaestus.validation.ValidationCode;
import totah.lab.hephaestus.validation.ValidationIssue;
import totah.lab.hephaestus.validation.ValidationSeverity;

import java.util.ArrayList;
import java.util.List;

/** Internal shared canonical index implementation; indices are never repaired by searching. */
public final class CanonicalAtomResolver {
    private final List<CanonicalAtomRecord> atoms;

    public CanonicalAtomResolver(Structure structure) {
        List<CanonicalAtomRecord> records = new ArrayList<>();
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                ResidueId residueReference = new ResidueId(
                        chain.id(), residue.getNumber(), residue.getInsertionCode());
                for (var atom : residue.getAtoms()) {
                    int index = records.size();
                    AtomReference reference = new AtomReference(residueReference, atom.getName(), index);
                    records.add(new CanonicalAtomRecord(index, reference, residue.getName(), residue, atom));
                }
            }
        }
        atoms = List.copyOf(records);
    }

    public List<CanonicalAtomRecord> atoms() { return atoms; }

    public Resolution resolve(AtomReference reference) {
        String location = reference == null ? "atom-reference" : reference.toString();
        if (reference == null || reference.atomIndex() < 0 || reference.atomIndex() >= atoms.size()) {
            return new Resolution(null, new ValidationIssue(ValidationSeverity.ERROR,
                    ValidationCode.STALE_ATOM_INDEX, "Canonical atom index is stale or out of range.", location));
        }
        CanonicalAtomRecord resolved = atoms.get(reference.atomIndex());
        if (!resolved.reference().equals(reference)) {
            return new Resolution(null, new ValidationIssue(ValidationSeverity.ERROR,
                    ValidationCode.ATOM_REFERENCE_MISMATCH,
                    "Canonical index does not identify the supplied chain, residue, insertion code, and atom name.", location));
        }
        return new Resolution(resolved, null);
    }

    public record Resolution(CanonicalAtomRecord atom, ValidationIssue issue) {
        public boolean resolved() { return atom != null; }
    }
}
