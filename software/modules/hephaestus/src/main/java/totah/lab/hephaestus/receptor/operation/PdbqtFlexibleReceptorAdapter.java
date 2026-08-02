package totah.lab.hephaestus.receptor.operation;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.hephaestus.flexibility.AtomReference;
import totah.lab.hephaestus.flexibility.FlexibilityModel;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.validation.ValidationException;
import totah.lab.hephaestus.validation.ValidationReport;
import totah.lab.hephaestus.validation.internal.CanonicalAtomRecord;
import totah.lab.hephaestus.validation.internal.CanonicalAtomResolver;
import totah.lab.hermes.file.writer.pdbqt.PdbqtAtomInput;
import totah.lab.hermes.file.writer.pdbqt.PdbqtFlexibleReceptorInput;
import totah.lab.hermes.file.writer.pdbqt.PdbqtFlexibleResidueInput;
import totah.lab.hermes.file.writer.pdbqt.PdbqtFragmentInput;
import totah.lab.hermes.file.writer.pdbqt.PdbqtRigidAtomInput;
import totah.lab.hermes.file.writer.pdbqt.PdbqtRotatableBondInput;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class PdbqtFlexibleReceptorAdapter {
    PdbqtFlexibleReceptorInput adapt(
            PreparedProtein preparedProtein,
            FlexibilityModel flexibilityModel) {
        CanonicalAtomResolver resolver = new CanonicalAtomResolver(
                preparedProtein.protein().structure());
        List<CanonicalAtomRecord> canonical = resolver.atoms();
        Set<Integer> flexibleIndices = new HashSet<>();
        List<PdbqtFlexibleResidueInput> flexibleResidues = new ArrayList<>();

        for (var flexible : flexibilityModel.flexibleResidues()) {
            List<PdbqtFragmentInput> fragments = new ArrayList<>();
            for (var fragment : flexible.fragments()) {
                List<PdbqtAtomInput> atoms = fragment.atoms().stream()
                        .map(reference -> resolve(reference, resolver, flexibleIndices))
                        .map(this::input).toList();
                validateReference(fragment.anchor(), resolver);
                fragments.add(new PdbqtFragmentInput(
                        fragment.id(), atoms, fragment.anchor().atomIndex(), fragment.parentFragmentId()));
            }
            validateReference(flexible.anchorAtom(), resolver);
            CanonicalAtomRecord residueAtom = canonical.get(flexible.anchorAtom().atomIndex());
            ResidueId identity = flexible.residue();
            if (!identity.equals(residueAtom.reference().residue()))
                throw new IllegalArgumentException("Flexible residue identity does not match its anchor.");
            List<PdbqtRotatableBondInput> bonds = flexible.rotatableBonds().stream().map(bond -> {
                validateReference(bond.parentAtom(), resolver);
                validateReference(bond.childAtom(), resolver);
                return new PdbqtRotatableBondInput(
                        bond.parentAtom().atomIndex(), bond.childAtom().atomIndex(),
                        bond.parentFragmentId(), bond.childFragmentId());
            }).toList();
            flexibleResidues.add(new PdbqtFlexibleResidueInput(
                    residueAtom.residueName(), identity.chainId(), identity.residueNumber(),
                    identity.insertionCode(), flexible.anchorAtom().atomIndex(), fragments, bonds));
        }

        List<PdbqtRigidAtomInput> rigid = canonical.stream()
                .filter(atom -> !flexibleIndices.contains(atom.reference().atomIndex()))
                .map(this::input).map(PdbqtRigidAtomInput::new).toList();
        if (rigid.size() + flexibleIndices.size() != canonical.size())
            throw new IllegalArgumentException("Rigid/flexible partition is incomplete.");
        return new PdbqtFlexibleReceptorInput(rigid, flexibleResidues, canonical.size());
    }

    private CanonicalAtomRecord resolve(
            AtomReference reference, CanonicalAtomResolver resolver, Set<Integer> used) {
        CanonicalAtomRecord atom = validateReference(reference, resolver);
        if (!used.add(reference.atomIndex()))
            throw new IllegalArgumentException("Duplicate flexible atom reference: " + reference);
        return atom;
    }

    private CanonicalAtomRecord validateReference(
            AtomReference reference, CanonicalAtomResolver resolver) {
        var resolution = resolver.resolve(reference);
        if (resolution.issue()!=null)
            throw new ValidationException(new ValidationReport(List.of(resolution.issue())));
        return resolution.atom();
    }

    private PdbqtAtomInput input(CanonicalAtomRecord resolved) {
        Atom atom = resolved.atom();
        ResidueId residue = resolved.reference().residue();
        return new PdbqtAtomInput(
                resolved.reference().atomIndex(), resolved.reference().atomIndex() + 1,
                atom.getName(), resolved.residueName(), residue.chainId(),
                residue.residueNumber(), residue.insertionCode(), atom.getPosition(),
                atom.getOccupancy(), atom.getBFactor(), atom.getCharge(), atom.getAutoDockType());
    }
}
