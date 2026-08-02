package totah.lab.hephaestus.typing;

import java.util.List;
import java.util.Objects;

public final class AtomTypeAssignment {
    private final String name;
    private final List<AssignedAtomType> atomTypes;

    public AtomTypeAssignment(String name, List<AssignedAtomType> atomTypes) {
        this.name = Objects.requireNonNull(name, "name");
        this.atomTypes = List.copyOf(atomTypes);
    }

    public String name() { return name; }
    public List<AssignedAtomType> atomTypes() { return atomTypes; }
    public int atomCount() { return atomTypes.size(); }
}
