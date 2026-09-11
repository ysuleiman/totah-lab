package totah.lab.athena.design.backend;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic Athena-owned implementation of the bounded graph-edit vocabulary. */
public final class GraphEditTransactionEngine {
    public Result apply(MolecularGraph parent, GraphEdit edit, Authorization authorization) {
        Objects.requireNonNull(parent); Objects.requireNonNull(edit); Objects.requireNonNull(authorization);
        validateGraph(parent);
        if (!authorization.editableVectorId().equals(edit.editableVectorId())) {
            throw new IllegalArgumentException("edit vector is not authorized");
        }
        if (!authorization.allowedTypes().contains(edit.type())) {
            throw new IllegalArgumentException("transformation is not authorized: " + edit.type());
        }
        rejectProtected(edit.affectedAtomIds(), authorization.protectedAtomIds(), "atom");
        rejectProtected(edit.affectedBondIds(), authorization.protectedBondIds(), "bond");
        if (!authorization.permittedEditAtomIds().containsAll(edit.affectedAtomIds())) {
            throw new IllegalArgumentException("edit affects atoms outside permitted region");
        }
        String validatedAnchor = validateAttachmentAnchor(parent, edit, authorization);

        var atoms = new LinkedHashMap<String, MolecularGraph.Atom>();
        parent.atoms().forEach(atom -> atoms.put(atom.id(), atom));
        var bonds = new LinkedHashMap<String, MolecularGraph.Bond>();
        parent.bonds().forEach(bond -> bonds.put(bond.id(), bond));
        var addedAtoms = new LinkedHashSet<String>(); var deletedAtoms = new LinkedHashSet<String>();
        var addedBonds = new LinkedHashSet<String>(); var deletedBonds = new LinkedHashSet<String>();

        switch (edit.type()) {
            case ATOM_SUBSTITUTION -> substituteAtom(atoms, bonds, edit, deletedAtoms, deletedBonds);
            case AUTHORIZED_BOND_MODIFICATION -> modifyBond(bonds, edit);
            case SUBSTITUENT_PRUNING -> prune(atoms, bonds, edit, deletedAtoms, deletedBonds);
            case FRAGMENT_ATTACHMENT, SUBSTITUENT_GROWTH -> attach(
                    atoms, bonds, edit, addedAtoms, addedBonds);
            case SUBSTITUENT_REPLACEMENT -> {
                prune(atoms, bonds, edit, deletedAtoms, deletedBonds);
                attach(atoms, bonds, edit, addedAtoms, addedBonds);
            }
        }
        var product = new MolecularGraph(List.copyOf(atoms.values()), List.copyOf(bonds.values()), parent.properties());
        validateGraph(product);
        authorization.protectedAtomIds().forEach(id -> {
            if (!product.atom(id).equals(parent.atom(id))) throw new IllegalStateException("protected atom changed: " + id);
        });
        authorization.protectedBondIds().forEach(id -> {
            if (!product.bond(id).equals(parent.bond(id))) throw new IllegalStateException("protected bond changed: " + id);
        });
        var atomMap = unchanged(parent.atoms().stream().map(MolecularGraph.Atom::id).toList(), atoms.keySet());
        var bondMap = unchanged(parent.bonds().stream().map(MolecularGraph.Bond::id).toList(), bonds.keySet());
        var validations = new ArrayList<>(List.of(
                "AUTHORIZATION_PASS", "PROTECTED_NEIGHBORHOOD_PASS", "GRAPH_INTEGRITY_PASS"));
        if (validatedAnchor != null) {
            validations.add("ATTACHMENT_ANCHOR_AUTHORIZATION_PASS:" + validatedAnchor);
        }
        var receipt = new GraphEditReceipt(edit.editId(), edit.editableVectorId(), edit.type().name(),
                atomMap, bondMap, addedAtoms, deletedAtoms, addedBonds, deletedBonds,
                validations);
        return new Result(product, receipt);
    }

