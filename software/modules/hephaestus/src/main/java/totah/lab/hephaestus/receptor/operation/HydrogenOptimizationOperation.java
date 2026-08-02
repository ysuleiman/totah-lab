package totah.lab.hephaestus.receptor.operation;

import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.amber.AmberParameterSet;
import totah.lab.hephaestus.amber.AmberResidueTemplateLibrary;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.preparation.OperationResult;
import totah.lab.hephaestus.receptor.ReceptorPreparationOperation;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.hydrogen.HydrogenOptimizer;
import totah.lab.hephaestus.receptor.hydrogenation.HydrogenOptimizationReport;
import totah.lab.hephaestus.receptor.residue.ResidueState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HydrogenOptimizationOperation
        implements ReceptorPreparationOperation {

    public static final String HYDROGEN_OPTIMIZATION_REPORT_ATTRIBUTE =
            "hydrogen-optimization-report";

    private static final double POSITION_TOLERANCE = 1.0e-6;

    @Override
    public OperationResult<PreparedProtein> apply(
            PreparedProtein preparedProtein,
            ReceptorPreparationOptions options) {

        Objects.requireNonNull(preparedProtein, "preparedProtein");
        Objects.requireNonNull(options, "options");

        if (!options.optimizeHydrogens()) {
            return OperationResult.success(preparedProtein);
        }

        requireHydrogenation(preparedProtein);
        Map<String, ResidueState> states = residueStates(preparedProtein);
        Map<String, String> templates = amberTemplates(states);
        Structure incoming = preparedProtein.protein().structure();

        if (incoming.getResidueCount() == 0) {
            throw new IllegalStateException(
                    "Protein structure contains no residues. "
                            + "Run ReceptorHydrogenationOperation first.");
        }

        HydrogenOptimizer optimizer = new HydrogenOptimizer(
                AmberResidueTemplateLibrary.getInstance(),
                loadParameters(options.protonationConfig().amberParameterPath()),
                options.protonationConfig().clashCutoff());

        List<Chain> optimizedChains = new ArrayList<>(incoming.getChainCount());
        List<String> optimizedLabels = new ArrayList<>();
        int movedHydrogens = 0;

        for (Chain chain : incoming.getChains()) {
            List<Residue> optimizedResidues =
                    new ArrayList<>(chain.residueCount());

            for (Residue residue : chain.residues()) {
                String key = residueKey(chain.id(), residue);
                String amberTemplate = templates.get(key);

                if (amberTemplate == null) {
                    throw new IllegalStateException(
                            "Missing residue state for "
                                    + residueLabel(chain.id(), residue));
                }

                List<Atom> optimizedAtoms = optimizer.optimize(
                        chain.id(),
                        residue,
                        incoming,
                        amberTemplate,
                        templates);

                validatePreservedChemistry(
                        chain.id(),
                        residue,
                        optimizedAtoms);

                int moved = movedHydrogenCount(
                        residue.getAtoms(),
                        optimizedAtoms);

                if (moved > 0) {
                    movedHydrogens += moved;
                    optimizedLabels.add(
                            residueLabel(chain.id(), residue));
                }

                optimizedResidues.add(
                        residue.toBuilder()
                                .atoms(optimizedAtoms)
                                .build());
            }

            optimizedChains.add(
                    new Chain(chain.id(), optimizedResidues));
        }

        Structure optimizedStructure = new Structure(optimizedChains);
        HydrogenOptimizationReport report =
                new HydrogenOptimizationReport(
                        incoming.getResidueCount(),
                        optimizedStructure.getResidueCount(),
                        optimizedLabels.size(),
                        movedHydrogens,
                        optimizedLabels);

        PreparedProtein result = preparedProtein
                .withProtein(copyWithStructure(
                        preparedProtein.protein(),
                        optimizedStructure))
                .withAttribute(
                        HYDROGEN_OPTIMIZATION_REPORT_ATTRIBUTE,
                        report);

        return OperationResult.success(result);
    }

    private AmberParameterSet loadParameters(Path configuredPath) {
        AmberParameterSet parameters = AmberParameterSet.createEmpty();

        try {
            if (configuredPath == null) {
                parameters.loadFromResource(
                        AmberParameterSet.DEFAULT_RESOURCE);
            } else {
                parameters.loadFromFile(configuredPath);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to load Amber Lennard-Jones parameters "
                            + "for hydrogen optimization.",
                    exception);
        }

        return parameters;
    }

    private void requireHydrogenation(PreparedProtein preparedProtein) {
        if (!preparedProtein.attributes().containsKey(
                ReceptorHydrogenationOperation
                        .HYDROGENATION_REPORT_ATTRIBUTE)) {
            throw new IllegalStateException(
                    "Hydrogenation report is missing. Run "
                            + "ReceptorHydrogenationOperation first.");
        }
    }

    private Map<String, ResidueState> residueStates(
            PreparedProtein preparedProtein) {

        Object value = preparedProtein.attributes().get(
                ResidueStateAssignmentOperation.RESIDUE_STATES_ATTRIBUTE);

        if (!(value instanceof Map<?, ?> rawStates)) {
            throw new IllegalStateException(
                    "Residue states are missing. Run "
                            + "ResidueStateAssignmentOperation first.");
        }

        Map<String, ResidueState> states = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawStates.entrySet()) {
            if (!(entry.getKey() instanceof String key)
                    || !(entry.getValue() instanceof ResidueState state)) {
                throw new IllegalStateException(
                        "Invalid residue-state entry: " + entry);
            }
            states.put(key, state);
        }

        return Map.copyOf(states);
    }

    private Map<String, String> amberTemplates(
            Map<String, ResidueState> states) {

        Map<String, String> templates = new LinkedHashMap<>();
        for (ResidueState state : states.values()) {
            templates.put(
                    state.residueKey(),
                    state.amberTemplateName());
        }
        return Map.copyOf(templates);
    }

    private void validatePreservedChemistry(
            String chainId,
            Residue input,
            List<Atom> outputAtoms) {

        if (input.getAtomCount() != outputAtoms.size()) {
            throw new IllegalStateException(
                    "Hydrogen optimization changed atom count for "
                            + residueLabel(chainId, input));
        }

        for (int index = 0; index < input.getAtomCount(); index++) {
            Atom before = input.getAtoms().get(index);
            Atom after = outputAtoms.get(index);

            if (!Objects.equals(before.getName(), after.getName())
                    || before.getElement() != after.getElement()) {
                throw new IllegalStateException(
                        "Hydrogen optimization changed atom identity/order for "
                                + residueLabel(chainId, input));
            }

            if (before.getElement() != Element.H
                    && before.getPosition().distance(after.getPosition())
                    > POSITION_TOLERANCE) {
                throw new IllegalStateException(
                        "Hydrogen optimization moved heavy atom "
                                + before.getName()
                                + " in "
                                + residueLabel(chainId, input));
            }
        }
    }

    private int movedHydrogenCount(
            List<Atom> input,
            List<Atom> output) {

        int moved = 0;
        for (int index = 0; index < input.size(); index++) {
            Atom before = input.get(index);
            Atom after = output.get(index);
            if (before.getElement() == Element.H
                    && before.getPosition().distance(after.getPosition())
                    > POSITION_TOLERANCE) {
                moved++;
            }
        }
        return moved;
    }

    private Protein copyWithStructure(
            Protein protein,
            Structure structure) {

        return new Protein(
                protein.id(),
                protein.uniProtId().orElse(null),
                protein.name(),
                protein.gene().orElse(null),
                protein.organism().orElse(null),
                protein.function().orElse(null),
                structure);
    }

    private String residueKey(String chainId, Residue residue) {
        Character insertionCode = residue.getInsertionCode();
        return chainId
                + ":"
                + residue.getNumber()
                + (insertionCode == null ? "" : insertionCode);
    }

    private String residueLabel(String chainId, Residue residue) {
        return residue.getName()
                + " "
                + residueKey(chainId, residue);
    }
}
