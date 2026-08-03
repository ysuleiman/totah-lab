package totah.lab.proteus.protein.mutation;

import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.proteus.protein.mutation.geometry.SideChainTemplate;
import totah.lab.proteus.protein.mutation.geometry.SideChainTemplateLibrary;
import totah.lab.proteus.protein.mutation.rotamer.RotamerLibrary;
import totah.lab.proteus.protein.mutation.rotamer.RotamerSelector;
import totah.lab.proteus.protein.variant.ProteinVariant;
import totah.lab.proteus.protein.variant.VariantProvenance;
import totah.lab.proteus.validation.ValidationCode;
import totah.lab.proteus.validation.ValidationIssue;
import totah.lab.proteus.validation.ValidationReport;
import totah.lab.proteus.validation.ValidationSeverity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ProteinMutationOperation {
    private static final String ROTAMER_METHOD =
            "RotamerSelector least-clash over stub RotamerLibrary (3 fixed chi1 candidates)";
    private static final String SOFTWARE_VERSION = "1.0-SNAPSHOT";

    private final MutationValidator validator = new MutationValidator();
    private final SideChainTemplateLibrary templates = new SideChainTemplateLibrary();
    private final RotamerLibrary rotamers = new RotamerLibrary();
    private final RotamerSelector selector = new RotamerSelector();

    public MutationResult apply(Structure source, Mutation mutation,
                                MutationContext context) {
        ValidationReport validation = validator.validate(source, mutation, context);
        if (validation.hasErrors()) return new MutationResult(source, Optional.empty(), validation);
        Residue original = source.residue(mutation.target());
        SideChainTemplate template = templates.find(mutation.replacementResidueName()).orElse(null);
        if (template == null) {
            var issues = new ArrayList<>(validation.issues());
            issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                    ValidationCode.MUTATION_TEMPLATE_MISSING,
                    "No side-chain template exists for " + mutation.replacementResidueName() + ".",
                    mutation.target().toString()));
            return new MutationResult(source, Optional.empty(), new ValidationReport(issues));
        }
        // The library does not use the backbone conformation yet; none is known here.
        var candidates = rotamers.rotamers(template.residueName(), null);
        var selection = selector.select(source, mutation.target(), original, template, candidates);
        Residue replacement = new Residue(template.residueName(), original.getNumber(),
                original.getInsertionCode(), original.getClassificationEvidence(),
                mergedAtoms(original, selection.atoms()));
        Structure changed = rebuild(source, mutation, replacement, template);
        AppliedMutation applied = new AppliedMutation(mutation.target(), original.getName(),
                replacement.getName(), selection.rotamer().id(), selection.score());
        return new MutationResult(changed, Optional.of(applied), validation);
    }

    /**
     * Applies every {@link Mutation} of the request's {@link MutationSet}
     * sequentially, in list order, each to the result of the previous one.
     * Every mutation is validated with {@link MutationValidator}; a mutation
     * that fails validation aborts the whole request with an
     * {@link IllegalStateException} carrying the failed report.
     *
     * <p>Deterministic: rotamer candidates are evaluated in fixed library
     * order and the first candidate with the strictly lowest steric score
     * wins, so identical inputs yield identical atom positions.
     */
    public ProteinVariant apply(MutationRequest request) {
        Objects.requireNonNull(request, "request");
        Structure current = request.parent();
        var applied = new ArrayList<AppliedMutation>();
        var warnings = new ArrayList<String>();
        for (Mutation mutation : request.mutationSet().mutations()) {
            MutationResult result = apply(current, mutation, request.context());
            result.validation().issues().stream()
                    .filter(issue -> issue.severity() == ValidationSeverity.WARNING)
                    .forEach(issue -> warnings.add(issue.code() + " at "
                            + issue.location() + ": " + issue.message()));
            if (result.appliedMutation().isEmpty()) {
                throw new IllegalStateException("Mutation " + mutation
                        + " failed validation: " + result.validation().issues());
            }
            applied.add(result.appliedMutation().get());
            current = result.structure();
        }
        VariantProvenance provenance = new VariantProvenance(
                request.mutationSet().parentTarget(), applied,
                ROTAMER_METHOD, SOFTWARE_VERSION, Instant.now(), warnings);
        return new ProteinVariant(request.mutationSet().id(),
                request.mutationSet().parentTarget(), request.mutationSet(),
                current, provenance);
    }

    private List<totah.lab.gaia.structure.Atom> mergedAtoms(
            Residue original, List<totah.lab.gaia.structure.Atom> sideChain) {
        var atoms = new ArrayList<totah.lab.gaia.structure.Atom>();
        original.getAtoms().stream().filter(atom -> isBackbone(atom.getName())).forEach(atoms::add);
        atoms.addAll(sideChain);
        return List.copyOf(atoms);
    }

    private Structure rebuild(Structure source, Mutation mutation, Residue replacement,
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

    private boolean sameTarget(Residue residue, Mutation mutation) {
        return residue.getNumber() == mutation.target().residueNumber()
                && java.util.Objects.equals(residue.getInsertionCode(), mutation.target().insertionCode());
    }

    private boolean removedSideChainEndpoint(AtomReference reference, Mutation mutation) {
        return reference.chainId().equals(mutation.target().chainId())
                && reference.residueNumber() == mutation.target().residueNumber()
                && reference.insertionCode() == (mutation.target().insertionCode() == null
                ? ' ' : mutation.target().insertionCode())
                && !isBackbone(reference.atomName());
    }

    private AtomReference reference(Mutation mutation, String atomName) {
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
