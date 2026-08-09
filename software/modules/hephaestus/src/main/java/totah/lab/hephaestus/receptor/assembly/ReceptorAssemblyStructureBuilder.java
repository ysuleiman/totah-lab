package totah.lab.hephaestus.receptor.assembly;

import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityMetadata;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Builds a combined structure view without reconstructing component atoms. */
public final class ReceptorAssemblyStructureBuilder {

    public Structure build(ReceptorAssembly assembly) {
        Objects.requireNonNull(assembly, "assembly");

        Structure protein = assembly.protein().protein().structure();
        List<Structure> components = new ArrayList<>();
        components.add(protein);
        for (FixedCofactor cofactor : assembly.fixedCofactors()) {
            components.add(cofactor.pose().preparedPose()
                    .ligand().structure());
        }

        List<Chain> chains = new ArrayList<>();
        List<Bond> bonds = new ArrayList<>();
        Set<String> chainIds = new HashSet<>();
        for (Structure component : components) {
            for (Chain chain : component.getChains()) {
                if (!chainIds.add(chain.id())) {
                    throw new IllegalArgumentException(
                            "Receptor assembly contains duplicate chain id: "
                                    + chain.id());
                }
                chains.add(chain);
            }
            bonds.addAll(component.bonds());
        }

        return new Structure(
                chains,
                bonds,
                combinedConnectivity(components, bonds));
    }

    private static ConnectivityMetadata combinedConnectivity(
            List<Structure> components,
            List<Bond> bonds) {

        List<ConnectivityMetadata> metadata = components.stream()
                .map(Structure::getConnectivityMetadata)
                .toList();
        List<String> diagnostics = metadata.stream()
                .flatMap(value -> value.diagnostics().stream())
                .toList();

        if (bonds.isEmpty() && metadata.stream().allMatch(value ->
                value.provenance() == ConnectivityProvenance.ABSENT)) {
            return new ConnectivityMetadata(
                    ConnectivityProvenance.ABSENT,
                    diagnostics);
        }
        if (metadata.stream().allMatch(value ->
                value.provenance() == ConnectivityProvenance.EXPLICIT)) {
            return new ConnectivityMetadata(
                    ConnectivityProvenance.EXPLICIT,
                    diagnostics);
        }
        List<String> combinedDiagnostics = new ArrayList<>(diagnostics);
        combinedDiagnostics.add(
                "Assembly combines components with different connectivity "
                        + "provenance; no inter-component covalent bonds "
                        + "were inferred.");
        return new ConnectivityMetadata(
                ConnectivityProvenance.PARTIAL,
                combinedDiagnostics);
    }
}
