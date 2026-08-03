package totah.lab.hephaestus.mutation;

import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.mutation.geometry.SideChainTemplate;
import totah.lab.hephaestus.mutation.geometry.SideChainTemplateLibrary;
import totah.lab.hephaestus.mutation.rotamer.BackboneConformation;
import totah.lab.hephaestus.mutation.rotamer.LocalRotamerOptimizer;
import totah.lab.hephaestus.mutation.rotamer.RotamerLibrary;
import totah.lab.hephaestus.validation.ValidationCode;
import totah.lab.hephaestus.validation.ValidationIssue;
import totah.lab.hephaestus.validation.ValidationReport;
import totah.lab.hephaestus.validation.ValidationSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MutationOperation {
    private final MutationValidator validator = new MutationValidator();
    private final SideChainTemplateLibrary templates = new SideChainTemplateLibrary();
    private final RotamerLibrary rotamers = new RotamerLibrary();
    private final LocalRotamerOptimizer optimizer = new LocalRotamerOptimizer();

    public MutationOperationResult apply(Structure source, ResidueMutation mutation,
                                         MutationContext context) {
        ValidationReport validation = validator.validate(source, mutation, context);
        if (validation.hasErrors()) return new MutationOperationResult(source, Optional.empty(), validation);
        Residue original = source.residue(mutation.target());
        SideChainTemplate template = templates.find(mutation.replacementResidueName()).orElse(null);
        if (template == null) {
            var issues = new ArrayList<>(validation.issues());
            issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                    ValidationCode.MUTATION_TEMPLATE_MISSING,
                    "No side-chain template exists for " + mutation.replacementResidueName() + ".",
                    mutation.target().toString()));
            return new MutationOperationResult(source, Optional.empty(), new ValidationReport(issues));
        }
        // The library does not use the backbone conformation yet; none is known here.
        var candidates = rotamers.rotamers(template.residueName(), null);
        var selection = optimizer.select(source, mutation.target(), original, template, candidates);
        Residue replacement = new Residue(template.residueName(), original.getNumber(),
                original.getInsertionCode(), original.getClassificationEvidence(),
                mergedAtoms(original, selection.atoms()));
        Structure changed = rebuild(source, mutation, replacement, template);
        AppliedMutation applied = new AppliedMutation(mutation.target(), original.getName(),
                replacement.getName(), selection.rotamer().id(), selection.score());
        return new MutationOperationResult(changed, Optional.of(applied), validation);
    }

    private List<totah.lab.gaia.structure.Atom> mergedAtoms(
            Residue original, List<totah.lab.gaia.structure.Atom> sideChain) {
        var atoms = new ArrayList<totah.lab.gaia.structure.Atom>();
        original.getAtoms().stream().filter(atom -> isBackbone(atom.getName())).forEach(atoms::add);
        atoms.addAll(sideChain);
        return List.copyOf(atoms);
    }

    private Structure rebuild(Structure source, ResidueMutation mutation, Residue replacement,
                              SideChainTemplate template) {
        List<Chain> chains = source.getChains().stream().map(chain -> {
            if (!chain.id().equals(mutation.target().chainId())) return chain;
            return new Chain(chain.id(), chain.residues().stream()
                    .map(residue -> sameTarget(residue, mutation) ? replacement : residue).toList());
        }).toList();
        List<Bond> bonds = source.bonds().stream()
                .filter(bond -> !removedSideChainEndpoint(bond.atom1(), mutation)
                        && !removedSideChainEndpoint(bond.atom2(), mutation))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for (var bond : template.bonds()) {
            bonds.add(new Bond(reference(mutation, bond.atom1()), reference(mutation, bond.atom2()),
                    bond.order()));
        }
        return new Structure(chains, bonds, source.getConnectivityMetadata());
    }

    private boolean sameTarget(Residue residue, ResidueMutation mutation) {
        return residue.getNumber() == mutation.target().residueNumber()
                && java.util.Objects.equals(residue.getInsertionCode(), mutation.target().insertionCode());
    }

    private boolean removedSideChainEndpoint(AtomReference reference, ResidueMutation mutation) {
        return reference.chainId().equals(mutation.target().chainId())
                && reference.residueNumber() == mutation.target().residueNumber()
                && reference.insertionCode() == (mutation.target().insertionCode() == null
                ? ' ' : mutation.target().insertionCode())
                && !isBackbone(reference.atomName());
    }

    private AtomReference reference(ResidueMutation mutation, String atomName) {
        return new AtomReference(mutation.target().chainId(), mutation.target().residueNumber(),
                mutation.target().insertionCode() == null ? ' ' : mutation.target().insertionCode(), atomName);
    }

    private boolean isBackbone(String atomName) {
        return switch (atomName) {
            case "N", "CA", "C", "O", "OXT", "H", "HA", "HA2", "HA3" -> true;
            default -> false;
        };
    }
}
