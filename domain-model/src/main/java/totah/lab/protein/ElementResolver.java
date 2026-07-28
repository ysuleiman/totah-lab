package totah.lab.protein;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves canonical element symbols from explicit atom metadata or PDB atom
 * names. The monoatomic-residue flag protects protein atom names such as CA
 * from being interpreted as calcium.
 */
public final class ElementResolver {

    private static final Map<String, String> TWO_LETTER_ELEMENTS = Map.ofEntries(
            Map.entry("CL", "Cl"),
            Map.entry("BR", "Br"),
            Map.entry("FE", "Fe"),
            Map.entry("ZN", "Zn"),
            Map.entry("CA", "Ca"),
            Map.entry("MG", "Mg"),
            Map.entry("MN", "Mn"),
            Map.entry("NA", "Na"),
            Map.entry("CU", "Cu"),
            Map.entry("SI", "Si"),
            Map.entry("SE", "Se"),
            Map.entry("CO", "Co"),
            Map.entry("NI", "Ni"),
            Map.entry("RB", "Rb"),
            Map.entry("CS", "Cs")
    );

    private static final Set<String> AMBIGUOUS_PROTEIN_NAMES = Set.of("CA");

    private ElementResolver() {}

    public static String resolveSymbol(Atom atom, Residue residue) {
        boolean monoatomicResidue = residue != null && residue.getAtomCount() == 1;
        return resolveSymbol(atom, monoatomicResidue);
    }

    public static String resolveSymbol(Atom atom, boolean monoatomicResidue) {
        if (atom == null) {
            return "C";
        }
        Element element = atom.getElement();
        if (element != null && !element.isUnknown()) {
            return canonicalize(element.getSymbol());
        }

        String name = atom.getName();
        if (name == null) {
            return "C";
        }

        String stripped = stripLeadingDigits(name.trim());
        if (stripped.isEmpty()) {
            return "C";
        }

        String candidate = stripped.toUpperCase(Locale.ROOT);
        if (candidate.length() >= 2) {
            String two = candidate.substring(0, 2);
            String resolved = TWO_LETTER_ELEMENTS.get(two);
            if (resolved != null && (monoatomicResidue || !AMBIGUOUS_PROTEIN_NAMES.contains(two))) {
                return resolved;
            }
        }

        return canonicalize(String.valueOf(candidate.charAt(0)));
    }

    private static String stripLeadingDigits(String value) {
        int start = 0;
        while (start < value.length() && Character.isDigit(value.charAt(start))) {
            start++;
        }
        return value.substring(start);
    }

    private static String canonicalize(String symbol) {
        String trimmed = symbol.trim();
        if (trimmed.isEmpty()) {
            return "C";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.length() == 1
                ? lower.toUpperCase(Locale.ROOT)
                : lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }
}
