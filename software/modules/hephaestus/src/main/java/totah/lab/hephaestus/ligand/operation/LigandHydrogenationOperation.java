package totah.lab.hephaestus.ligand.operation;

import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.Atom;
import totah.lab.hephaestus.ligand.LigandPreparationOperation;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.hydrogen.CcdHydrogenCoordinateGenerator;
import totah.lab.hephaestus.ligand.topology.CcdAtomCoordinates;
import totah.lab.hephaestus.ligand.topology.LigandAtomProperties;
import totah.lab.hephaestus.ligand.topology.LigandTopology;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.preparation.OperationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LigandHydrogenationOperation implements LigandPreparationOperation {
    private final CcdHydrogenCoordinateGenerator coordinateGenerator;

    public LigandHydrogenationOperation() {
        this(new CcdHydrogenCoordinateGenerator());
    }

    public LigandHydrogenationOperation(CcdHydrogenCoordinateGenerator coordinateGenerator) {
        this.coordinateGenerator = Objects.requireNonNull(coordinateGenerator, "coordinateGenerator");
    }

    @Override
    public OperationResult<PreparedLigand> apply(
            PreparedLigand preparedLigand, LigandPreparationOptions options) {
        Objects.requireNonNull(preparedLigand, "preparedLigand");
        Objects.requireNonNull(options, "options");
        if (!options.addHydrogens()) {
            return OperationResult.success(preparedLigand);
        }
        if (!(preparedLigand.topology() instanceof LigandTopology topology)) {
            throw new IllegalStateException("Ligand CCD topology is required before hydrogenation.");
        }
        List<Atom> original = LigandStructureSupport.singleResidue(
                preparedLigand.ligand()).getAtoms();
        List<Atom> atoms = new ArrayList<>(original);
        List<ChemicalBond> bonds = new ArrayList<>(topology.bonds());
        List<LigandAtomProperties> properties = new ArrayList<>(topology.atomProperties());
        List<CcdAtomCoordinates> coordinates = new ArrayList<>(topology.ccdCoordinates());
        int nextSerial = atoms.stream().mapToInt(Atom::getPdbSerial).max().orElse(0) + 1;
        for (var hydrogen : topology.missingHydrogens()) {
            int index = atoms.size();
            Atom parent = original.get(hydrogen.parentAtomIndex());
            atoms.add(Atom.builder()
                    .pdbSerial(nextSerial++)
                    .name(hydrogen.atomName())
                    .position(coordinateGenerator.generate(original, topology, hydrogen))
                    .charge(0.0)
                    .occupancy(1.0)
                    .bFactor(parent.getBFactor())
                    .element(Element.H)
                    .build());
            bonds.add(new ChemicalBond(hydrogen.parentAtomIndex(), index,
                    hydrogen.bondOrder(), false));
            properties.add(new LigandAtomProperties(
                    hydrogen.atomName(), hydrogen.formalCharge(),
                    hydrogen.aromatic(), hydrogen.leavingAtom()));
            coordinates.add(new CcdAtomCoordinates(
                    index, hydrogen.modelPosition(), hydrogen.idealPosition()));
        }
        LigandTopology completed = new LigandTopology(
                topology.componentId(), atoms.size(), bonds, properties, List.of(), coordinates);
        validateValence(atoms, completed);
        var ligand = LigandStructureSupport.replaceAtoms(preparedLigand.ligand(), atoms);
        return OperationResult.success(preparedLigand.withLigand(ligand).withTopology(completed));
    }

    private void validateValence(List<Atom> atoms, LigandTopology topology) {
        double[] sums = new double[atoms.size()];
        for (ChemicalBond bond : topology.bonds()) {
            double order = switch (bond.order()) {
                case SINGLE -> 1.0;
                case DOUBLE -> 2.0;
                case TRIPLE -> 3.0;
                case AROMATIC -> 1.5;
            };
            sums[bond.atomIndexA()] += order;
            sums[bond.atomIndexB()] += order;
        }
        for (int index = 0; index < atoms.size(); index++) {
            double maximum = switch (atoms.get(index).getElement()) {
                case H, F, CL, BR, I -> 1.0;
                case C -> 4.0;
                case N -> topology.atomProperties().get(index).formalCharge() > 0 ? 4.0 : 3.0;
                case O -> topology.atomProperties().get(index).formalCharge() > 0 ? 3.0 : 2.0;
                case S -> 6.0;
                default -> Double.POSITIVE_INFINITY;
            };
            if (sums[index] > maximum + 1.0e-8) {
                throw new IllegalStateException(
                        "Completed valence exceeds supported valence for atom "
                                + atoms.get(index).getName());
            }
        }
    }
}
