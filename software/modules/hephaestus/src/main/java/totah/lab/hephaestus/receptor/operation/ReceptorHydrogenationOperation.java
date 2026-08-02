package totah.lab.hephaestus.receptor.operation;

import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.preparation.OperationResult;
import totah.lab.hephaestus.receptor.ReceptorPreparationOperation;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.hydrogenation.HydrogenationReport;
import totah.lab.hephaestus.receptor.hydrogen.ReceptorHydrogenator;
import totah.lab.hephaestus.receptor.protonation.ProtonationConfig;
import totah.lab.hephaestus.receptor.residue.ResidueState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ReceptorHydrogenationOperation
        implements ReceptorPreparationOperation {

    public static final String HYDROGENATION_REPORT_ATTRIBUTE =
            "hydrogenation-report";

    public static final String DISULFIDE_RESIDUES_ATTRIBUTE =
            "disulfide-residues";

    private final ReceptorHydrogenator hydrogenator;
    public ReceptorHydrogenationOperation(
            ReceptorHydrogenator hydrogenator) {

        this.hydrogenator = Objects.requireNonNull(
                hydrogenator,
                "hydrogenator");
    }

    @Override
    public OperationResult<PreparedProtein> apply(
            PreparedProtein preparedProtein,
            ReceptorPreparationOptions options) {

        Objects.requireNonNull(
                preparedProtein,
                "preparedProtein");

        Objects.requireNonNull(
                options,
                "options");

        if (!options.addHydrogens()) {
            return OperationResult.success(preparedProtein);
        }

        Protein protein = preparedProtein.protein();
        Structure incomingStructure = protein.structure();

        if (incomingStructure.getResidueCount() == 0) {
            throw new IllegalStateException(
                    "Protein structure contains no residues. "
                            + "Run residue-state assignment first.");
        }

        Map<String, ResidueState> states =
                residueStates(preparedProtein);

        Map<String, String> amberTemplates =
                amberTemplates(states);

        validateEveryResidueHasState(
                incomingStructure,
                amberTemplates);

        ProtonationConfig config = options.protonationConfig();

        int inputHydrogenCount =
                hydrogenCount(incomingStructure);

        List<Chain> protonatedChains = new ArrayList<>();

        for (Chain chain : incomingStructure.getChains()) {
            List<Residue> protonatedResidues =
                    hydrogenator.hydrogenate(chain, config, amberTemplates);
            if (protonatedResidues.size() != chain.residueCount()) {
                throw new IllegalStateException(
                        "Hydrogenation changed residue count for chain "
                                + chain.id()
                                + " from "
                                + chain.residueCount()
                                + " to "
                                + protonatedResidues.size()
                                + ".");
            }

            protonatedChains.add(
                    new Chain(
                            chain.id(),
                            protonatedResidues));
        }

        Structure protonatedStructure =
                new Structure(protonatedChains);

        int outputHydrogenCount = hydrogenCount(protonatedStructure);
        Set<String> disulfideResidues = disulfideResidueKeys(states);
        HydrogenationReport report =
                new HydrogenationReport(
                        incomingStructure.getResidueCount(),
                        protonatedStructure.getResidueCount(),
                        inputHydrogenCount,
                        outputHydrogenCount,
                        assignedTemplates(states),
                        disulfideResidues.stream()
                                .sorted()
                                .toList());

        Protein protonatedProtein = copyWithStructure(
                protein,
                protonatedStructure);

        PreparedProtein updated =
                preparedProtein
                        .withProtein(protonatedProtein)
                        .withAttribute(
                                HYDROGENATION_REPORT_ATTRIBUTE,
                                report)
                        .withAttribute(
                                DISULFIDE_RESIDUES_ATTRIBUTE,
                                disulfideResidues)
                        .withAttribute(
                                "ph",
                                config.ph());

        return OperationResult.success(updated);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ResidueState> residueStates(PreparedProtein preparedProtein) {
        Object value = preparedProtein.attributes().get(
                ResidueStateAssignmentOperation
                        .RESIDUE_STATES_ATTRIBUTE);
        if (!(value instanceof Map<?, ?> rawStates)) {
            throw new IllegalStateException(
                    "Residue states are missing. "
                            + "Run ResidueStateAssignmentOperation first.");
        }
        Map<String, ResidueState> states = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : rawStates.entrySet()) {
            if (!(entry.getKey() instanceof String key)
                    || !(entry.getValue()
                    instanceof ResidueState state)) {

                throw new IllegalStateException(
                        "Invalid residue-state entry: "
                                + entry);
            }

            states.put(key, state);
        }

        if (states.isEmpty()) {
            throw new IllegalStateException(
                    "Residue-state map is empty.");
        }

        return Map.copyOf(states);
    }

    private Map<String, String> amberTemplates(
            Map<String, ResidueState> states) {

        Map<String, String> templates =
                new LinkedHashMap<>();

        for (ResidueState state : states.values()) {
            templates.put(
                    state.residueKey(),
                    state.amberTemplateName());
        }

        return Map.copyOf(templates);
    }

    private void validateEveryResidueHasState(
            Structure structure,
            Map<String, String> amberTemplates) {

        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                String key =
                        residueKey(chain.id(), residue);

                if (!amberTemplates.containsKey(key)) {
                    throw new IllegalStateException(
                            "Missing residue state for "
                                    + residueLabel(
                                    chain.id(),
                                    residue)
                                    + ". Run "
                                    + "ResidueStateAssignmentOperation "
                                    + "first.");
                }
            }
        }
    }

    private int hydrogenCount(
            Structure structure) {

        int count = 0;

        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                for (var atom : residue.getAtoms()) {
                    if (atom.getElement() == Element.H) {
                        count++;
                    }
                }
            }
        }

        return count;
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

    private List<String> assignedTemplates(
            Map<String, ResidueState> states) {

        return states.values()
                .stream()
                .map(state ->
                        state.residueKey()
                                + " -> "
                                + state.amberTemplateName())
                .sorted()
                .toList();
    }

    private Set<String> disulfideResidueKeys(
            Map<String, ResidueState> states) {

        Set<String> keys =
                new LinkedHashSet<>();

        for (ResidueState state : states.values()) {
            if (state.disulfide()) {
                keys.add(state.residueKey());
            }
        }

        return Set.copyOf(keys);
    }

    private String residueKey(
            String chainId,
            Residue residue) {

        return chainId
                + ":"
                + residue.getNumber()
                + insertionSuffix(residue);
    }

    private String insertionSuffix(
            Residue residue) {

        Character insertionCode =
                residue.getInsertionCode();

        return insertionCode == null
                || Character.isWhitespace(insertionCode)
                ? ""
                : insertionCode.toString();
    }

    private String residueLabel(
            String chainId,
            Residue residue) {

        return residue.getName()
                + " "
                + residueKey(chainId, residue);
    }
}
