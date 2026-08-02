package totah.lab.hephaestus.ligand.topology;

import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.hephaestus.topology.TopologyModel;

import java.util.List;
import java.util.Objects;

public record LigandTopology(
        String componentId,
        int atomCount,
        List<ChemicalBond> bonds,
        List<LigandAtomProperties> atomProperties,
        List<MissingLigandHydrogen> missingHydrogens,
        List<CcdAtomCoordinates> ccdCoordinates)
        implements TopologyModel {

    public LigandTopology {
        Objects.requireNonNull(componentId, "componentId");
        componentId = componentId.trim();
        if (componentId.isEmpty()) {
            throw new IllegalArgumentException("componentId must not be blank.");
        }
        if (atomCount < 1) {
            throw new IllegalArgumentException("atomCount must be positive.");
        }
        bonds = List.copyOf(bonds);
        atomProperties = List.copyOf(atomProperties);
        missingHydrogens = List.copyOf(missingHydrogens);
        ccdCoordinates = List.copyOf(ccdCoordinates);
        if (atomProperties.size() != atomCount || ccdCoordinates.size() != atomCount) {
            throw new IllegalArgumentException(
                    "Ligand topology atom metadata must match atomCount.");
        }
        for (ChemicalBond bond : bonds) {
            if (bond.atomIndexA() >= atomCount || bond.atomIndexB() >= atomCount) {
                throw new IllegalArgumentException("Bond references an atom outside the topology.");
            }
        }
    }

    @Override
    public String name() {
        return "CCD:" + componentId;
    }
}
