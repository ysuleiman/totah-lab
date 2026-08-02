package totah.lab.hephaestus.validation;

import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.topology.ProteinTopology;
import totah.lab.hephaestus.validation.internal.CanonicalAtomResolver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PdbqtExportValidator implements PreparationValidator<PreparedProtein> {
    private final PreparedProteinValidator preparedValidator = new PreparedProteinValidator();

    @Override
    public ValidationReport validate(PreparedProtein protein) {
        List<ValidationIssue> issues = new ArrayList<>(preparedValidator.validate(protein).issues());
        if (protein == null || protein.protein() == null || protein.protein().structure() == null)
            return new ValidationReport(issues);
        CanonicalAtomResolver resolver = new CanonicalAtomResolver(protein.protein().structure());
        if (protein.flexibility()!=null && !protein.flexibility().isEmpty()) {
            Set<Integer> flexible = new HashSet<>();
            protein.flexibility().flexibleResidues().forEach(residue -> residue.fragments().forEach(fragment ->
                    fragment.atoms().forEach(reference -> {
                        var resolution=resolver.resolve(reference);
                        if (resolution.issue()!=null) issues.add(resolution.issue());
                        else if (!flexible.add(reference.atomIndex())) issues.add(error(
                                ValidationCode.FLEXIBILITY_ATOM_DUPLICATE,"Flexible atom is assigned more than once.",reference.toString()));
                    })));
            if (flexible.size()>resolver.atoms().size()) issues.add(error(
                    ValidationCode.FLEXIBILITY_PARTITION_INCOMPLETE,"Flexible partition exceeds prepared atom count.","flexibility"));
        }
        return new ValidationReport(deduplicate(issues));
    }

    private List<ValidationIssue> deduplicate(List<ValidationIssue> issues) {
        return new ArrayList<>(new java.util.LinkedHashSet<>(issues));
    }
    private ValidationIssue error(ValidationCode code,String message,String location) {
        return new ValidationIssue(ValidationSeverity.ERROR,code,message,location);
    }
}