    private static void substituteAtom(Map<String, MolecularGraph.Atom> atoms,
                                       Map<String, MolecularGraph.Bond> bonds,
                                       GraphEdit edit, Set<String> deletedAtoms,
                                       Set<String> deletedBonds) {
        String configured = edit.parameters().get("substitutionAtomId");
        String id = configured == null
                ? exactlyOne(edit.affectedAtomIds(), "atom substitution requires one atom unless substitutionAtomId is supplied")
                : configured;
        if (!edit.affectedAtomIds().contains(id)) {
            throw new IllegalArgumentException("substitutionAtomId is not in affectedAtomIds");
        }
        var atom = required(atoms, id, "atom");
        if (edit.replacementElement() == null || edit.replacementElement().isBlank()) {
            throw new IllegalArgumentException("replacement element missing");
        }
        atoms.put(id, new MolecularGraph.Atom(atom.id(), edit.replacementElement(), atom.isotope(),
                atom.formalCharge(), atom.explicitHydrogens(), atom.aromatic(), atom.stereochemistry(),
                atom.coordinates(), atom.properties()));
        for (String leavingId : edit.affectedAtomIds().stream().sorted().toList()) {
            if (leavingId.equals(id)) continue;
            var leaving = required(atoms, leavingId, "leaving atom");
            if (!leaving.element().equals("H")) {
                throw new IllegalArgumentException("atom-substitution leaving atom must be explicit hydrogen: " + leavingId);
            }
            var incident = bonds.values().stream().filter(b -> b.firstAtomId().equals(leavingId)
                    || b.secondAtomId().equals(leavingId)).sorted(java.util.Comparator.comparing(MolecularGraph.Bond::id))
                    .toList();
            if (incident.size() != 1) throw new IllegalArgumentException("leaving hydrogen must be terminal: " + leavingId);
            var leavingBond = incident.getFirst();
            String neighbor = leavingBond.firstAtomId().equals(leavingId)
                    ? leavingBond.secondAtomId() : leavingBond.firstAtomId();
            if (!neighbor.equals(id)) {
                throw new IllegalArgumentException("leaving hydrogen is not bonded to substitution atom: " + leavingId);
            }
            bonds.remove(leavingBond.id()); deletedBonds.add(leavingBond.id());
            atoms.remove(leavingId); deletedAtoms.add(leavingId);
        }
    }

    private static void modifyBond(Map<String, MolecularGraph.Bond> bonds, GraphEdit edit) {
        String id = exactlyOne(edit.affectedBondIds(), "bond modification requires one bond");
        var bond = required(bonds, id, "bond");
        if (edit.replacementBondOrder() == null) throw new IllegalArgumentException("replacement bond order missing");
        bonds.put(id, new MolecularGraph.Bond(bond.id(), bond.firstAtomId(), bond.secondAtomId(),
                edit.replacementBondOrder(), edit.replacementBondOrder() == MolecularGraph.BondOrder.AROMATIC,
                bond.stereochemistry(), bond.properties()));
    }

    private static void prune(Map<String, MolecularGraph.Atom> atoms, Map<String, MolecularGraph.Bond> bonds,
                              GraphEdit edit, Set<String> deletedAtoms, Set<String> deletedBonds) {
        if (edit.affectedAtomIds().isEmpty()) throw new IllegalArgumentException("prune region is empty");
        for (String id : edit.affectedAtomIds()) {
            if (atoms.remove(id) == null) throw new IllegalArgumentException("atom missing: " + id);
            deletedAtoms.add(id);
        }
        var incident = bonds.values().stream().filter(bond -> deletedAtoms.contains(bond.firstAtomId())
                || deletedAtoms.contains(bond.secondAtomId())).map(MolecularGraph.Bond::id).toList();
        incident.forEach(id -> { bonds.remove(id); deletedBonds.add(id); });
    }

