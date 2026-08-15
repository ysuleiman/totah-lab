package totah.lab.prometheus.validation;

import java.util.List;
import java.util.Objects;

/** Checks a set of positioned atoms for steric clashes. */
public interface GeometryClashChecker {

    /**
     * An atom with a position for clash checking. {@code label} is the source
     * label (e.g. "S26"), used to match covalent-bond exclusions.
     */
    record ClashAtom(String label, String elementSymbol, double x, double y, double z) {

        public ClashAtom {
            Objects.requireNonNull(label, "label");
            if (label.isBlank()) {
                throw new IllegalArgumentException("label must be non-blank");
            }
            Objects.requireNonNull(elementSymbol, "elementSymbol");
            if (elementSymbol.isBlank()) {
                throw new IllegalArgumentException("elementSymbol must be non-blank");
            }
        }
    }

    /**
     * Returns human-readable descriptions of the clashes found among
     * {@code atoms}; an empty list means the geometry is clean.
     */
    List<String> clashes(List<ClashAtom> atoms);
}
