package totah.lab.ligand;

import totah.lab.protein.Atom;
import totah.lab.chemistry.BondOrder;
import totah.lab.chemistry.ChemicalBond;
import totah.lab.chemistry.MolecularGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class LigandValenceValidator {

    private static final double EPSILON = 1.0e-8;

    public LigandValenceValidationReport validate(
            MolecularGraph graph,
            List<MissingLigandHydrogen> missingHydrogens) {
        Objects.requireNonNull(graph, "graph is null");
        Objects.requireNonNull(missingHydrogens, "missingHydrogens is null");

        double[] depositedSums = new double[graph.atoms().size()];
        for (ChemicalBond bond : graph.bonds()) {
            double order = numericalOrder(bond.order());
            depositedSums[bond.atomIndexA()] += order;
            depositedSums[bond.atomIndexB()] += order;
        }

        int[] plannedHydrogenCounts = new int[graph.atoms().size()];
        for (MissingLigandHydrogen hydrogen : missingHydrogens) {
            if (hydrogen.parentAtomIndex() >= graph.atoms().size()) {
                throw new IllegalArgumentException(
                        "Missing hydrogen parent index is outside molecular graph");
            }
            plannedHydrogenCounts[hydrogen.parentAtomIndex()]++;
        }

        List<LigandValenceValidationReport.AtomValence> atoms = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        for (int index = 0; index < graph.atoms().size(); index++) {
            Atom atom = graph.atoms().get(index);
            String element = element(atom);
            int formalCharge = graph.atomProperties().get(index).formalCharge();
            boolean aromatic = graph.atomProperties().get(index).aromatic();
            double maximum = maximumSupportedValence(element, formalCharge, aromatic);
            double completed = depositedSums[index] + plannedHydrogenCounts[index];
            atoms.add(new LigandValenceValidationReport.AtomValence(
                    index,
                    atom.getName(),
                    element,
                    formalCharge,
                    depositedSums[index],
                    plannedHydrogenCounts[index],
                    completed,
                    maximum));
            if (!Double.isFinite(maximum)) {
                violations.add(atom.getName() + ": unsupported element/formal charge "
                        + element + "(" + formalCharge + ")");
            } else if (completed > maximum + EPSILON) {
                violations.add(atom.getName() + ": completed bond-order sum " + completed
                        + " exceeds supported valence " + maximum);
            }
        }
        return new LigandValenceValidationReport(atoms, violations);
    }

    private double maximumSupportedValence(
            String element,
            int formalCharge,
            boolean aromatic) {
        return switch (element) {
            case "H" -> formalCharge == 0 ? 1.0 : Double.NaN;
            case "C" -> formalCharge >= -1 && formalCharge <= 1 ? 4.0 : Double.NaN;
            case "N" -> switch (formalCharge) {
                case -1, 0 -> aromatic ? 4.0 : 3.0;
                case 1 -> 4.0;
                default -> Double.NaN;
            };
            case "O" -> switch (formalCharge) {
                case -1 -> 1.0;
                case 0 -> 2.0;
                case 1 -> 3.0;
                default -> Double.NaN;
            };
            case "S" -> formalCharge >= -1 && formalCharge <= 1 ? 6.0 : Double.NaN;
            case "F", "CL", "BR", "I" -> formalCharge == 0 ? 1.0 : Double.NaN;
            default -> Double.NaN;
        };
    }

    private double numericalOrder(BondOrder order) {
        return switch (order) {
            case SINGLE -> 1.0;
            case DOUBLE -> 2.0;
            case TRIPLE -> 3.0;
            case AROMATIC -> 1.5;
        };
    }

    private String element(Atom atom) {
        if (atom.getElement() == null || atom.getElement().getSymbol() == null) {
            return "";
        }
        return atom.getElement().getSymbol().trim().toUpperCase(Locale.ROOT);
    }
}
