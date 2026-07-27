package totah.lab.pipeline.stage;

import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.Stage;
import totah.lab.protein.Atom;
import totah.lab.protein.Residue;
import totah.lab.topology.AmberParameterSet;
import totah.lab.topology.AmberResidueTemplateLibrary;
import totah.lab.topology.HydrogenOptimizer;

import java.util.ArrayList;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Optional second-pass stage that optimizes rotatable hydrogen positions.
 *
 * <p>Run this <em>after</em> {@link ReceptorHydrogenationStage}.
 * It evaluates Asn/Gln flips, His tautomers, and hydroxyl/methyl rotations
 * using Coulomb + Lennard-Jones scoring.
 *
 * <p>Configurable via PipelineContext:
 * <ul>
 *   <li>{@link ContextKeys#AMBER_PARM_PATH}: Path or String to AMBER parameter file (optional)</li>
 * </ul>
 */
public class HydrogenOptimizationStage implements Stage {

    @Override
    @SuppressWarnings("unchecked")
    public void run(PipelineContext context) throws Exception {
        Objects.requireNonNull(context, "context is null");
        List<Residue> incoming = (List<Residue>) context.require(ContextKeys.PROTEIN_RESIDUES);
        if (incoming.isEmpty()) {
            throw new IllegalStateException("No protein_residues in context. Run ReceptorHydrogenationStage first.");
        }
        context.require(ContextKeys.HYDROGENATION_REPORT);
        Map<String, ResidueState> states = (Map<String, ResidueState>) context.require(ContextKeys.RESIDUE_STATES);

        AmberResidueTemplateLibrary amberLib = AmberResidueTemplateLibrary.getInstance();

        // Fresh per-run instance, not the shared singleton (avoids leaking
        // parameters across runs/tests and partially-modified singleton state)
        AmberParameterSet ljSet = AmberParameterSet.createEmpty();
        try {
            Object configuredParameters = context.get(ContextKeys.AMBER_PARM_PATH);
            if (configuredParameters instanceof Path path) {
                ljSet.loadFromFile(path);
            } else if (configuredParameters != null) {
                ljSet.loadFromResource(configuredParameters.toString());
            } else {
                ljSet.loadFromResource(AmberParameterSet.DEFAULT_RESOURCE);
            }
        } catch (Exception e) {
            System.err.println("[HydrogenOptimization] Failed to load LJ parameters: " + e.getMessage());
        }

        double clashCutoff = parseDouble(context, ContextKeys.HYDROGEN_CLASH_CUTOFF, 1.0);
        HydrogenOptimizer optimizer = new HydrogenOptimizer(amberLib, ljSet, clashCutoff);

        List<Residue> optimized = new ArrayList<>();
        List<String> optimizedResidueLabels = new ArrayList<>();
        int movedHydrogens = 0;
        for (Residue r : incoming) {
            ResidueState state = states.get(residueKey(r));
            if (state == null) {
                throw new IllegalStateException("Missing residue state for " + residueLabel(r));
            }
            List<Atom> optAtoms = optimizer.optimize(r, incoming, state.amberTemplateName());
            validatePreservedChemistry(r, optAtoms);
            int moved = movedHydrogenCount(r.getAtoms(), optAtoms);
            if (moved > 0) {
                movedHydrogens += moved;
                optimizedResidueLabels.add(residueLabel(r));
            }
            optimized.add(r.toBuilder().atoms(optAtoms).build());
        }

        context.put(ContextKeys.PROTEIN_RESIDUES, optimized);
        context.put(ContextKeys.HYDROGEN_OPTIMIZATION_REPORT,
                new HydrogenOptimizationReport(incoming.size(), optimized.size(),
                        optimizedResidueLabels.size(), movedHydrogens, optimizedResidueLabels));
    }

    private double parseDouble(PipelineContext ctx, String key, double defaultVal) {
        Object val = ctx.get(key);
        if (val == null) return defaultVal;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private void validatePreservedChemistry(Residue input, List<Atom> outputAtoms) {
        List<String> inputHeavy = input.getAtoms().stream()
                .filter(atom -> !isHydrogen(atom))
                .map(Atom::getName)
                .toList();
        List<String> outputHeavy = outputAtoms.stream()
                .filter(atom -> !isHydrogen(atom))
                .map(Atom::getName)
                .toList();
        if (!inputHeavy.equals(outputHeavy)) {
            throw new IllegalStateException("Hydrogen optimization changed heavy-atom identity/order for "
                    + residueLabel(input));
        }

        long inputHydrogens = input.getAtoms().stream().filter(this::isHydrogen).count();
        long outputHydrogens = outputAtoms.stream().filter(this::isHydrogen).count();
        if (inputHydrogens != outputHydrogens) {
            throw new IllegalStateException("Hydrogen optimization changed hydrogen count for "
                    + residueLabel(input) + " (" + inputHydrogens + " -> " + outputHydrogens + ")");
        }

        List<String> inputHydrogenNames = input.getAtoms().stream()
                .filter(this::isHydrogen)
                .map(Atom::getName)
                .sorted()
                .toList();
        List<String> outputHydrogenNames = outputAtoms.stream()
                .filter(this::isHydrogen)
                .map(Atom::getName)
                .sorted()
                .toList();
        if (!inputHydrogenNames.equals(outputHydrogenNames)) {
            throw new IllegalStateException("Hydrogen optimization changed hydrogen identities for "
                    + residueLabel(input));
        }
    }

    private int movedHydrogenCount(List<Atom> inputAtoms, List<Atom> outputAtoms) {
        int moved = 0;
        int count = Math.min(inputAtoms.size(), outputAtoms.size());
        for (int i = 0; i < count; i++) {
            Atom before = inputAtoms.get(i);
            Atom after = outputAtoms.get(i);
            if (isHydrogen(before) && isHydrogen(after) && before.getName().equals(after.getName())
                    && before.getPosition().distance(after.getPosition()) > 1e-6) {
                moved++;
            }
        }
        return moved;
    }

    private boolean isHydrogen(Atom atom) {
        return atom.getElement() != null && "H".equals(atom.getElement().getSymbol());
    }

    private String residueKey(Residue residue) {
        return residue.getChain() + ":" + residue.getNumber();
    }

    private String residueLabel(Residue residue) {
        String insertion = residue.getInsertionCode() == null || residue.getInsertionCode() == ' '
                ? ""
                : residue.getInsertionCode().toString();
        return residue.getName() + " " + residue.getChain() + ":" + residue.getNumber() + insertion;
    }
}