    private static void attach(Map<String, MolecularGraph.Atom> atoms, Map<String, MolecularGraph.Bond> bonds,
                               GraphEdit edit, Set<String> addedAtoms, Set<String> addedBonds) {
        if (edit.anchorAtomId() == null || !atoms.containsKey(edit.anchorAtomId())) {
            throw new IllegalArgumentException("attachment anchor missing");
        }
        if (edit.fragment() == null || edit.fragment().atoms().isEmpty()) {
            throw new IllegalArgumentException("attachment fragment missing");
        }
        String fragmentAnchor = edit.parameters().get("fragmentAnchorAtomId");
        if (fragmentAnchor == null || edit.fragment().atom(fragmentAnchor).isEmpty()) {
            throw new IllegalArgumentException("fragmentAnchorAtomId missing or invalid");
        }
        for (var atom : edit.fragment().atoms()) {
            if (atoms.putIfAbsent(atom.id(), atom) != null) throw new IllegalArgumentException("duplicate atom id: " + atom.id());
            addedAtoms.add(atom.id());
        }
        for (var bond : edit.fragment().bonds()) {
            if (bonds.putIfAbsent(bond.id(), bond) != null) throw new IllegalArgumentException("duplicate bond id: " + bond.id());
            addedBonds.add(bond.id());
        }
        String bondId = edit.parameters().getOrDefault("attachmentBondId", edit.editId() + ":attachment");
        if (bonds.containsKey(bondId)) throw new IllegalArgumentException("duplicate attachment bond id");
        var order = edit.replacementBondOrder() == null ? MolecularGraph.BondOrder.SINGLE : edit.replacementBondOrder();
        bonds.put(bondId, new MolecularGraph.Bond(bondId, edit.anchorAtomId(), fragmentAnchor,
                order, order == MolecularGraph.BondOrder.AROMATIC, "UNSPECIFIED", Map.of()));
        addedBonds.add(bondId);
    }

    private static void validateGraph(MolecularGraph graph) {
        var atomIds = new HashSet<String>();
        graph.atoms().forEach(atom -> { if (!atomIds.add(atom.id())) throw new IllegalArgumentException("duplicate atom: " + atom.id()); });
        var bondIds = new HashSet<String>();
        graph.bonds().forEach(bond -> {
            if (!bondIds.add(bond.id())) throw new IllegalArgumentException("duplicate bond: " + bond.id());
            if (!atomIds.contains(bond.firstAtomId()) || !atomIds.contains(bond.secondAtomId())) {
                throw new IllegalArgumentException("bond endpoint missing: " + bond.id());
            }
        });
    }

    private static void rejectProtected(Set<String> affected, Set<String> protectedIds, String label) {
        var overlap = new HashSet<>(affected); overlap.retainAll(protectedIds);
        if (!overlap.isEmpty()) throw new IllegalArgumentException("protected " + label + " affected: " + overlap);
    }
    private static String validateAttachmentAnchor(MolecularGraph parent, GraphEdit edit,
                                                   Authorization authorization) {
        if (edit.type() != GraphEdit.Type.FRAGMENT_ATTACHMENT
                && edit.type() != GraphEdit.Type.SUBSTITUENT_GROWTH
                && edit.type() != GraphEdit.Type.SUBSTITUENT_REPLACEMENT) {
            return null;
        }
        String anchor = edit.anchorAtomId();
        if (anchor == null || parent.atom(anchor).isEmpty()) {
            throw new IllegalArgumentException("attachment anchor missing: " + anchor);
        }
        if (!authorization.permittedEditAtomIds().contains(anchor)) {
            throw new IllegalArgumentException("attachment anchor outside permitted region: " + anchor);
        }
        if (authorization.protectedAtomIds().contains(anchor)) {
            throw new IllegalArgumentException("attachment anchor is protected: " + anchor);
        }
        return anchor;
    }
    private static String exactlyOne(Set<String> values, String error) {
        if (values.size() != 1) throw new IllegalArgumentException(error); return values.iterator().next();
    }
    private static <T> T required(Map<String, T> values, String id, String label) {
        T value = values.get(id); if (value == null) throw new IllegalArgumentException(label + " missing: " + id); return value;
    }
    private static Map<String, String> unchanged(List<String> parent, Set<String> product) {
        var result = new LinkedHashMap<String, String>(); parent.stream().filter(product::contains).forEach(id -> result.put(id, id)); return result;
    }

    public record Authorization(String editableVectorId, Set<GraphEdit.Type> allowedTypes,
                                Set<String> permittedEditAtomIds, Set<String> protectedAtomIds,
                                Set<String> protectedBondIds) {
        public Authorization {
            allowedTypes = Set.copyOf(allowedTypes); permittedEditAtomIds = Set.copyOf(permittedEditAtomIds);
            protectedAtomIds = Set.copyOf(protectedAtomIds); protectedBondIds = Set.copyOf(protectedBondIds);
        }
    }
    public record Result(MolecularGraph product, GraphEditReceipt receipt) { }
}
