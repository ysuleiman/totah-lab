package totah.lab.gaia.structure;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An ordered collection of residues with unique canonical identities.
 * Residue order is preserved exactly as supplied.
 */
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
        validateUniqueResidueIdentities(residues);
    }

    /**
     * Finds a residue by sequence number, ignoring insertion codes.
     * The plain residue (no insertion code) is returned when it exists;
     * otherwise the single match is returned.
     *
     * @throws IllegalStateException if the number is ambiguous, i.e. only
     *         multiple insertion-code siblings (e.g. 10A and 10B) match;
     *         use {@link #findResidue(int, Character)} in that case.
     */
    public Optional<Residue> findResidue(int number) {
        List<Residue> matches = residues.stream()
                .filter(residue -> residue.getNumber() == number)
                .toList();

        Optional<Residue> plain = matches.stream()
                .filter(residue -> residue.getInsertionCode() == null)
                .findFirst();
        if (plain.isPresent()) {
            return plain;
        }

        if (matches.size() > 1) {
            throw new IllegalStateException(
                    "Residue number " + number + " in chain " + id
                            + " is ambiguous: multiple insertion-code "
                            + "siblings; use findResidue(int, Character).");
        }

        return matches.stream().findFirst();
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

    private static void validateUniqueResidueIdentities(
            List<Residue> residues) {

        Set<ResiduePosition> identities = new HashSet<>();
        for (Residue residue : residues) {
            ResiduePosition identity = new ResiduePosition(
                    residue.getNumber(),
                    normalizeInsertionCode(residue.getInsertionCode()));
            if (!identities.add(identity)) {
                throw new IllegalArgumentException(
                        "Duplicate residue identity: "
                                + identity.number()
                                + formatInsertionCode(
                                        identity.insertionCode()));
            }
        }
    }

    private static String formatInsertionCode(
            Character insertionCode) {

        return insertionCode == null
                ? ""
                : Character.toString(insertionCode);
    }

    private record ResiduePosition(
            int number,
            Character insertionCode) {
    }
}
