package totah.lab.hephaestus.validation;

import totah.lab.gaia.structure.Chain;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.operation.AD4AtomTypingOperation;
import totah.lab.hephaestus.receptor.operation.ChargeAssignmentOperation;
import totah.lab.hephaestus.receptor.operation.TopologyBuilderOperation;
import totah.lab.hephaestus.topology.ProteinTopology;
import totah.lab.hephaestus.validation.internal.CanonicalAtomResolver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PreparedProteinValidator implements PreparationValidator<PreparedProtein> {
    private final FlexibilityModelValidator flexibilityValidator = new FlexibilityModelValidator();

    @Override
    public ValidationReport validate(PreparedProtein preparedProtein) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (preparedProtein == null || preparedProtein.protein() == null
                || preparedProtein.protein().structure() == null) {
            issues.add(error(ValidationCode.NULL_VALUE,"Prepared protein, protein, and structure are required.","preparedProtein"));
            return new ValidationReport(issues);
        }
        var structure = preparedProtein.protein().structure();
        if (structure.getAtomCount() == 0) issues.add(error(ValidationCode.EMPTY_STRUCTURE,"Prepared structure has no atoms.","structure"));
        Set<String> chains = new HashSet<>(); Set<String> residues = new HashSet<>();
        for (Chain chain : structure.getChains()) {
            if (!chains.add(chain.id())) issues.add(error(ValidationCode.DUPLICATE_CHAIN_ID,"Duplicate chain ID.",chain.id()));
            if (chain.residues().isEmpty()) issues.add(error(ValidationCode.EMPTY_CHAIN,"Chain has no residues.",chain.id()));
            for (var residue : chain.residues()) {
                String key = chain.id()+":"+residue.getNumber()+(residue.getInsertionCode()==null?"":residue.getInsertionCode());
                if (!residues.add(key)) issues.add(error(ValidationCode.DUPLICATE_RESIDUE_IDENTITY,"Duplicate residue identity.",key));
                if (residue.getAtoms().isEmpty()) issues.add(error(ValidationCode.EMPTY_RESIDUE,"Residue has no atoms.",key));
                Set<String> atomNames = new HashSet<>();
                for (var atom : residue.getAtoms()) {
                    if (!atomNames.add(atom.getName())) issues.add(error(ValidationCode.DUPLICATE_ATOM_IDENTITY,"Duplicate atom name in residue.",key+":"+atom.getName()));
                    var p = atom.getPosition();
                    if (p == null || !Double.isFinite(p.x()) || !Double.isFinite(p.y()) || !Double.isFinite(p.z()))
                        issues.add(error(ValidationCode.NONFINITE_COORDINATE,"Atom coordinates are missing or non-finite.",key+":"+atom.getName()));
                    if (!Double.isFinite(atom.getCharge())) issues.add(error(ValidationCode.NONFINITE_CHARGE,"Atom charge is non-finite.",key+":"+atom.getName()));
                    if (atom.getAutoDockType()==null || atom.getAutoDockType().isBlank())
                        issues.add(error(ValidationCode.MISSING_AD4_TYPE,"Atom has no AD4 type.",key+":"+atom.getName()));
                }
            }
        }
        CanonicalAtomResolver resolver = new CanonicalAtomResolver(structure);
        ProteinTopology topology = preparedProtein.topology() instanceof ProteinTopology value ? value : null;
        if (topology == null) issues.add(error(ValidationCode.MISSING_TOPOLOGY,"Protein topology is missing.","topology"));
        else {
            if (topology.atomCount()!=resolver.atoms().size()) issues.add(error(ValidationCode.TOPOLOGY_ATOM_COUNT_MISMATCH,"Topology atom count differs from structure.","topology"));
            topology.edges().forEach(edge -> { if (edge.indexA()>=resolver.atoms().size() || edge.indexB()>=resolver.atoms().size())
                issues.add(error(ValidationCode.TOPOLOGY_INDEX_INVALID,"Topology edge has an invalid atom index.",edge.toString())); });
        }
        validateCharges(preparedProtein,resolver,issues);
        validateTypes(preparedProtein,resolver,issues);
        if (preparedProtein.flexibility()!=null && !preparedProtein.flexibility().isEmpty())
            issues.addAll(flexibilityValidator.validate(structure,topology,preparedProtein.flexibility()).issues());
        validateReports(preparedProtein,resolver.atoms().size(),issues);
        return new ValidationReport(issues);
    }

    public ValidationReport validatePreparedProtein(
            PreparedProtein preparedProtein) {
        return validate(preparedProtein);
    }

    private void validateCharges(PreparedProtein protein,CanonicalAtomResolver resolver,List<ValidationIssue> issues) {
        if (protein.charges()==null) { issues.add(error(ValidationCode.MISSING_CHARGE_ASSIGNMENT,"Charge assignment is missing.","charges")); return; }
        if (protein.charges().atomCount()!=resolver.atoms().size()) issues.add(error(ValidationCode.CHARGE_ATOM_COUNT_MISMATCH,"Charge assignment count differs from structure.","charges"));
        Set<Integer> indices=new HashSet<>();
        protein.charges().charges().forEach(value -> {
            if (value.atomIndex()<0 || value.atomIndex()>=resolver.atoms().size()) issues.add(error(ValidationCode.CHARGE_INDEX_INVALID,"Charge atom index is invalid.",value.toString()));
            else {
                var record=resolver.atoms().get(value.atomIndex());
                if (!record.reference().residue().chainId().equals(value.chainId()) || record.reference().residue().residueNumber()!=value.residueNumber()
                        || !record.reference().atomName().equals(value.atomName())) issues.add(error(ValidationCode.CHARGE_INDEX_INVALID,"Charge identity does not match canonical atom.",value.toString()));
            }
            if (!indices.add(value.atomIndex())) issues.add(error(ValidationCode.DUPLICATE_CANONICAL_ASSIGNMENT,"Duplicate charge atom index.",Integer.toString(value.atomIndex())));
        });
    }
    private void validateTypes(PreparedProtein protein,CanonicalAtomResolver resolver,List<ValidationIssue> issues) {
        if (protein.atomTypes()==null) { issues.add(error(ValidationCode.MISSING_ATOM_TYPE_ASSIGNMENT,"Atom-type assignment is missing.","atomTypes")); return; }
        if (protein.atomTypes().atomCount()!=resolver.atoms().size()) issues.add(error(ValidationCode.ATOM_TYPE_COUNT_MISMATCH,"Atom-type assignment count differs from structure.","atomTypes"));
        Set<Integer> indices=new HashSet<>();
        protein.atomTypes().atomTypes().forEach(value -> {
            if (value.atomIndex()<0 || value.atomIndex()>=resolver.atoms().size()) issues.add(error(ValidationCode.ATOM_TYPE_INDEX_INVALID,"Atom-type index is invalid.",value.toString()));
            else {
                var record=resolver.atoms().get(value.atomIndex());
                if (!record.reference().residue().chainId().equals(value.chainId()) || record.reference().residue().residueNumber()!=value.residueNumber()
                        || !record.reference().atomName().equals(value.atomName())) issues.add(error(ValidationCode.ATOM_TYPE_INDEX_INVALID,"Atom-type identity does not match canonical atom.",value.toString()));
            }
            if (!indices.add(value.atomIndex())) issues.add(error(ValidationCode.DUPLICATE_CANONICAL_ASSIGNMENT,"Duplicate atom-type index.",Integer.toString(value.atomIndex())));
        });
    }
    private void validateReports(PreparedProtein protein,int count,List<ValidationIssue> issues) {
        requireReport(protein,TopologyBuilderOperation.TOPOLOGY_BUILD_REPORT_ATTRIBUTE,issues);
        requireReport(protein,ChargeAssignmentOperation.CHARGE_ASSIGNMENT_REPORT_ATTRIBUTE,issues);
        requireReport(protein,AD4AtomTypingOperation.AD4_ATOM_TYPING_REPORT_ATTRIBUTE,issues);
        Object charge=protein.attributes().get(ChargeAssignmentOperation.CHARGE_ASSIGNMENT_REPORT_ATTRIBUTE);
        if (charge instanceof totah.lab.hephaestus.charge.ChargeAssignmentReport report && report.atomCount()!=count)
            issues.add(error(ValidationCode.REPORT_STATE_MISMATCH,"Charge report atom count contradicts structure.","charge-assignment-report"));
        Object typing=protein.attributes().get(AD4AtomTypingOperation.AD4_ATOM_TYPING_REPORT_ATTRIBUTE);
        if (typing instanceof totah.lab.hephaestus.typing.AD4AtomTypingReport report && report.atomCount()!=count)
            issues.add(error(ValidationCode.REPORT_STATE_MISMATCH,"Typing report atom count contradicts structure.","ad4-atom-typing-report"));
    }
    private void requireReport(PreparedProtein protein,String key,List<ValidationIssue> issues) {
        if (!protein.attributes().containsKey(key)) issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                ValidationCode.MISSING_PREPARATION_REPORT,"Preparation report is missing.",key));
    }
    private ValidationIssue error(ValidationCode code,String message,String location) { return new ValidationIssue(ValidationSeverity.ERROR,code,message,location); }
}
