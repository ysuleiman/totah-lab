package totah.lab.ligand.charge;

import totah.lab.chemistry.MolecularGraph;
import totah.lab.math.charges.ChargeModel;
import totah.lab.math.charges.GasteigerModel;
import totah.lab.protein.Atom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LigandChargeAssigner {

    private static final double CHARGE_TOLERANCE = 1.0e-9;

    private final ChargeModel chargeModel;

    public LigandChargeAssigner() {
        this(new GasteigerModel());
    }

    public LigandChargeAssigner(ChargeModel chargeModel) {
        this.chargeModel = Objects.requireNonNull(chargeModel, "chargeModel is null");
    }

    public LigandChargeAssignmentResult assign(MolecularGraph graph) {
        Objects.requireNonNull(graph, "graph is null");
        if (graph.atoms().isEmpty()) {
            throw new IllegalArgumentException("Cannot assign charges to an empty ligand graph");
        }

        MolecularGraphChargeSystem system = new MolecularGraphChargeSystem(graph);
        validateSupportedElements(system);
        int formalCharge = graph.atomProperties().stream()
                .mapToInt(property -> property.formalCharge())
                .sum();
        double[] charges = chargeModel.computeCharges(system, formalCharge);
        validateCharges(charges, graph.atoms().size(), formalCharge);

        List<Atom> chargedAtoms = new ArrayList<>(graph.atoms().size());
        double totalPartialCharge = 0.0;
        for (int index = 0; index < graph.atoms().size(); index++) {
            chargedAtoms.add(graph.atoms().get(index).toBuilder()
                    .charge(charges[index])
                    .build());
            totalPartialCharge += charges[index];
        }
        MolecularGraph chargedGraph = new MolecularGraph(
                chargedAtoms, graph.bonds(), graph.atomProperties());
        return new LigandChargeAssignmentResult(
                chargedGraph,
                chargeModel.getClass().getSimpleName(),
                formalCharge,
                totalPartialCharge);
    }

    private void validateSupportedElements(MolecularGraphChargeSystem system) {
        for (int index = 0; index < system.size(); index++) {
            String element = system.getElement(index);
            if (!chargeModel.hasParameters(element)) {
                throw new IllegalArgumentException(
                        "Charge model " + chargeModel.getClass().getSimpleName()
                                + " has no parameters for ligand element " + element
                                + " at atom index " + index);
            }
        }
    }

    private void validateCharges(
            double[] charges,
            int atomCount,
            int formalCharge) {
        if (charges == null || charges.length < atomCount) {
            throw new IllegalStateException(
                    "Charge model returned fewer charges than ligand atoms");
        }
        double total = 0.0;
        for (int index = 0; index < atomCount; index++) {
            if (!Double.isFinite(charges[index])) {
                throw new IllegalStateException(
                        "Charge model returned a non-finite charge at atom index " + index);
            }
            total += charges[index];
        }
        if (Math.abs(total - formalCharge) > CHARGE_TOLERANCE) {
            throw new IllegalStateException(
                    "Ligand partial-charge total " + total
                            + " does not preserve formal charge " + formalCharge);
        }
    }
}
