package totah.lab.athena.ligand.selectivity;

import totah.lab.athena.sequence.AlignedResiduePair;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Renders a {@link LigandContactAlignment} as compact one-letter
 * contact strings in alignment order, with the residue-number mapping
 * underneath and an aligned diff line ({@code |} identical,
 * {@code .} different) over the positions where both sides contact.
 *
 * <p>The strings are a compact view only: the residue numbers beneath
 * them keep every letter anchored to its {@code ResidueId}, so the
 * string never replaces residue identity. Contact rows carry only
 * informative positions, so contact flags for unlisted (identical,
 * contact-free) positions are derived from the stored
 * {@code SequenceAlignment} plus the rows.</p>
 */
public final class ContactStringRenderer {

    /**
     * Renders the contact map:
     * <pre>
     * A: FPYFLVRF
     * A residues: 36 37 38 39 40 41 42 43
     * B: FPYLMAVL
     * B residues: 36 37 38 39 40 41 42 43
     * diff: |||.....
     * </pre>
     * When the two sides contact at different aligned positions the
     * strings have different lengths; the diff line always covers the
     * positions where BOTH sides contact, in alignment order.
     */
    public String render(LigandContactAlignment alignment) {
        Objects.requireNonNull(alignment, "alignment");

        Map<Integer, AlignedLigandContact> rowsByPosition =
                new HashMap<>();
        for (AlignedLigandContact row : alignment.contacts()) {
            rowsByPosition.put(row.alignmentPosition(), row);
        }

        StringBuilder stringA = new StringBuilder();
        StringBuilder stringB = new StringBuilder();
        StringBuilder numbersA = new StringBuilder();
        StringBuilder numbersB = new StringBuilder();
        StringBuilder diff = new StringBuilder();

        List<AlignedResiduePair> pairs =
                alignment.sequenceAlignment().pairs();

        for (int index = 0; index < pairs.size(); index++) {
            AlignedResiduePair pair = pairs.get(index);
            AlignedLigandContact row =
                    rowsByPosition.get(index + 1);

            boolean contactA = row != null && row.contactA();
            boolean contactB = row != null && row.contactB();

            if (contactA) {
                stringA.append(oneLetter(pair.queryResidueName()));
                appendToken(numbersA, pair.queryResidueNumber());
            }

            if (contactB) {
                stringB.append(oneLetter(pair.candidateResidueName()));
                appendToken(numbersB, pair.candidateResidueNumber());
            }

            if (contactA && contactB) {
                diff.append(pair.queryResidueName().equalsIgnoreCase(
                        pair.candidateResidueName()) ? '|' : '.');
            }
        }

        return "A: " + stringA
                + "\nA residues: " + numbersA.toString().trim()
                + "\nB: " + stringB
                + "\nB residues: " + numbersB.toString().trim()
                + "\ndiff: " + diff;
    }

    /**
     * Standard three-letter to one-letter residue-code mapping;
     * {@code X} for anything non-standard.
     */
    public static String oneLetter(String residueName) {
        Objects.requireNonNull(residueName, "residueName");

        return switch (residueName.trim().toUpperCase(Locale.ROOT)) {
            case "ALA" -> "A";
            case "ARG" -> "R";
            case "ASN" -> "N";
            case "ASP" -> "D";
            case "CYS" -> "C";
            case "GLN" -> "Q";
            case "GLU" -> "E";
            case "GLY" -> "G";
            case "HIS" -> "H";
            case "ILE" -> "I";
            case "LEU" -> "L";
            case "LYS" -> "K";
            case "MET" -> "M";
            case "PHE" -> "F";
            case "PRO" -> "P";
            case "SER" -> "S";
            case "THR" -> "T";
            case "TRP" -> "W";
            case "TYR" -> "Y";
            case "VAL" -> "V";
            default -> "X";
        };
    }

    private static void appendToken(
            StringBuilder builder,
            int residueNumber
    ) {
        builder.append(residueNumber).append(' ');
    }
}
