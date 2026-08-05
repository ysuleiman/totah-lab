package totah.lab.hephaestus.validation;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.hephaestus.ligand.flexibility.LigandFlexibilityModel;
import totah.lab.hephaestus.ligand.topology.LigandTopology;
import totah.lab.hephaestus.model.PreparedLigand;

import java.util.ArrayList;
import java.util.List;

public final class PreparedLigandValidator implements PreparationValidator<PreparedLigand> {

    private static final double CHARGE_TOLERANCE = 1.0e-6;

    @Override
    public ValidationReport validate(PreparedLigand preparedLigand) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (preparedLigand == null || preparedLigand.ligand() == null
                || preparedLigand.ligand().structure() == null) {
            issues.add(error(ValidationCode.NULL_VALUE,
                    "Prepared ligand, ligand, and structure are required.", "preparedLigand"));
            return new ValidationReport(issues);
        }
        var structure = preparedLigand.ligand().structure();
        if (structure.getAtomCount() == 0) {
            issues.add(error(ValidationCode.EMPTY_STRUCTURE,
                    "Prepared ligand structure has no atoms.", "structure"));
            return new ValidationReport(issues);
        }
        List<Chain> chains = structure.getChains();
        if (chains.size() != 1 || chains.getFirst().residues().size() != 1) {
            issues.add(error(ValidationCode.METADATA_INCONSISTENT,
                    "A prepared ligand must contain exactly one chain and one residue.",
                    "structure"));
            return new ValidationReport(issues);
        }
        Residue residue = chains.getFirst().residues().getFirst();
        List<Atom> atoms = residue.getAtoms();
        for (Atom atom : atoms) {
            var position = atom.getPosition();
            if (position == null || !Double.isFinite(position.x())
                    || !Double.isFinite(position.y()) || !Double.isFinite(position.z())) {
                issues.add(error(ValidationCode.NONFINITE_COORDINATE,
                        "Atom coordinates are missing or non-finite.", atom.getName()));
            }
            if (!Double.isFinite(atom.getCharge())) {
                issues.add(error(ValidationCode.NONFINITE_CHARGE,
                        "Atom charge is non-finite.", atom.getName()));
            }
            if (atom.getAutoDockType() == null || atom.getAutoDockType().isBlank()) {
                issues.add(error(ValidationCode.MISSING_AD4_TYPE,
                        "Atom has no AD4 type.", atom.getName()));
            }
        }
        validateTopology(preparedLigand, atoms.size(), issues);
        validateCharges(preparedLigand, atoms, issues);
        validateAtomTypes(preparedLigand, atoms.size(), issues);
        validateTorsionModel(preparedLigand, atoms.size(), issues);
        return new ValidationReport(issues);
    }

    private void validateTopology(
            PreparedLigand ligand, int atomCount, List<ValidationIssue> issues) {
        if (!(ligand.topology() instanceof LigandTopology topology)) {
            issues.add(error(ValidationCode.MISSING_TOPOLOGY,
                    "Ligand topology is missing.", "topology"));
            return;
        }
        if (topology.atomCount() != atomCount) {
            issues.add(error(ValidationCode.TOPOLOGY_ATOM_COUNT_MISMATCH,
                    "Topology atom count differs from structure.", "topology"));
        }
        topology.bonds().forEach(bond -> {
            if (bond.atomIndexA() >= atomCount || bond.atomIndexB() >= atomCount) {
                issues.add(error(ValidationCode.TOPOLOGY_INDEX_INVALID,
                        "Topology bond has an invalid atom index.", bond.toString()));
            }
        });
    }

    private void validateCharges(
            PreparedLigand ligand, List<Atom> atoms, List<ValidationIssue> issues) {
        if (ligand.charges() == null) {
            issues.add(error(ValidationCode.MISSING_CHARGE_ASSIGNMENT,
                    "Charge assignment is missing.", "charges"));
            return;
        }
        if (ligand.charges().atomCount() != atoms.size()) {
            issues.add(error(ValidationCode.CHARGE_ATOM_COUNT_MISMATCH,
                    "Charge assignment count differs from structure.", "charges"));
        }
        int formalCharge = ligand.topology() instanceof LigandTopology topology
                ? topology.atomProperties().stream()
                        .mapToInt(property -> property.formalCharge()).sum()
                : ligand.ligand().formalCharge().value();
        double total = atoms.stream().mapToDouble(Atom::getCharge).sum();
        if (Double.isFinite(total) && Math.abs(total - formalCharge) > CHARGE_TOLERANCE) {
            issues.add(error(ValidationCode.METADATA_INCONSISTENT,
                    "Partial charge total " + total
                            + " does not match formal charge " + formalCharge + ".",
                    "charges"));
        }
    }

    private void validateAtomTypes(
            PreparedLigand ligand, int atomCount, List<ValidationIssue> issues) {
        if (ligand.atomTypes() == null) {
            issues.add(error(ValidationCode.MISSING_ATOM_TYPE_ASSIGNMENT,
                    "Atom-type assignment is missing.", "atomTypes"));
            return;
        }
        if (ligand.atomTypes().atomCount() != atomCount) {
            issues.add(error(ValidationCode.ATOM_TYPE_COUNT_MISMATCH,
                    "Atom-type assignment count differs from structure.", "atomTypes"));
        }
    }

    private void validateTorsionModel(
            PreparedLigand ligand, int atomCount, List<ValidationIssue> issues) {
        Object value = ligand.attributes().get(LigandFlexibilityModel.ATTRIBUTE_KEY);
        if (!(value instanceof LigandFlexibilityModel model)) {
            issues.add(error(ValidationCode.METADATA_INCONSISTENT,
                    "Ligand torsion model is missing.", "torsionModel"));
            return;
        }
        if (model.atomCount() != atomCount) {
            issues.add(error(ValidationCode.REPORT_STATE_MISMATCH,
                    "Torsion model atom count differs from structure.", "torsionModel"));
        }
    }

    private ValidationIssue error(ValidationCode code, String message, String location) {
        return new ValidationIssue(ValidationSeverity.ERROR, code, message, location);
    }
}
