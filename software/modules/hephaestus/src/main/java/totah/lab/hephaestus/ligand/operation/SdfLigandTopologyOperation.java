package totah.lab.hephaestus.ligand.operation;

import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.chemistry.FormalCharge;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.hephaestus.ligand.LigandPreparationOperation;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.LigandUnsupportedReason;
import totah.lab.hephaestus.ligand.UnsupportedLigandException;
import totah.lab.hephaestus.ligand.topology.CcdAtomCoordinates;
import totah.lab.hephaestus.ligand.topology.KekuleAromaticity;
import totah.lab.hephaestus.ligand.topology.LigandAtomProperties;
import totah.lab.hephaestus.ligand.topology.LigandTopology;
import totah.lab.hephaestus.model.PreparationIssue;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.model.Severity;
import totah.lab.hephaestus.preparation.OperationResult;
import totah.lab.hermes.file.sdf.SdfLigand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds ligand topology from the explicit bond table and formal charges
 * of a parsed SDF record, replacing the CCD-driven
 * {@link LigandTopologyOperation} for SDF input. Hydrogens must be
 * explicit in the SDF: CCD-free hydrogen addition is not supported.
 * Disconnected input fails with
 * {@link LigandUnsupportedReason#DISCONNECTED_GRAPH} unless
 * {@link LigandPreparationOptions#selectLargestFragment()} is enabled, in
 * which case the largest heavy-atom fragment is kept (salt stripping)
 * with a warning.
 */
public final class SdfLigandTopologyOperation implements LigandPreparationOperation {

    public static final String LARGEST_FRAGMENT_ISSUE_CODE = "LARGEST_FRAGMENT_SELECTED";

    private final SdfLigand source;

    public SdfLigandTopologyOperation(SdfLigand source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    @Override
    public OperationResult<PreparedLigand> apply(
            PreparedLigand preparedLigand,
            LigandPreparationOptions options) {
        Objects.requireNonNull(preparedLigand, "preparedLigand");
        Objects.requireNonNull(options, "options");

        Residue residue = LigandStructureSupport.singleResidue(preparedLigand.ligand());
        verifyMatchesSource(residue.getAtoms());
        String componentId = preparedLigand.ligand().componentCode()
                .orElse(residue.getName());

        List<Atom> atoms = residue.getAtoms();
        List<ChemicalBond> bonds = source.bonds();
        List<Integer> formalCharges = source.formalCharges();
        Ligand ligand = preparedLigand.ligand();
        List<PreparationIssue> issues = new ArrayList<>();

        List<List<Integer>> fragments = source.fragments();
        if (fragments.size() > 1) {
            if (!options.selectLargestFragment()) {
                throw new UnsupportedLigandException(
                        componentId,
                        LigandUnsupportedReason.DISCONNECTED_GRAPH,
                        "SDF input contains " + fragments.size()
                                + " disconnected fragments; enable selectLargestFragment"
                                + " to keep the largest heavy-atom fragment.");
            }
            List<Integer> selected = largestHeavyAtomFragment(fragments, atoms);
            Set<Integer> kept = new HashSet<>(selected);
            Map<Integer, Integer> reindex = new HashMap<>();
            List<Atom> selectedAtoms = new ArrayList<>();
            List<Integer> selectedCharges = new ArrayList<>();
            for (int index : selected) {
                reindex.put(index, selectedAtoms.size());
                selectedAtoms.add(atoms.get(index));
                selectedCharges.add(formalCharges.get(index));
            }
            List<ChemicalBond> selectedBonds = new ArrayList<>();
            for (ChemicalBond bond : bonds) {
                if (kept.contains(bond.atomIndexA()) && kept.contains(bond.atomIndexB())) {
                    selectedBonds.add(new ChemicalBond(
                            reindex.get(bond.atomIndexA()),
                            reindex.get(bond.atomIndexB()),
                            bond.order(), bond.aromatic()));
                }
            }
            int dropped = atoms.size() - selectedAtoms.size();
            ligand = withFormalCharge(
                    LigandStructureSupport.replaceAtoms(ligand, selectedAtoms),
                    selectedCharges);
            atoms = selectedAtoms;
            bonds = selectedBonds;
            formalCharges = selectedCharges;
            issues.add(new PreparationIssue(
                    Severity.WARNING,
                    LARGEST_FRAGMENT_ISSUE_CODE,
                    "Dropped " + (fragments.size() - 1) + " disconnected fragment(s) ("
                            + dropped + " atom(s)); kept the largest heavy-atom fragment ("
                            + selectedAtoms.size() + " atom(s))."));
        }

        if (options.addHydrogens() && atoms.stream().noneMatch(Atom::isHydrogen)) {
            throw new UnsupportedLigandException(
                    componentId,
                    LigandUnsupportedReason.UNUSABLE_HYDROGEN_REFERENCE_GEOMETRY,
                    "CCD-free hydrogen addition is not supported; "
                            + "supply an SDF with explicit hydrogens.");
        }

        return new OperationResult<>(
                preparedLigand.withLigand(ligand)
                        .withTopology(buildTopology(componentId, atoms, bonds, formalCharges)),
                issues);
    }

    private void verifyMatchesSource(List<Atom> atoms) {
        List<Atom> sourceAtoms = source.ligand().structure().getChains()
                .getFirst().residues().getFirst().getAtoms();
        if (atoms.size() != sourceAtoms.size()) {
            throw new IllegalArgumentException(
                    "Prepared ligand does not match the SDF input atom count.");
        }
        for (int index = 0; index < atoms.size(); index++) {
            if (!atoms.get(index).getName().equals(sourceAtoms.get(index).getName())) {
                throw new IllegalArgumentException(
                        "Prepared ligand does not match the SDF input at atom index " + index);
            }
        }
    }

    private List<Integer> largestHeavyAtomFragment(
            List<List<Integer>> fragments, List<Atom> atoms) {
        return fragments.stream().max(Comparator
                        .comparingInt((List<Integer> fragment) -> (int) fragment.stream()
                                .filter(index -> atoms.get(index).isHeavyAtom()).count())
                        .thenComparingInt(List::size)
                        .thenComparingInt(fragment -> -fragment.getFirst()))
                .orElseThrow();
    }

    private LigandTopology buildTopology(
            String componentId, List<Atom> atoms, List<ChemicalBond> bonds,
            List<Integer> formalCharges) {
        boolean[] aromatic = new boolean[atoms.size()];
        for (ChemicalBond bond : bonds) {
            if (bond.aromatic()) {
                aromatic[bond.atomIndexA()] = true;
                aromatic[bond.atomIndexB()] = true;
            }
        }
        // Kekulé-encoded rings carry no aromatic bond flags; perceive
        // them so typing matches RDKit/Meeko semantics.
        boolean[] perceived = KekuleAromaticity.perceive(
                atoms.size(), bonds, atoms);
        for (int index = 0; index < aromatic.length; index++) {
            aromatic[index] = aromatic[index] || perceived[index];
        }
        List<LigandAtomProperties> properties = new ArrayList<>();
        List<CcdAtomCoordinates> coordinates = new ArrayList<>();
        for (int index = 0; index < atoms.size(); index++) {
            properties.add(new LigandAtomProperties(
                    atoms.get(index).getName(), formalCharges.get(index),
                    aromatic[index], false));
            coordinates.add(new CcdAtomCoordinates(
                    index, atoms.get(index).getPosition(), null));
        }
        return new LigandTopology(
                componentId, atoms.size(), bonds, properties, List.of(), coordinates);
    }

    private Ligand withFormalCharge(Ligand ligand, List<Integer> formalCharges) {
        int total = formalCharges.stream().mapToInt(Integer::intValue).sum();
        return new Ligand(
                ligand.id(), ligand.name(), ligand.componentCode().orElse(null),
                ligand.smiles().orElse(null), ligand.inchiKey().orElse(null),
                FormalCharge.of(total), ligand.structure());
    }
}
