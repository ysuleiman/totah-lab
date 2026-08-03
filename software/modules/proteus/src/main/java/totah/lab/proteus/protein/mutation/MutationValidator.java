package totah.lab.proteus.protein.mutation;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.proteus.validation.ValidationCode;
import totah.lab.proteus.validation.ValidationIssue;
import totah.lab.proteus.validation.ValidationReport;
import totah.lab.proteus.validation.ValidationSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MutationValidator {

    public ValidationReport validate(
            Structure structure,
            Mutation mutation,
            MutationContext context) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(context, "context");
        List<ValidationIssue> issues = new ArrayList<>();
        Residue residue = structure.findResidue(mutation.target()).orElse(null);
        if (residue == null) {
            issues.add(issue(ValidationSeverity.ERROR,
                    ValidationCode.MUTATION_TARGET_MISSING,
                    "Mutation target residue does not exist.", mutation.target().toString()));
            return new ValidationReport(issues);
        }
        if (!residue.getName().equalsIgnoreCase(mutation.expectedResidueName())) {
            issues.add(issue(ValidationSeverity.ERROR,
                    ValidationCode.MUTATION_WILD_TYPE_MISMATCH,
                    "Structural residue does not match requested wild type.", mutation.target().toString()));
        }
        if (residue.findAtom("N").isEmpty()
                || residue.findAtom("CA").isEmpty()
                || residue.findAtom("C").isEmpty()) {
            issues.add(issue(ValidationSeverity.ERROR,
                    ValidationCode.MUTATION_BACKBONE_INCOMPLETE,
                    "N, CA, and C backbone atoms are required for fixed-backbone mutation.",
                    mutation.target().toString()));
        }
        if (residue.getAtoms().stream().anyMatch(atom ->
                atom.getAlternateLocationProvenance().alternativesPresent())) {
            issues.add(issue(ValidationSeverity.WARNING,
                    ValidationCode.MUTATION_ALT_LOC_PRESENT,
                    "Mutation target originated from alternate conformations.",
                    mutation.target().toString()));
        }
        validateCovalentTopology(structure, residue, mutation, context, issues);
        return new ValidationReport(issues);
    }

    private void validateCovalentTopology(
            Structure structure,
            Residue residue,
            Mutation mutation,
            MutationContext context,
            List<ValidationIssue> issues) {
        List<AtomReference> sideChainAtoms = residue.getAtoms().stream()
                .filter(atom -> !isBackboneAtom(atom.getName()))
                .map(atom -> reference(mutation, atom.getName()))
                .toList();
        boolean explicitExternalBond = sideChainAtoms.stream()
                .flatMap(reference -> structure.bondsFor(reference).stream())
                .anyMatch(bond -> !sameResidue(bond.atom1(), mutation)
                        || !sameResidue(bond.atom2(), mutation));
        if (explicitExternalBond && !context.allowExplicitBondBreaking()) {
            issues.add(issue(ValidationSeverity.ERROR,
                    ValidationCode.MUTATION_EXPLICIT_COVALENT_BOND,
                    "A side-chain atom has explicit external covalent connectivity; bond breaking was not authorized.",
                    mutation.target().toString()));
            return;
        }
        ConnectivityProvenance provenance =
                structure.getConnectivityMetadata().provenance();
        if (provenance == ConnectivityProvenance.ABSENT
                || provenance == ConnectivityProvenance.PARTIAL) {
            boolean plausibleContact = plausibleCovalentContact(structure, residue, mutation);
            ValidationSeverity severity = context.ambiguousTopologyPolicy()
                    == AmbiguousCovalentTopologyPolicy.FAIL
                    ? ValidationSeverity.ERROR
                    : ValidationSeverity.WARNING;
            String detail = plausibleContact
                    ? " Geometry contains a plausible external side-chain covalent contact."
                    : " No explicit topology was available to establish that the replaced side chain is externally unbonded.";
            issues.add(issue(severity,
                    ValidationCode.MUTATION_AMBIGUOUS_COVALENT_TOPOLOGY,
                    "Side-chain connectivity is ambiguous." + detail,
                    mutation.target().toString()));
        }
    }

    private boolean plausibleCovalentContact(
            Structure structure,
            Residue targetResidue,
            Mutation mutation) {
        List<Atom> sideChainAtoms = targetResidue.getAtoms().stream()
                .filter(atom -> !atom.isHydrogen())
                .filter(atom -> !isBackboneAtom(atom.getName()))
                .toList();
        List<Atom> externalAtoms = structure.getChains().stream()
                .flatMap(chain -> chain.residues().stream()
                        .filter(residue -> !(chain.id().equals(mutation.target().chainId())
                                && residue.getNumber() == mutation.target().residueNumber()
                                && Objects.equals(residue.getInsertionCode(),
                                mutation.target().insertionCode())))
                        .flatMap(residue -> residue.getAtoms().stream()))
                .filter(atom -> !atom.isHydrogen())
                .toList();
        return sideChainAtoms.stream().anyMatch(sideChain ->
                externalAtoms.stream().anyMatch(external ->
                        distance(sideChain.getPosition(), external.getPosition())
                                <= covalentRadius(sideChain) + covalentRadius(external) + 0.30));
    }

    private AtomReference reference(Mutation mutation, String atomName) {
        return new AtomReference(
                mutation.target().chainId(), mutation.target().residueNumber(),
                mutation.target().insertionCode() == null
                        ? ' '
                        : mutation.target().insertionCode(), atomName);
    }

    private boolean sameResidue(AtomReference reference, Mutation mutation) {
        return reference.chainId().equals(mutation.target().chainId())
                && reference.residueNumber() == mutation.target().residueNumber()
                && reference.insertionCode() == (mutation.target().insertionCode() == null
                ? ' '
                : mutation.target().insertionCode());
    }

    private boolean isBackboneAtom(String atomName) {
        return switch (atomName) {
            case "N", "CA", "C", "O", "OXT", "H", "HA", "HA2", "HA3" -> true;
            default -> false;
        };
    }

    private double covalentRadius(Atom atom) {
        if (atom.getElement() == null) return 0.77;
        return switch (atom.getElement()) {
            case H -> 0.31;
            case C -> 0.76;
            case N -> 0.71;
            case O -> 0.66;
            case S -> 1.05;
            case P -> 1.07;
            default -> 0.85;
        };
    }

    private double distance(Point3D first, Point3D second) {
        double x = first.x() - second.x();
        double y = first.y() - second.y();
        double z = first.z() - second.z();
        return Math.sqrt(x * x + y * y + z * z);
    }

    private ValidationIssue issue(
            ValidationSeverity severity,
            ValidationCode code,
            String message,
            String location) {
        return new ValidationIssue(severity, code, message, location);
    }
}
