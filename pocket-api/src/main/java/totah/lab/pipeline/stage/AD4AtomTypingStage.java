package totah.lab.pipeline.stage;

import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.Stage;
import totah.lab.protein.Atom;
import totah.lab.protein.ElementResolver;
import totah.lab.protein.Residue;
import totah.lab.protein.Topology;
import totah.lab.topology.AutoDockType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Assigns AutoDock4 atom types for PDBQT output.
 */
public class AD4AtomTypingStage implements Stage {

    private final MetalIonPolicy metalIonPolicy = new MetalIonPolicy();

    @Override
    @SuppressWarnings("unchecked")
    public void run(PipelineContext context) {
        Objects.requireNonNull(context, "context is null");
        List<Residue> residues = (List<Residue>) context.require(ContextKeys.PROTEIN_RESIDUES);
        if (residues.isEmpty()) {
            throw new IllegalStateException("No protein_residues in context. Run ChargeAssignmentStage first.");
        }
        Topology topology = context.require(ContextKeys.PROTEIN_TOPOLOGY);
        context.require(ContextKeys.CHARGE_ASSIGNMENT_REPORT);
        Map<String, ResidueState> states = (Map<String, ResidueState>) context.require(ContextKeys.RESIDUE_STATES);

        List<FlatAtom> flatAtoms = flatten(residues);
        validateTopologyAtomCount(topology, flatAtoms.size());
        validateChargedAndAmberTyped(flatAtoms);

        List<Residue> typedResidues = new ArrayList<>();
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        int flatIndex = 0;
        for (Residue residue : residues) {
            ResidueState state = states.get(residueKey(residue));
            if (state == null && !metalIonPolicy.isKnownIonResidue(residue)) {
                throw new IllegalStateException("Missing residue state for " + residueLabel(residue));
            }
            List<Atom> typedAtoms = new ArrayList<>();
            for (Atom atom : residue.getAtoms()) {
                String ad4Type = assignType(atom, flatIndex, residue, state, topology, flatAtoms);
                typedAtoms.add(atom.toBuilder().autoDockType(ad4Type).build());
                typeCounts.merge(ad4Type, 1, Integer::sum);
                flatIndex++;
            }
            typedResidues.add(residue.toBuilder().atoms(typedAtoms).build());
        }

        context.put(ContextKeys.PROTEIN_RESIDUES, typedResidues);
        context.put(ContextKeys.AD4_ATOM_TYPING_REPORT,
                new AD4AtomTypingReport(typedResidues.size(), flatAtoms.size(), typeCounts));
    }

    private List<FlatAtom> flatten(List<Residue> residues) {
        List<FlatAtom> flatAtoms = new ArrayList<>();
        for (Residue residue : residues) {
            for (Atom atom : residue.getAtoms()) {
                flatAtoms.add(new FlatAtom(residue, atom));
            }
        }
        return flatAtoms;
    }

    private void validateChargedAndAmberTyped(List<FlatAtom> flatAtoms) {
        for (FlatAtom flatAtom : flatAtoms) {
            Atom atom = flatAtom.atom();
            if (!Double.isFinite(atom.getCharge())) {
                throw new IllegalStateException("Non-finite charge on " + atom.getName()
                        + " in " + residueLabel(flatAtom.residue()));
            }
            if (metalIonPolicy.isKnownIonResidue(flatAtom.residue())) {
                continue;
            }
            if (atom.getAmberType() == null || atom.getAmberType().isBlank()) {
                throw new IllegalStateException("Missing Amber atom type on " + atom.getName()
                        + " in " + residueLabel(flatAtom.residue()));
            }
        }
    }

    private void validateTopologyAtomCount(Topology topology, int atomCount) {
        if (topology.getAtomCount() != atomCount) {
            throw new IllegalStateException("Topology atom count " + topology.getAtomCount()
                    + " does not match receptor atom count " + atomCount);
        }
    }

    private String assignType(Atom atom, int flatIndex, Residue residue, ResidueState state,
                              Topology topology, List<FlatAtom> flatAtoms) {
        var ion = metalIonPolicy.fixedIon(residue);
        if (ion.isPresent()) {
            String type = metalIonPolicy.requireAd4Type(ion.get(), residue);
            assertLegalType(type);
            return type;
        }
        if (metalIonPolicy.isKnownIonResidue(residue)) {
            throw new IllegalStateException(metalIonPolicy.requireFixedChargeFailureMessage(residue));
        }
        String element = ElementResolver.resolveSymbol(atom, residue).toUpperCase(Locale.ROOT);
        String type = switch (element) {
            case "H" -> assignHydrogenType(flatIndex, topology, flatAtoms);
            case "C" -> assignCarbonType(atom, state);
            case "N" -> assignNitrogenType(atom, flatIndex, state, topology, flatAtoms);
            case "O" -> "OA";
            case "S" -> assignSulfurType(atom, flatIndex, state, topology, flatAtoms);
            case "P" -> "P";
            case "F" -> "F";
            case "CL" -> "Cl";
            case "BR" -> "Br";
            case "I" -> "I";
            case "MG" -> "Mg";
            case "CA" -> "Ca";
            case "MN" -> "Mn";
            case "FE" -> "Fe";
            case "ZN" -> "Zn";
            default -> throw new IllegalArgumentException("Unsupported AD4 element '" + element
                    + "' for " + atom.getName() + " in " + residueLabel(residue));
        };
        assertLegalType(type);
        return type;
    }

