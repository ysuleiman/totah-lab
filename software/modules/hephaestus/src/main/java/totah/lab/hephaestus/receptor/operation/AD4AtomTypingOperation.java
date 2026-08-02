package totah.lab.hephaestus.receptor.operation;

import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.preparation.OperationResult;
import totah.lab.hephaestus.receptor.ReceptorPreparationOperation;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.residue.ResidueState;
import totah.lab.hephaestus.topology.ProteinTopology;
import totah.lab.hephaestus.typing.AD4AtomTyper;
import totah.lab.hephaestus.typing.AtomTyper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AD4AtomTypingOperation
        implements ReceptorPreparationOperation {

    public static final String AD4_ATOM_TYPING_REPORT_ATTRIBUTE =
            "ad4-atom-typing-report";

    private final AtomTyper atomTyper;

    public AD4AtomTypingOperation() {
        this(new AD4AtomTyper());
    }

    public AD4AtomTypingOperation(AtomTyper atomTyper) {
        this.atomTyper = Objects.requireNonNull(atomTyper, "atomTyper");
    }

    @Override
    public OperationResult<PreparedProtein> apply(
            PreparedProtein preparedProtein,
            ReceptorPreparationOptions options) {
        Objects.requireNonNull(preparedProtein, "preparedProtein");
        Objects.requireNonNull(options, "options");

        if (!options.assignAtomTypes()) {
            return OperationResult.success(preparedProtein);
        }
        if (!preparedProtein.attributes().containsKey(
                ChargeAssignmentOperation.CHARGE_ASSIGNMENT_REPORT_ATTRIBUTE)
                || preparedProtein.charges() == null) {
            throw new IllegalStateException(
                    "Charge assignment is missing. Run "
                            + "ChargeAssignmentOperation first.");
        }
        if (!(preparedProtein.topology() instanceof ProteinTopology topology)) {
            throw new IllegalStateException(
                    "Protein topology is missing. Run "
                            + "TopologyBuilderOperation first.");
        }

        AtomTyper.TypingResult result = atomTyper.assign(
                preparedProtein.protein().structure(),
                topology,
                residueStates(preparedProtein));
        Protein typedProtein = copyWithStructure(
                preparedProtein.protein(), result.structure());

        return OperationResult.success(
                preparedProtein
                        .withProtein(typedProtein)
                        .withAtomTypes(result.assignment())
                        .withAttribute(
                                AD4_ATOM_TYPING_REPORT_ATTRIBUTE,
                                result.report()));
    }

    private Map<String, ResidueState> residueStates(PreparedProtein protein) {
        Object value = protein.attributes().get(
                ResidueStateAssignmentOperation.RESIDUE_STATES_ATTRIBUTE);
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalStateException("Residue states are missing.");
        }
        Map<String, ResidueState> states = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key
                    && entry.getValue() instanceof ResidueState state) {
                states.put(key, state);
            } else {
                throw new IllegalStateException(
                        "Invalid residue-state entry: " + entry);
            }
        }
        return Map.copyOf(states);
    }

    private Protein copyWithStructure(Protein protein, Structure structure) {
        return new Protein(
                protein.id(),
                protein.uniProtId().orElse(null),
                protein.name(),
                protein.gene().orElse(null),
                protein.organism().orElse(null),
                protein.function().orElse(null),
                structure);
    }
}
