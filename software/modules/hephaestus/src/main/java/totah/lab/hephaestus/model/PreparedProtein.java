package totah.lab.hephaestus.model;

import totah.lab.gaia.molecule.Protein;
import totah.lab.hephaestus.charge.ChargeAssignment;
import totah.lab.hephaestus.flexibility.FlexibilityModel;
import totah.lab.hephaestus.topology.TopologyModel;
import totah.lab.hephaestus.typing.AtomTypeAssignment;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record PreparedProtein(
        Protein protein,
        TopologyModel topology,
        ChargeAssignment charges,
        AtomTypeAssignment atomTypes,
        FlexibilityModel flexibility,
        Map<String, Object> attributes) {

    public static final String STRUCTURE_CHANGE_ATTRIBUTE = "structure-change";

    public PreparedProtein {
        Objects.requireNonNull(protein, "protein");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static PreparedProtein of(Protein protein) {
        return new PreparedProtein(
                protein,
                null,
                null,
                null,
                FlexibilityModel.empty(),
                Map.of());
    }

    public Optional<TopologyModel> topologyOptional() {
        return Optional.ofNullable(topology);
    }

    public Optional<ChargeAssignment> chargesOptional() {
        return Optional.ofNullable(charges);
    }

    public Optional<AtomTypeAssignment> atomTypesOptional() {
        return Optional.ofNullable(atomTypes);
    }

    public PreparedProtein withFlexibility(FlexibilityModel flexibility) {
        return new PreparedProtein(protein, topology, charges, atomTypes,
                Objects.requireNonNull(flexibility, "flexibility"), attributes);
    }

    public PreparedProtein withProtein(Protein protein) {
        return new PreparedProtein(
                protein,
                topology,
                charges,
                atomTypes,
                flexibility,
                attributes);
    }

    /**
     * Replaces molecular structure while discarding every preparation result
     * derived from the prior atom set, coordinates, residue identities, or bonds.
     */
    public PreparedProtein withChangedStructure(
            Protein changedProtein,
            StructureChange change) {
        Objects.requireNonNull(changedProtein, "changedProtein");
        Objects.requireNonNull(change, "change");
        return new PreparedProtein(
                changedProtein,
                null,
                null,
                null,
                FlexibilityModel.empty(),
                Map.of(STRUCTURE_CHANGE_ATTRIBUTE, change));
    }

    public PreparedProtein withTopology(TopologyModel topology) {
        return new PreparedProtein(
                protein,
                Objects.requireNonNull(topology, "topology"),
                charges,
                atomTypes,
                flexibility,
                attributes);
    }

    public PreparedProtein withCharges(ChargeAssignment charges) {
        return new PreparedProtein(
                protein,
                topology,
                Objects.requireNonNull(charges, "charges"),
                atomTypes,
                flexibility,
                attributes);
    }

    public PreparedProtein withAtomTypes(AtomTypeAssignment atomTypes) {
        return new PreparedProtein(
                protein,
                topology,
                charges,
                Objects.requireNonNull(atomTypes, "atomTypes"),
                flexibility,
                attributes);
    }

    public PreparedProtein withAttribute(String key, Object value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");

        var updated = new java.util.LinkedHashMap<>(attributes);
        updated.put(key, value);

        return new PreparedProtein(
                protein,
                topology,
                charges,
                atomTypes,
                flexibility,
                updated);
    }
}
