package totah.lab.gaia.molecule;


import totah.lab.gaia.chemistry.FormalCharge;
import totah.lab.gaia.structure.Structure;

import java.util.Objects;
import java.util.Optional;

public final class Ligand implements Molecule {

    private final String id;
    private final String name;
    private final String componentCode;
    private final String smiles;
    private final String inchiKey;
    private final FormalCharge formalCharge;
    private final Structure structure;

    public Ligand(
            String id,
            String name,
            String componentCode,
            String smiles,
            String inchiKey,
            FormalCharge formalCharge,
            Structure structure) {

        this.id = requireNonBlank(id, "id");
        this.name = requireNonBlank(name, "name");
        this.componentCode = normalizeNullable(componentCode);
        this.smiles = normalizeNullable(smiles);
        this.inchiKey = normalizeNullable(inchiKey);
        this.formalCharge = Objects.requireNonNullElse(
                formalCharge,
                FormalCharge.NEUTRAL);
        this.structure = Objects.requireNonNull(
                structure,
                "structure");
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Structure structure() {
        return structure;
    }

    public FormalCharge formalCharge() {
        return formalCharge;
    }

    public Optional<String> componentCode() {
        return Optional.ofNullable(componentCode);
    }

    public Optional<String> smiles() {
        return Optional.ofNullable(smiles);
    }

    public Optional<String> inchiKey() {
        return Optional.ofNullable(inchiKey);
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

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }
}
