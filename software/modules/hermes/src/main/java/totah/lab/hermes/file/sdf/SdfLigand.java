package totah.lab.hermes.file.sdf;

import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A ligand parsed from an SDF (V2000) record together with the explicit
 * bond table and per-atom formal charges, which the {@link Ligand} /
 * {@link totah.lab.gaia.structure.Structure} model does not carry.
 * Atom indices are positions within the single residue of
 * {@link #ligand()}.
 */
public record SdfLigand(
        Ligand ligand,
        List<ChemicalBond> bonds,
        List<Integer> formalCharges,
        String title) {

    public SdfLigand {
        Objects.requireNonNull(ligand, "ligand");
        bonds = List.copyOf(Objects.requireNonNull(bonds, "bonds"));
        formalCharges = List.copyOf(
                Objects.requireNonNull(formalCharges, "formalCharges"));
        Objects.requireNonNull(title, "title");
        if (ligand.structure().getChains().size() != 1
                || ligand.structure().getChains().getFirst().residues().size() != 1) {
            throw new IllegalArgumentException(
                    "An SDF ligand must contain exactly one chain and one residue.");
        }
        int atomCount = atoms(ligand).size();
        if (formalCharges.size() != atomCount) {
            throw new IllegalArgumentException(
                    "formalCharges must match the ligand atom count.");
        }
        for (ChemicalBond bond : bonds) {
            if (bond.atomIndexA() >= atomCount || bond.atomIndexB() >= atomCount) {
                throw new IllegalArgumentException(
                        "Bond references an atom outside the ligand.");
            }
        }
    }

    public int atomCount() {
        return atoms(ligand).size();
    }

    public boolean hasExplicitHydrogens() {
        return atoms(ligand).stream().anyMatch(Atom::isHydrogen);
    }

    /**
     * Connected components of the bond graph, each a sorted list of atom
     * indices; components are ordered by their lowest atom index so the
     * result is deterministic.
     */
    public List<List<Integer>> fragments() {
        int atomCount = atomCount();
        List<List<Integer>> adjacency = new ArrayList<>();
        for (int index = 0; index < atomCount; index++) {
            adjacency.add(new ArrayList<>());
        }
        for (ChemicalBond bond : bonds) {
            adjacency.get(bond.atomIndexA()).add(bond.atomIndexB());
            adjacency.get(bond.atomIndexB()).add(bond.atomIndexA());
        }
        List<List<Integer>> fragments = new ArrayList<>();
        boolean[] visited = new boolean[atomCount];
        for (int start = 0; start < atomCount; start++) {
            if (visited[start]) {
                continue;
            }
            List<Integer> members = new ArrayList<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            visited[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int atom = queue.removeFirst();
                members.add(atom);
                for (int neighbor : adjacency.get(atom)) {
                    if (!visited[neighbor]) {
                        visited[neighbor] = true;
                        queue.add(neighbor);
                    }
                }
            }
            members.sort(Integer::compareTo);
            fragments.add(List.copyOf(members));
        }
        return List.copyOf(fragments);
    }

    private static List<Atom> atoms(Ligand ligand) {
        List<Residue> residues = ligand.structure().getChains()
                .getFirst().residues();
        return residues.getFirst().getAtoms();
    }
}
