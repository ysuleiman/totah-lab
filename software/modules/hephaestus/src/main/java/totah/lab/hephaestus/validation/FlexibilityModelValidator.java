package totah.lab.hephaestus.validation;

import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.flexibility.FlexibilityModel;
import totah.lab.hephaestus.flexibility.RigidFragment;
import totah.lab.hephaestus.topology.ProteinTopology;
import totah.lab.hephaestus.validation.internal.CanonicalAtomResolver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FlexibilityModelValidator {
    public ValidationReport validate(
            Structure structure,
            ProteinTopology topology,
            FlexibilityModel flexibilityModel) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (structure == null || flexibilityModel == null) {
            issues.add(error(ValidationCode.NULL_VALUE, "Structure and flexibility model are required.", "flexibility"));
            return new ValidationReport(issues);
        }
        CanonicalAtomResolver resolver = new CanonicalAtomResolver(structure);
        if (topology == null) issues.add(error(ValidationCode.MISSING_TOPOLOGY, "Topology is required.", "topology"));
        else if (topology.atomCount() != resolver.atoms().size())
            issues.add(error(ValidationCode.TOPOLOGY_ATOM_COUNT_MISMATCH, "Topology atom count differs from canonical structure count.", "topology"));

        Set<Object> residueIds = new HashSet<>();
        Set<Integer> flexibleAtoms = new HashSet<>();
        for (var flexible : flexibilityModel.flexibleResidues()) {
            String location = flexible.residue().toString();
            if (!residueIds.add(flexible.residue()))
                issues.add(error(ValidationCode.FLEXIBILITY_RESIDUE_MISSING, "Flexible residue occurs more than once.", location));
            resolve(flexible.anchorAtom(), resolver, issues);
            if (!flexible.anchorAtom().residue().equals(flexible.residue()))
                issues.add(error(ValidationCode.FLEXIBILITY_ANCHOR_INVALID, "Anchor belongs to a different residue.", location));

            Map<String, RigidFragment> fragments = new HashMap<>();
            for (RigidFragment fragment : flexible.fragments()) {
                if (fragments.put(fragment.id(), fragment) != null)
                    issues.add(error(ValidationCode.FLEXIBILITY_FRAGMENT_INVALID, "Duplicate fragment ID.", fragment.id()));
                if (!fragment.atoms().contains(fragment.anchor()))
                    issues.add(error(ValidationCode.FLEXIBILITY_ANCHOR_INVALID, "Fragment does not contain its anchor.", fragment.id()));
                for (var reference : fragment.atoms()) {
                    resolve(reference, resolver, issues);
                    if (!reference.residue().equals(flexible.residue()))
                        issues.add(error(ValidationCode.FLEXIBILITY_FRAGMENT_INVALID, "Fragment atom belongs to another residue.", fragment.id()));
                    if (!flexibleAtoms.add(reference.atomIndex()))
                        issues.add(error(ValidationCode.FLEXIBILITY_ATOM_DUPLICATE, "Flexible atom occurs more than once.", reference.toString()));
                }
            }
            Map<String, List<String>> children = new HashMap<>();
            Set<String> childIds = new HashSet<>();
            for (var bond : flexible.rotatableBonds()) {
                resolve(bond.parentAtom(), resolver, issues); resolve(bond.childAtom(), resolver, issues);
                if (!fragments.containsKey(bond.parentFragmentId()) || !fragments.containsKey(bond.childFragmentId()))
                    issues.add(error(ValidationCode.FLEXIBILITY_BOND_INVALID, "Bond references an unknown fragment.", location));
                if (!childIds.add(bond.childFragmentId()))
                    issues.add(error(ValidationCode.FLEXIBILITY_GRAPH_CYCLIC, "Fragment has more than one parent.", bond.childFragmentId()));
                children.computeIfAbsent(bond.parentFragmentId(), ignored -> new ArrayList<>()).add(bond.childFragmentId());
            }
            String root = flexible.fragments().stream()
                    .filter(f -> f.atoms().contains(flexible.anchorAtom())).map(RigidFragment::id)
                    .findFirst().orElse(null);
            if (root == null) issues.add(error(ValidationCode.FLEXIBILITY_ANCHOR_INVALID, "No fragment contains the residue anchor.", location));
            else validateGraph(root, fragments.keySet(), children, issues);
        }
        return new ValidationReport(issues);
    }

    private void validateGraph(String root, Set<String> fragments, Map<String,List<String>> children,
            List<ValidationIssue> issues) {
        Set<String> visited = new HashSet<>(); Set<String> active = new HashSet<>();
        ArrayDeque<Node> stack = new ArrayDeque<>(); stack.push(new Node(root, false));
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (node.exit()) { active.remove(node.id()); continue; }
            if (!active.add(node.id())) { issues.add(error(ValidationCode.FLEXIBILITY_GRAPH_CYCLIC, "Fragment graph contains a cycle.", node.id())); continue; }
            if (!visited.add(node.id())) { active.remove(node.id()); continue; }
            stack.push(new Node(node.id(), true));
            for (String child : children.getOrDefault(node.id(), List.of())) stack.push(new Node(child, false));
        }
        if (!visited.containsAll(fragments))
            issues.add(error(ValidationCode.FLEXIBILITY_GRAPH_DISCONNECTED, "Fragment graph is disconnected.", root));
    }
    private void resolve(totah.lab.hephaestus.flexibility.AtomReference reference,
            CanonicalAtomResolver resolver, List<ValidationIssue> issues) {
        var resolution = resolver.resolve(reference); if (resolution.issue() != null) issues.add(resolution.issue());
    }
    private ValidationIssue error(ValidationCode code,String message,String location) {
        return new ValidationIssue(ValidationSeverity.ERROR,code,message,location);
    }
    private record Node(String id, boolean exit) {}
}
