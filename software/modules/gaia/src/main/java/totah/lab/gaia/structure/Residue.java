package totah.lab.gaia.structure;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import totah.lab.gaia.classification.ResidueClassificationEvidence;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@Builder(toBuilder = true)
public final class Residue {

    @ToString.Include
    private final String name;

    @ToString.Include
    private final int number;

    @ToString.Include
    private final Character insertionCode;

    @Builder.Default
    private final List<ResidueClassificationEvidence> classificationEvidence =
            List.of();

    @ToString.Exclude
    @Builder.Default
    private final List<Atom> atoms = List.of();

    public Residue(
            String name,
            int number,
            Character insertionCode,
            List<ResidueClassificationEvidence> classificationEvidence,
            List<Atom> atoms) {

        this.name = requireNonBlank(name, "name");
        this.number = number;
        this.insertionCode = normalizeInsertionCode(insertionCode);

        this.classificationEvidence = classificationEvidence == null
                ? List.of()
                : List.copyOf(classificationEvidence);

        this.atoms = atoms == null
                ? List.of()
                : List.copyOf(atoms);
    }

    public Residue(
            String name,
            int number,
            Character insertionCode,
            List<Atom> atoms) {

        this(name, number, insertionCode, List.of(), atoms);
    }

    public Residue(
            String name,
            int number,
            List<Atom> atoms) {

        this(name, number, null, List.of(), atoms);
    }

    @ToString.Include(name = "atomCount")
    public int getAtomCount() {
        return atoms.size();
    }

    public boolean isEmpty() {
        return atoms.isEmpty();
    }

    public boolean hasInsertionCode() {
        return insertionCode != null;
    }

    public Optional<Atom> findAtom(String atomName) {
        if (atomName == null || atomName.isBlank()) {
            return Optional.empty();
        }

        String normalizedName = atomName.trim();

        return atoms.stream()
                .filter(Objects::nonNull)
                .filter(atom -> normalizedName.equals(atom.getName()))
                .findFirst();
    }

    public boolean containsAtom(String atomName) {
        return findAtom(atomName).isPresent();
    }

    public Optional<Point3D> getAlphaCarbonPosition() {
        return findAtom("CA")
                .map(Atom::getPosition);
    }

    public int getHeavyAtomCount() {
        return (int) atoms.stream()
                .filter(Objects::nonNull)
                .filter(atom -> atom.getElement() != null)
                .filter(atom -> !"H".equalsIgnoreCase(
                        atom.getElement().toString()))
                .count();
    }

    private static Character normalizeInsertionCode(
            Character insertionCode) {

        if (insertionCode == null
                || Character.isWhitespace(insertionCode)) {
            return null;
        }

        return insertionCode;
    }

    private static String requireNonBlank(
            String value,
            String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank.");
        }
        return normalized;
    }
}