package totah.lab.hephaestus.flexibility;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.topology.ProteinTopology;
import totah.lab.hephaestus.protein.flexibility.StandardResidueChiBonds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FlexibilityModelBuilder {
    private static final Set<String> BACKBONE = Set.of("N", "C", "O", "OXT");

    public FlexibilityModel build(
            Structure structure,
            ProteinTopology topology,
            FlexibilityPreparationConfig config) {
        if (topology.atomCount() != structure.getAtomCount()) {
            throw new FlexibilityValidationException("Topology atom count does not match structure.");
        }
        if (config == null || config.flexibleResidues().isEmpty()) return FlexibilityModel.empty();

        Map<ResidueId, LocatedResidue> located = locate(structure);
        List<FlexibleResidue> result = new ArrayList<>();
        for (ResidueId requested : config.flexibleResidues()) {
            LocatedResidue selected = located.get(requested);
            if (selected == null) {
                throw new FlexibilityValidationException("Flexible residue does not exist: " + requested);
            }
            validateSelection(selected, config);
            result.add(buildResidue(selected, topology, config));
        }
        result.sort(java.util.Comparator.comparingInt(
                residue -> residue.anchorAtom().atomIndex()));
        return new FlexibilityModel(result);
    }

    private FlexibleResidue buildResidue(
            LocatedResidue selected,
            ProteinTopology topology,
            FlexibilityPreparationConfig config) {
        Residue residue = selected.residue();
        List<Atom> atoms = residue.getAtoms();
        int ca = indexOf(atoms, "CA");
        if (ca < 0) throw new FlexibilityValidationException("Selected residue has no CA anchor: " + selected.reference());

        List<List<Integer>> neighbors = localNeighbors(selected, topology);
        Set<Long> cuts = new HashSet<>();
        List<int[]> activeBonds = new ArrayList<>();
        for (StandardResidueChiBonds.ChiBond names : StandardResidueChiBonds.bondsFor(residue.getName())) {
            int parent = indexOf(atoms, names.parentAtomName());
            int child = indexOf(atoms, names.childAtomName());
            if (parent < 0 || child < 0 || !neighbors.get(parent).contains(child)) {
                throw new FlexibilityValidationException("Required rotatable bond "
                        + names.parentAtomName() + "-" + names.childAtomName()
                        + " is missing in " + selected.reference());
            }
            cuts.add(edge(parent, child));
            activeBonds.add(new int[]{parent, child});
        }

        Set<Integer> included = new HashSet<>();
        for (int i = 0; i < atoms.size(); i++) {
            if (config.includeBackbone() || !BACKBONE.contains(atoms.get(i).getName())) included.add(i);
        }
        List<Set<Integer>> components = components(included, neighbors, cuts);
        Map<Integer, String> fragmentByAtom = new HashMap<>();
        List<RigidFragment> fragments = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            Set<Integer> component = components.get(i);
            String id = selected.reference().chainId() + ":"
                    + selected.reference().residueNumber() + ":fragment-" + i;
            for (int atom : component) fragmentByAtom.put(atom, id);
            int anchor = component.contains(ca) ? ca : component.stream().min(Integer::compareTo).orElseThrow();
            String parentId = parentFragment(component, activeBonds, fragmentByAtom);
            fragments.add(new RigidFragment(id,
                    component.stream().sorted().map(index -> reference(selected, index)).toList(),
                    reference(selected, anchor), parentId));
        }

        List<RotatableBond> bonds = new ArrayList<>();
        for (int[] bond : activeBonds) {
            String parent = fragmentByAtom.get(bond[0]);
            String child = fragmentByAtom.get(bond[1]);
            if (parent == null || child == null || parent.equals(child)) {
                throw new FlexibilityValidationException("Invalid fragment partition for " + selected.reference());
            }
            bonds.add(new RotatableBond(reference(selected, bond[0]), reference(selected, bond[1]), parent, child));
        }
        validateAcyclic(fragments, bonds);
        return new FlexibleResidue(selected.reference(), reference(selected, ca), fragments, bonds);
    }

    private String parentFragment(Set<Integer> component, List<int[]> bonds, Map<Integer, String> known) {
        for (int[] bond : bonds) if (component.contains(bond[1])) return known.get(bond[0]);
        return null;
    }

    private List<Set<Integer>> components(Set<Integer> included, List<List<Integer>> neighbors, Set<Long> cuts) {
        List<Set<Integer>> result = new ArrayList<>();
        Set<Integer> unseen = new HashSet<>(included);
        while (!unseen.isEmpty()) {
            int start = unseen.iterator().next();
            Set<Integer> component = new HashSet<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(start); unseen.remove(start);
            while (!queue.isEmpty()) {
                int atom = queue.remove(); component.add(atom);
                for (int neighbor : neighbors.get(atom)) {
                    if (included.contains(neighbor) && unseen.contains(neighbor)
                            && !cuts.contains(edge(atom, neighbor))) {
                        unseen.remove(neighbor); queue.add(neighbor);
                    }
                }
            }
            result.add(component);
        }
        result.sort(java.util.Comparator.comparingInt(set -> set.stream().min(Integer::compareTo).orElseThrow()));
        return result;
    }

    private void validateSelection(LocatedResidue selected, FlexibilityPreparationConfig config) {
        String name = selected.residue().getName();
        if (!StandardResidueChiBonds.supports(name) && !config.allowModifiedResidues()) {
            throw new FlexibilityValidationException("Unsupported modified or non-protein residue: " + name);
        }
        if (!config.allowTerminalResidues() && selected.terminal()) {
            throw new FlexibilityValidationException("Terminal flexible residue is not allowed: " + selected.reference());
        }
        if (selected.residue().getAtomCount() == 1) {
            throw new FlexibilityValidationException("Metal or monoatomic residue cannot be flexible: " + selected.reference());
        }
    }

    private Map<ResidueId, LocatedResidue> locate(Structure structure) {
        Map<ResidueId, LocatedResidue> result = new LinkedHashMap<>();
        int base = 0;
        for (Chain chain : structure.getChains()) {
            for (int ri = 0; ri < chain.residueCount(); ri++) {
                Residue residue = chain.residues().get(ri);
                ResidueId ref = new ResidueId(chain.id(), residue.getNumber(), residue.getInsertionCode());
                if (result.put(ref, new LocatedResidue(ref, residue, base,
                        ri == 0 || ri == chain.residueCount() - 1)) != null) {
                    throw new FlexibilityValidationException("Duplicate residue identity: " + ref);
                }
                base += residue.getAtomCount();
            }
        }
        return result;
    }

    private List<List<Integer>> localNeighbors(LocatedResidue selected, ProteinTopology topology) {
        int count = selected.residue().getAtomCount();
        List<List<Integer>> result = new ArrayList<>(count);
        for (int local = 0; local < count; local++) {
            List<Integer> neighbors = topology.neighbors(selected.baseIndex() + local).stream()
                    .filter(index -> index >= selected.baseIndex() && index < selected.baseIndex() + count)
                    .map(index -> index - selected.baseIndex()).toList();
            result.add(neighbors);
        }
        return result;
    }

    private AtomReference reference(LocatedResidue selected, int localIndex) {
        return new AtomReference(selected.reference(),
                selected.residue().getAtoms().get(localIndex).getName(), selected.baseIndex() + localIndex);
    }
    private int indexOf(List<Atom> atoms, String name) {
        for (int i = 0; i < atoms.size(); i++) if (name.equals(atoms.get(i).getName())) return i;
        return -1;
    }
    private void validateAcyclic(List<RigidFragment> fragments, List<RotatableBond> bonds) {
        if (bonds.size() >= fragments.size()) throw new FlexibilityValidationException("Flexible fragment graph is cyclic.");
    }
    private long edge(int a, int b) { return a < b ? ((long) a << 32) | b : ((long) b << 32) | a; }
    private record LocatedResidue(ResidueId reference, Residue residue, int baseIndex, boolean terminal) {}
}
