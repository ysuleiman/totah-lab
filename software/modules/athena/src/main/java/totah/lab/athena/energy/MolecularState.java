package totah.lab.athena.energy;

import totah.lab.gaia.structure.Structure;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Complete declared state evaluated under one Hamiltonian. */
public record MolecularState(
        String stateId,
        String receptorId,
        String ligandId,
        Structure receptor,
        Structure ligand,
        Optional<Structure> cofactor,
        String protonationProvenance,
        String forceFieldIdentity,
        String parameterProvenance,
        String environmentModel,
        String restraintDefinition,
        Map<String, String> provenance) {

    public MolecularState {
        stateId = requireText(stateId, "stateId");
        receptorId = requireText(receptorId, "receptorId");
        ligandId = requireText(ligandId, "ligandId");
        Objects.requireNonNull(receptor, "receptor");
        Objects.requireNonNull(ligand, "ligand");
        cofactor = Objects.requireNonNull(cofactor, "cofactor");
        protonationProvenance = requireText(protonationProvenance,
                "protonationProvenance");
        forceFieldIdentity = requireText(forceFieldIdentity, "forceFieldIdentity");
        parameterProvenance = requireText(parameterProvenance,
                "parameterProvenance");
        environmentModel = requireText(environmentModel, "environmentModel");
        restraintDefinition = requireText(restraintDefinition,
                "restraintDefinition");
        provenance = Map.copyOf(Objects.requireNonNull(provenance, "provenance"));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
