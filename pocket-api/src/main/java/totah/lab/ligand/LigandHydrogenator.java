package totah.lab.ligand;

import totah.lab.chemistry.AtomChemicalProperties;
import totah.lab.chemistry.ChemicalAtomFactory;
import totah.lab.chemistry.ChemicalBond;
import totah.lab.chemistry.MolecularGraph;
import totah.lab.protein.Atom;
import totah.lab.protein.Point3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LigandHydrogenator {

    private final LigandHydrogenPlanner planner;
    private final CcdHydrogenCoordinateGenerator coordinateGenerator;
    private final LigandValenceValidator valenceValidator;

    public LigandHydrogenator() {
        this(
                new LigandHydrogenPlanner(),
                new CcdHydrogenCoordinateGenerator(),
                new LigandValenceValidator());
    }

    LigandHydrogenator(
            LigandHydrogenPlanner planner,
            CcdHydrogenCoordinateGenerator coordinateGenerator,
            LigandValenceValidator valenceValidator) {
        this.planner = Objects.requireNonNull(planner, "planner is null");
        this.coordinateGenerator = Objects.requireNonNull(
                coordinateGenerator, "coordinateGenerator is null");
        this.valenceValidator = Objects.requireNonNull(
                valenceValidator, "valenceValidator is null");
    }

    public LigandHydrogenationResult hydrogenate(CcdLigandGraphResult graphResult) {
        Objects.requireNonNull(graphResult, "graphResult is null");
        LigandHydrogenPlan plan = planner.plan(graphResult);
        MolecularGraph original = graphResult.graph();
        List<Atom> atoms = new ArrayList<>(original.atoms());
        List<AtomChemicalProperties> properties = new ArrayList<>(
                original.atomProperties());
        List<ChemicalBond> bonds = new ArrayList<>(original.bonds());
        List<String> generatedNames = new ArrayList<>();

        for (MissingLigandHydrogen hydrogen : plan.hydrogens()) {
            Point3D position = coordinateGenerator.generate(graphResult, hydrogen);
            Atom parent = original.atoms().get(hydrogen.parentAtomIndex());
            int hydrogenIndex = atoms.size();
            atoms.add(ChemicalAtomFactory.hydrogen(
                    hydrogen.ccdAtomId(), position, parent.getBFactor()));
            properties.add(new AtomChemicalProperties(
                    hydrogen.ccdAtomId(),
                    hydrogen.formalCharge(),
                    hydrogen.aromatic(),
                    hydrogen.leavingAtom(),
                    null));
            bonds.add(new ChemicalBond(
                    hydrogen.parentAtomIndex(),
                    hydrogenIndex,
                    hydrogen.bondOrder(),
                    false));
            generatedNames.add(hydrogen.ccdAtomId());
        }

        MolecularGraph completed = new MolecularGraph(atoms, bonds, properties);
        LigandValenceValidationReport completedValence = valenceValidator.validate(
                completed, List.of());
        if (!completedValence.valid()) {
            throw new LigandValenceException(completedValence);
        }
        return new LigandHydrogenationResult(
                completed, generatedNames, completedValence);
    }
}