    private String assignHydrogenType(int flatIndex, Topology topology, List<FlatAtom> flatAtoms) {
        Integer parentIndex = bondedHeavyNeighbor(flatIndex, topology, flatAtoms);
        if (parentIndex == null) {
            throw new IllegalStateException("Hydrogen has no bonded heavy parent: "
                    + flatAtoms.get(flatIndex).atom().getName() + " in "
                    + residueLabel(flatAtoms.get(flatIndex).residue()));
        }
        Atom parent = flatAtoms.get(parentIndex).atom();
        String parentElement = ElementResolver.resolveSymbol(parent, flatAtoms.get(parentIndex).residue()).toUpperCase(Locale.ROOT);
        return switch (parentElement) {
            case "N", "O", "S" -> "HD";
            default -> "H";
        };
    }

    private String assignCarbonType(Atom atom, ResidueState state) {
        if (isAromaticCarbon(atom.getName(), baseTemplateName(state))) {
            return "A";
        }
        return "C";
    }

    private String assignNitrogenType(Atom atom, int flatIndex, ResidueState state,
                                      Topology topology, List<FlatAtom> flatAtoms) {
        String name = atom.getName();
        String template = baseTemplateName(state);
        if (("ND1".equals(name) || "NE2".equals(name))
                && ("HID".equals(template) || "HIE".equals(template) || "HIP".equals(template))) {
            return hasBondedHydrogen(flatIndex, topology, flatAtoms) ? "N" : "NA";
        }
        return "N";
    }

    private String assignSulfurType(Atom atom, int flatIndex, ResidueState state,
                                    Topology topology, List<FlatAtom> flatAtoms) {
        String template = baseTemplateName(state);
        if ("SD".equals(atom.getName()) && "MET".equals(template)) {
            return "S";
        }
        if ("SG".equals(atom.getName())) {
            if ("CYX".equals(template)) return "S";
            if ("CYM".equals(template)) return "SA";
            return hasBondedHydrogen(flatIndex, topology, flatAtoms) ? "SA" : "S";
        }
        return "S";
    }

    private Integer bondedHeavyNeighbor(int flatIndex, Topology topology, List<FlatAtom> flatAtoms) {
        for (Integer neighbor : topology.getNeighbors(flatIndex)) {
            if (!isHydrogen(flatAtoms.get(neighbor).atom(), flatAtoms.get(neighbor).residue())) {
                return neighbor;
            }
        }
        return null;
    }

    private boolean hasBondedHydrogen(int flatIndex, Topology topology, List<FlatAtom> flatAtoms) {
        for (Integer neighbor : topology.getNeighbors(flatIndex)) {
            if (isHydrogen(flatAtoms.get(neighbor).atom(), flatAtoms.get(neighbor).residue())) {
                return true;
            }
        }
        return false;
    }

    private boolean isHydrogen(Atom atom, Residue residue) {
        return "H".equals(ElementResolver.resolveSymbol(atom, residue).toUpperCase(Locale.ROOT));
    }

    private boolean isAromaticCarbon(String atomName, String templateName) {
        return switch (templateName) {
            case "PHE", "TYR", "TYS" -> atomName.matches("CG|CD[12]|CE[12]|CZ");
            case "TRP" -> atomName.matches("CG|CD[12]|CE[23]|CZ[23]|CH2");
            case "HID", "HIE", "HIP", "HIS" -> atomName.matches("CG|CD2|CE1");
            default -> false;
        };
    }

    private String baseTemplateName(ResidueState state) {
        String template = state.amberTemplateName();
        if (template.length() == 4 && (template.charAt(0) == 'N' || template.charAt(0) == 'C')) {
            return template.substring(1);
        }
        return template;
    }

    private void assertLegalType(String type) {
        for (AutoDockType value : AutoDockType.values()) {
            if (value.getSymbol().equals(type)) return;
        }
        throw new IllegalArgumentException("Illegal AD4 atom type: " + type);
    }

    private String residueKey(Residue residue) {
        return residue.getChain() + ":" + residue.getNumber() + insertionSuffix(residue);
    }

    private String insertionSuffix(Residue residue) {
        return residue.getInsertionCode() == null || residue.getInsertionCode() == ' '
                ? ""
                : residue.getInsertionCode().toString();
    }

    private String residueLabel(Residue residue) {
        String insertion = residue.getInsertionCode() == null || residue.getInsertionCode() == ' '
                ? ""
                : residue.getInsertionCode().toString();
        return residue.getName() + " " + residue.getChain() + ":" + residue.getNumber() + insertion;
    }

    private record FlatAtom(Residue residue, Atom atom) {
    }
}
