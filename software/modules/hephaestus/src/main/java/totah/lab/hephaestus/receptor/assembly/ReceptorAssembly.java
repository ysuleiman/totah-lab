package totah.lab.hephaestus.receptor.assembly;

import totah.lab.hephaestus.model.PreparedProtein;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Prepared protein plus fixed cofactors, retained in caller-supplied order. */
public record ReceptorAssembly(
        PreparedProtein protein,
        List<FixedCofactor> fixedCofactors) {

    public ReceptorAssembly {
        Objects.requireNonNull(protein, "protein");
        fixedCofactors = fixedCofactors == null
                ? List.of()
                : List.copyOf(fixedCofactors);
        requireUniqueCofactorIds(fixedCofactors);
    }

    public static ReceptorAssembly of(PreparedProtein protein) {
        return new ReceptorAssembly(protein, List.of());
    }

    public ReceptorAssembly withFixedCofactor(FixedCofactor cofactor) {
        Objects.requireNonNull(cofactor, "cofactor");
        List<FixedCofactor> updated = new java.util.ArrayList<>(
                fixedCofactors);
        updated.add(cofactor);
        return new ReceptorAssembly(protein, updated);
    }

    private static void requireUniqueCofactorIds(
            List<FixedCofactor> cofactors) {

        Set<String> identifiers = new HashSet<>();
        for (FixedCofactor cofactor : cofactors) {
            Objects.requireNonNull(
                    cofactor,
                    "fixedCofactors must not contain null elements");
            if (!identifiers.add(cofactor.id())) {
                throw new IllegalArgumentException(
                        "Duplicate fixed cofactor id: " + cofactor.id());
            }
        }
    }
}
