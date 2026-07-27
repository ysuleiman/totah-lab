package totah.lab.pipeline.stage;

import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.Stage;
import totah.lab.protein.Residue;
import totah.lab.protein.hydrogenation.DisulfideDetector;
import totah.lab.protein.hydrogenation.ProtonationConfig;
import totah.lab.protein.hydrogenation.ReceptorHydrogenator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Thin pipeline adapter. All heavy logic lives in ReceptorHydrogenator.
 */
public class ReceptorHydrogenationStage implements Stage {

    @Override
    @SuppressWarnings("unchecked")
    public void run(PipelineContext context) throws Exception {
        Objects.requireNonNull(context, "context is null");
        List<Residue> incoming = (List<Residue>) context.require(ContextKeys.PROTEIN_RESIDUES);
        if (incoming.isEmpty()) {
            throw new IllegalStateException("No protein_residues in context. Run ResidueStateAssignmentStage first.");
        }
        Map<String, ResidueState> states = (Map<String, ResidueState>) context.require(ContextKeys.RESIDUE_STATES);
        Map<String, String> amberTemplates = amberTemplates(states);
        validateEveryResidueHasState(incoming, amberTemplates);

        ProtonationConfig config = ProtonationConfig.fromContext(context);
        int inputHydrogens = hydrogenCount(incoming);
        List<Residue> protonated = ReceptorHydrogenator.hydrogenate(incoming, config, amberTemplates);
        int outputHydrogens = hydrogenCount(protonated);

        context.put(ContextKeys.PROTEIN_RESIDUES, protonated);
        // Pipeline contract: publish the cross-linked CYS set. SG positions do
        // not move during hydrogenation, so re-detection on the output is exact.
        Set<Residue> disulfideResidues = disulfideResiduesFromStates(protonated, states);
        if (config.detectDisulfides()) {
            disulfideResidues = DisulfideDetector.findDisulfideBonds(protonated, config.disulfideCutoff());
        }
        if (!disulfideResidues.isEmpty()) {
            context.put(ContextKeys.DISULFIDE_BONDS, disulfideResidues);
        }
        context.put(ContextKeys.HYDROGENATION_REPORT,
                new HydrogenationReport(incoming.size(), protonated.size(), inputHydrogens,
                        outputHydrogens, assignedTemplates(states), residueLabels(disulfideResidues)));
        context.put(ContextKeys.PH, config.ph());
    }

    private Map<String, String> amberTemplates(Map<String, ResidueState> states) {
        Map<String, String> templates = new LinkedHashMap<>();
        for (ResidueState state : states.values()) {
            templates.put(state.residueKey(), state.amberTemplateName());
        }
        return Map.copyOf(templates);
    }

    private void validateEveryResidueHasState(List<Residue> residues, Map<String, String> amberTemplates) {
        for (Residue residue : residues) {
            String key = residueKey(residue);
            if (!amberTemplates.containsKey(key)) {
                throw new IllegalStateException("Missing residue state for " + residueLabel(residue)
                        + ". Run ResidueStateAssignmentStage first.");
            }
        }
    }

    private int hydrogenCount(List<Residue> residues) {
        int count = 0;
        for (Residue residue : residues) {
            for (var atom : residue.getAtoms()) {
                if (atom.getElement() != null && "H".equals(atom.getElement().getSymbol())) {
                    count++;
                }
            }
        }
        return count;
    }

    private List<String> assignedTemplates(Map<String, ResidueState> states) {
        List<String> assigned = new ArrayList<>();
        for (ResidueState state : states.values()) {
            assigned.add(state.residueKey() + " -> " + state.amberTemplateName());
        }
        return assigned;
    }

    private List<String> residueLabels(Set<Residue> residues) {
        return residues.stream()
                .map(this::residueLabel)
                .sorted()
                .toList();
    }

    private Set<Residue> disulfideResiduesFromStates(List<Residue> residues, Map<String, ResidueState> states) {
        java.util.LinkedHashSet<Residue> disulfides = new java.util.LinkedHashSet<>();
        for (Residue residue : residues) {
            ResidueState state = states.get(residueKey(residue));
            if (state != null && state.disulfide()) {
                disulfides.add(residue);
            }
        }
        return Set.copyOf(disulfides);
    }

    private String residueKey(Residue residue) {
        return residue.getChain() + ":" + residue.getNumber() + insertionSuffix(residue);
    }

    private String insertionSuffix(Residue residue) {
        return residue.getInsertionCode() == null || residue.getInsertionCode() == ' '
                ? ""
                : residue.getInsertionCode().toString();
    }

    private String residueLabel(Residue residue) {
        String insertion = residue.getInsertionCode() == null || residue.getInsertionCode() == ' '
                ? ""
                : residue.getInsertionCode().toString();
        return residue.getName() + " " + residue.getChain() + ":" + residue.getNumber() + insertion;
    }
}
