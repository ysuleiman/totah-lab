package totah.lab.hephaestus.ligand.operation;

import org.biojava.nbio.structure.chem.ChemComp;
import org.biojava.nbio.structure.chem.ChemCompProvider;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.hephaestus.ligand.LigandPreparationOperation;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.topology.CcdLigandTopologyBuilder;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.preparation.OperationResult;

import java.util.Objects;

public final class LigandTopologyOperation implements LigandPreparationOperation {

    private final ChemCompProvider chemCompProvider;
    private final CcdLigandTopologyBuilder topologyBuilder;

    public LigandTopologyOperation(ChemCompProvider chemCompProvider) {
        this(chemCompProvider, new CcdLigandTopologyBuilder());
    }

    public LigandTopologyOperation(
            ChemCompProvider chemCompProvider,
            CcdLigandTopologyBuilder topologyBuilder) {
        this.chemCompProvider = Objects.requireNonNull(chemCompProvider, "chemCompProvider");
        this.topologyBuilder = Objects.requireNonNull(topologyBuilder, "topologyBuilder");
    }

    @Override
    public OperationResult<PreparedLigand> apply(
            PreparedLigand preparedLigand,
            LigandPreparationOptions options) {
        Objects.requireNonNull(preparedLigand, "preparedLigand");
        Objects.requireNonNull(options, "options");
        Residue residue = singleResidue(preparedLigand);
        String componentId = preparedLigand.ligand().componentCode()
                .orElse(residue.getName());
        ChemComp chemComp = chemCompProvider.getChemComp(componentId);
        if (chemComp == null || chemComp.getAtoms() == null
                || chemComp.getAtoms().isEmpty()) {
            throw new IllegalArgumentException(
                    "Complete CCD atom definitions are required for " + componentId);
        }
        if (chemComp.getBonds() == null || chemComp.getBonds().isEmpty()) {
            throw new IllegalArgumentException(
                    "Complete CCD bond definitions are required for " + componentId);
        }
        return OperationResult.success(preparedLigand.withTopology(
                topologyBuilder.build(residue, chemComp)));
    }

    private Residue singleResidue(PreparedLigand preparedLigand) {
        var chains = preparedLigand.ligand().structure().getChains();
        if (chains.size() != 1) {
            throw new IllegalArgumentException(
                    "A ligand must contain exactly one chain; found " + chains.size());
        }
        Chain chain = chains.getFirst();
        if (chain.residues().size() != 1) {
            throw new IllegalArgumentException(
                    "A ligand must contain exactly one residue; found " + chain.residues().size());
        }
        return chain.residues().getFirst();
    }
}
