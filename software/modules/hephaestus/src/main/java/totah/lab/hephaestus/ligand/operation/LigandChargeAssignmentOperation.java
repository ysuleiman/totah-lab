package totah.lab.hephaestus.ligand.operation;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.hephaestus.charge.AssignedCharge;
import totah.lab.hephaestus.charge.ChargeAssignment;
import totah.lab.hephaestus.ligand.LigandPreparationOperation;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.charge.LigandTopologyChargeSystem;
import totah.lab.hephaestus.ligand.topology.LigandTopology;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.preparation.OperationResult;
import totah.lab.hephaestus.ligand.charge.ChargeModel;
import totah.lab.hephaestus.ligand.charge.GasteigerModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LigandChargeAssignmentOperation implements LigandPreparationOperation {
    private static final double CHARGE_TOLERANCE = 1.0e-8;
    private final ChargeModel chargeModel;

    public LigandChargeAssignmentOperation() {
        this(new GasteigerModel());
    }

    public LigandChargeAssignmentOperation(ChargeModel chargeModel) {
        this.chargeModel = Objects.requireNonNull(chargeModel, "chargeModel");
    }

    @Override
    public OperationResult<PreparedLigand> apply(
            PreparedLigand preparedLigand, LigandPreparationOptions options) {
        Objects.requireNonNull(preparedLigand, "preparedLigand");
        Objects.requireNonNull(options, "options");
        if (!options.assignCharges()) {
            return OperationResult.success(preparedLigand);
        }
        if (!(preparedLigand.topology() instanceof LigandTopology topology)) {
            throw new IllegalStateException("Ligand CCD topology is required before charges.");
        }
        Chain chain = LigandStructureSupport.singleChain(preparedLigand.ligand());
        Residue residue = LigandStructureSupport.singleResidue(preparedLigand.ligand());
        List<Atom> atoms = residue.getAtoms();
        LigandTopologyChargeSystem system = new LigandTopologyChargeSystem(atoms, topology);
        for (int index = 0; index < system.size(); index++) {
            if (!chargeModel.hasParameters(system.getElement(index))) {
                throw new IllegalArgumentException(
                        "Charge model has no parameters for " + system.getElement(index));
            }
        }
        int formalCharge = topology.atomProperties().stream()
                .mapToInt(property -> property.formalCharge()).sum();
        double[] values = chargeModel.computeCharges(system, formalCharge);
        if (values == null || values.length < atoms.size()) {
            throw new IllegalStateException("Charge model returned too few charges.");
        }
        List<Atom> chargedAtoms = new ArrayList<>();
        List<AssignedCharge> assignments = new ArrayList<>();
        double total = 0.0;
        for (int index = 0; index < atoms.size(); index++) {
            double value = values[index];
            if (!Double.isFinite(value)) {
                throw new IllegalStateException("Non-finite charge at atom index " + index);
            }
            Atom atom = atoms.get(index);
            chargedAtoms.add(atom.toBuilder().charge(value).build());
            assignments.add(new AssignedCharge(
                    index, chain.id(), residue.getNumber(), residue.getInsertionCode(),
                    atom.getName(), value, atom.getAmberType(),
                    chargeModel.getClass().getSimpleName()));
            total += value;
        }
        if (Math.abs(total - formalCharge) > CHARGE_TOLERANCE) {
            throw new IllegalStateException(
                    "Partial charge total does not preserve formal charge: " + total);
        }
        var ligand = LigandStructureSupport.replaceAtoms(preparedLigand.ligand(), chargedAtoms);
        ChargeAssignment assignment = new ChargeAssignment(
                chargeModel.getClass().getSimpleName(), assignments);
        return OperationResult.success(preparedLigand.withLigand(ligand).withCharges(assignment));
    }
}
