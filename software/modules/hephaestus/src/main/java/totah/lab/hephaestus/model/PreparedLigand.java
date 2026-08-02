package totah.lab.hephaestus.model;

import totah.lab.gaia.molecule.Ligand;
import totah.lab.hephaestus.charge.ChargeAssignment;
import totah.lab.hephaestus.topology.TopologyModel;
import totah.lab.hephaestus.typing.AtomTypeAssignment;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record PreparedLigand(
        Ligand ligand,
        TopologyModel topology,
        ChargeAssignment charges,
        AtomTypeAssignment atomTypes,
        Map<String, Object> attributes) {

    public PreparedLigand {
        Objects.requireNonNull(ligand, "ligand");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static PreparedLigand of(Ligand ligand) {
        return new PreparedLigand(
                ligand,
                null,
                null,
                null,
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

    public PreparedLigand withLigand(Ligand ligand) {
        return new PreparedLigand(
                ligand,
                topology,
                charges,
                atomTypes,
                attributes);
    }

    public PreparedLigand withTopology(TopologyModel topology) {
        return new PreparedLigand(
                ligand,
                Objects.requireNonNull(topology, "topology"),
                charges,
                atomTypes,
                attributes);
    }

    public PreparedLigand withCharges(ChargeAssignment charges) {
        return new PreparedLigand(
                ligand,
                topology,
                Objects.requireNonNull(charges, "charges"),
                atomTypes,
                attributes);
    }

    public PreparedLigand withAtomTypes(AtomTypeAssignment atomTypes) {
        return new PreparedLigand(
                ligand,
                topology,
                charges,
                Objects.requireNonNull(atomTypes, "atomTypes"),
                attributes);
    }

    public PreparedLigand withAttribute(String key, Object value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");

        var updated = new java.util.LinkedHashMap<>(attributes);
        updated.put(key, value);

        return new PreparedLigand(
                ligand,
                topology,
                charges,
                atomTypes,
                updated);
    }
}
