package totah.lab.prometheus.candidate;

import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.identity.MoleculeIdentity;

/**
 * A single derived force-field parameter bound to a molecule and to a tuple of
 * canonical atom indices.
 *
 * <p>{@code canonicalAtomIndices} are {@code CanonicalAtomMap} serials — NOT
 * artifact file positions and NOT zero-based indices. In TSL, for example, the
 * atom labeled "C9" is canonical serial 10 and "C10" is serial 11; any code that
 * parses a serial out of a label will swap them.
 *
 * <p>{@code functionalForm} names the mathematical form, e.g. {@code "harmonic"},
 * {@code "periodic-fourier"}, {@code "lennard-jones-12-6"}.
 */
public record DerivedParameter(
        String parameterId,
        MoleculeIdentity molecule,
        List<Integer> canonicalAtomIndices,
        ParameterKind kind,
        String functionalForm,
        double value,
        String unit,
        ParameterProvenance provenance) {

    public DerivedParameter {
        requireNonBlank(parameterId, "parameterId");
        Objects.requireNonNull(molecule, "molecule");
        canonicalAtomIndices = List.copyOf(
                Objects.requireNonNull(canonicalAtomIndices, "canonicalAtomIndices"));
        if (canonicalAtomIndices.isEmpty()) {
            throw new IllegalArgumentException("canonicalAtomIndices must not be empty");
        }
        Objects.requireNonNull(kind, "kind");
        requireNonBlank(functionalForm, "functionalForm");
        requireNonBlank(unit, "unit");
        Objects.requireNonNull(provenance, "provenance");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
