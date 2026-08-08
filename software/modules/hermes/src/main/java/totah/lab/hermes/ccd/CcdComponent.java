package totah.lab.hermes.ccd;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Immutable source representation of one CCD chemical component. */
public record CcdComponent(
        String componentId,
        List<CcdComponentAtom> atoms,
        List<CcdComponentBond> bonds) {

    public CcdComponent {
        componentId = requireText(componentId, "componentId");
        atoms = List.copyOf(Objects.requireNonNull(atoms, "atoms"));
        bonds = List.copyOf(Objects.requireNonNull(bonds, "bonds"));
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException("A CCD component must define atoms.");
        }
        Set<String> atomIds = new HashSet<>();
        for (CcdComponentAtom atom : atoms) {
            if (!atomIds.add(normalize(atom.atomId()))) {
                throw new IllegalArgumentException("CCD has a duplicate atom id: " + atom.atomId());
            }
        }
        Set<String> endpoints = new HashSet<>();
        for (CcdComponentBond bond : bonds) {
            String first = normalize(bond.atomIdA());
            String second = normalize(bond.atomIdB());
            if (!atomIds.contains(first) || !atomIds.contains(second)) {
                throw new IllegalArgumentException("CCD bond references an undefined atom.");
            }
            String key = first.compareTo(second) < 0
                    ? first + '\u0000' + second : second + '\u0000' + first;
            if (!endpoints.add(key)) {
                throw new IllegalArgumentException("CCD has duplicate bond endpoints.");
            }
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
