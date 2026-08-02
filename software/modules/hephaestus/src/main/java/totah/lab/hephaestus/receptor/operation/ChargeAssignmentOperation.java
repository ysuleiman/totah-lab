package totah.lab.hephaestus.receptor.operation;

import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.charge.AmberChargeAssigner;
import totah.lab.hephaestus.charge.ChargeAssigner;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.preparation.OperationResult;
import totah.lab.hephaestus.receptor.ReceptorPreparationOperation;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.residue.ResidueState;
import totah.lab.hephaestus.topology.ProteinTopology;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ChargeAssignmentOperation
        implements ReceptorPreparationOperation {

    public static final String CHARGE_ASSIGNMENT_REPORT_ATTRIBUTE =
            "charge-assignment-report";

    private final ChargeAssigner chargeAssigner;

    public ChargeAssignmentOperation() {
        this(new AmberChargeAssigner());
    }

    public ChargeAssignmentOperation(ChargeAssigner chargeAssigner) {
        this.chargeAssigner = Objects.requireNonNull(
                chargeAssigner,
                "chargeAssigner");
    }

    @Override
    public OperationResult<PreparedProtein> apply(
            PreparedProtein preparedProtein,
            ReceptorPreparationOptions options) {

        Objects.requireNonNull(preparedProtein, "preparedProtein");
        Objects.requireNonNull(options, "options");

        if (!options.assignCharges()) {
            return OperationResult.success(preparedProtein);
        }

        ProteinTopology topology = requireTopology(preparedProtein);
        ChargeAssigner.AssignmentResult result = chargeAssigner.assign(
                preparedProtein.protein().structure(),
                topology,
                residueStates(preparedProtein));

        Protein chargedProtein = copyWithStructure(
                preparedProtein.protein(),
                result.structure());

        return OperationResult.success(
                preparedProtein
                        .withProtein(chargedProtein)
                        .withCharges(result.assignment())
                        .withAttribute(
                                CHARGE_ASSIGNMENT_REPORT_ATTRIBUTE,
                                result.report()));
    }

    private ProteinTopology requireTopology(PreparedProtein protein) {
        if (!protein.attributes().containsKey(
                TopologyBuilderOperation.TOPOLOGY_BUILD_REPORT_ATTRIBUTE)) {
            throw new IllegalStateException(
                    "Topology build report is missing. Run "
                            + "TopologyBuilderOperation first.");
        }
        if (!(protein.topology() instanceof ProteinTopology topology)) {
            throw new IllegalStateException(
                    "Protein topology is missing. Run "
                            + "TopologyBuilderOperation first.");
        }
        return topology;
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
}
