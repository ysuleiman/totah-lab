package totah.lab.gaia.structure;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Chain(
        String id,
        List<Residue> residues) {

    public Chain {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(residues, "residues");

        id = id.trim();

        if (id.isEmpty()) {
            throw new IllegalArgumentException(
                    "Chain id must not be blank.");
        }

        residues = List.copyOf(residues);
    }

    public Optional<Residue> findResidue(int number) {
        return residues.stream()
                .filter(residue -> residue.getNumber() == number)
                .findFirst();
    }

    public Optional<Residue> findResidue(
            int number,
            Character insertionCode) {

        Character normalizedInsertionCode =
                normalizeInsertionCode(insertionCode);

        return residues.stream()
                .filter(residue -> residue.getNumber() == number)
                .filter(residue -> Objects.equals(
                        residue.getInsertionCode(),
                        normalizedInsertionCode))
                .findFirst();
    }

    public Optional<Residue> findResidue(ResidueId residueId) {
        Objects.requireNonNull(residueId, "residueId");
        if (!id.equals(residueId.chainId())) {
            return Optional.empty();
        }
        return findResidue(
                residueId.residueNumber(),
                residueId.insertionCode());
    }

    public boolean containsResidue(int number) {
        return findResidue(number).isPresent();
    }

    public boolean containsResidue(
            int number,
            Character insertionCode) {

        return findResidue(number, insertionCode).isPresent();
    }

    public boolean contains(ResidueId residueId) {
        return findResidue(residueId).isPresent();
    }

    public int residueCount() {
        return residues.size();
    }

    public boolean isEmpty() {
        return residues.isEmpty();
    }

    private static Character normalizeInsertionCode(
            Character insertionCode) {

        if (insertionCode == null
                || Character.isWhitespace(insertionCode)) {
            return null;
        }

        return insertionCode;
    }
}
