package totah.lab.hephaestus.typing;

import totah.lab.gaia.chemistry.ElementResolver;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.receptor.cleanup.MetalIonPolicy;
import totah.lab.hephaestus.receptor.residue.ResidueState;
import totah.lab.hephaestus.topology.ProteinTopology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AD4AtomTyper implements AtomTyper {

    private static final Set<String> LEGAL_TYPES = Set.of(
            "C", "A", "N", "NA", "O", "OA", "S", "SA", "P",
            "HD", "H", "F", "Cl", "Br", "I", "Mg", "Mn", "Fe",
            "Zn", "Ca");

    private final MetalIonPolicy metalIonPolicy;

    public AD4AtomTyper() {
        this(new MetalIonPolicy());
    }

    public AD4AtomTyper(MetalIonPolicy metalIonPolicy) {
        this.metalIonPolicy = Objects.requireNonNull(
                metalIonPolicy, "metalIonPolicy");
    }

    @Override
    public TypingResult assign(
            Structure structure,
            ProteinTopology topology,
            Map<String, ResidueState> residueStates) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(residueStates, "residueStates");

        List<AtomOwner> atoms = atomOwners(structure);
        if (topology.atomCount() != atoms.size()) {
            throw new IllegalStateException(
                    "Topology atom count " + topology.atomCount()
                            + " does not match receptor atom count "
                            + atoms.size() + ".");
        }
        validateChargedAndAmberTyped(atoms);

        List<Chain> typedChains = new ArrayList<>(structure.getChainCount());
        List<AssignedAtomType> assignments = new ArrayList<>(atoms.size());
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        int atomIndex = 0;

        for (Chain chain : structure.getChains()) {
            List<Residue> typedResidues = new ArrayList<>(chain.residueCount());
            for (Residue residue : chain.residues()) {
                ResidueState state = residueStates.get(
                        residueKey(chain.id(), residue));
                if (state == null && !metalIonPolicy.isKnownIonResidue(residue)) {
                    throw new IllegalStateException(
                            "Missing residue state for "
                                    + residueLabel(chain.id(), residue));
                }

                List<Atom> typedAtoms = new ArrayList<>(residue.getAtomCount());
                for (Atom atom : residue.getAtoms()) {
                    String type = assignType(
                            atom, atomIndex, chain.id(), residue, state,
                            topology, atoms);
                    typedAtoms.add(atom.toBuilder().autoDockType(type).build());
                    assignments.add(new AssignedAtomType(
                            atomIndex, chain.id(), residue.getNumber(),
                            residue.getInsertionCode(), atom.getName(), type));
                    typeCounts.merge(type, 1, Integer::sum);
                    atomIndex++;
                }
                typedResidues.add(residue.toBuilder().atoms(typedAtoms).build());
            }
            typedChains.add(new Chain(chain.id(), typedResidues));
        }

        return new TypingResult(
                new Structure(typedChains),
                new AtomTypeAssignment("AutoDock4", assignments),
                new AD4AtomTypingReport(
                        structure.getResidueCount(), atoms.size(), typeCounts));
    }

    private List<AtomOwner> atomOwners(Structure structure) {
        List<AtomOwner> atoms = new ArrayList<>(structure.getAtomCount());
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                for (Atom atom : residue.getAtoms()) {
                    atoms.add(new AtomOwner(chain.id(), residue, atom));
                }
            }
        }
        return List.copyOf(atoms);
    }

    private void validateChargedAndAmberTyped(List<AtomOwner> atoms) {
        for (AtomOwner owner : atoms) {
            Atom atom = owner.atom();
            if (!Double.isFinite(atom.getCharge())) {
                throw new IllegalStateException(
                        "Non-finite charge on " + atom.getName() + " in "
                                + residueLabel(owner.chainId(), owner.residue()));
            }
            if (!metalIonPolicy.isKnownIonResidue(owner.residue())
                    && (atom.getAmberType() == null
                    || atom.getAmberType().isBlank())) {
                throw new IllegalStateException(
                        "Missing Amber atom type on " + atom.getName() + " in "
                                + residueLabel(owner.chainId(), owner.residue()));
            }
        }
    }

    private String assignType(
            Atom atom,
            int atomIndex,
            String chainId,
            Residue residue,
            ResidueState state,
            ProteinTopology topology,
            List<AtomOwner> atoms) {
        var ion = metalIonPolicy.fixedIon(residue);
        if (ion.isPresent()) {
            return legal(metalIonPolicy.requireAd4Type(ion.get(), residue));
        }
        if (metalIonPolicy.isKnownIonResidue(residue)) {
            throw new IllegalStateException(
                    metalIonPolicy.requireFixedChargeFailureMessage(residue));
        }

        String element = element(atom, residue);
        String type = switch (element) {
            case "H" -> hydrogenType(atomIndex, topology, atoms);
            case "C" -> isAromaticCarbon(
                    atom.getName(), baseTemplateName(state)) ? "A" : "C";
            case "N" -> nitrogenType(atom, atomIndex, state, topology, atoms);
            case "O" -> "OA";
            case "S" -> sulfurType(atom, atomIndex, state, topology, atoms);
            case "P", "F", "I" -> element;
            case "CL" -> "Cl";
            case "BR" -> "Br";
            case "MG" -> "Mg";
            case "CA" -> "Ca";
            case "MN" -> "Mn";
            case "FE" -> "Fe";
            case "ZN" -> "Zn";
            default -> throw new IllegalArgumentException(
                    "Unsupported AD4 element '" + element + "' for "
                            + atom.getName() + " in "
                            + residueLabel(chainId, residue));
        };
        return legal(type);
    }

    private String hydrogenType(
            int atomIndex,
            ProteinTopology topology,
            List<AtomOwner> atoms) {
        for (int neighbor : topology.neighbors(atomIndex)) {
            AtomOwner parent = atoms.get(neighbor);
            if (!"H".equals(element(parent.atom(), parent.residue()))) {
                return switch (element(parent.atom(), parent.residue())) {
                    case "N", "O", "S" -> "HD";
                    default -> "H";
                };
            }
        }
        AtomOwner owner = atoms.get(atomIndex);
        throw new IllegalStateException(
                "Hydrogen has no bonded heavy parent: "
                        + owner.atom().getName() + " in "
                        + residueLabel(owner.chainId(), owner.residue()));
    }

    private String nitrogenType(
            Atom atom,
            int atomIndex,
            ResidueState state,
            ProteinTopology topology,
            List<AtomOwner> atoms) {
        String template = baseTemplateName(state);
        if (("ND1".equals(atom.getName()) || "NE2".equals(atom.getName()))
                && Set.of("HID", "HIE", "HIP").contains(template)) {
            return hasBondedHydrogen(atomIndex, topology, atoms) ? "N" : "NA";
        }
        return "N";
    }

    private String sulfurType(
            Atom atom,
            int atomIndex,
            ResidueState state,
            ProteinTopology topology,
            List<AtomOwner> atoms) {
        String template = baseTemplateName(state);
        if ("SD".equals(atom.getName()) && "MET".equals(template)) return "S";
        if ("SG".equals(atom.getName())) {
            if ("CYX".equals(template)) return "S";
            if ("CYM".equals(template)) return "SA";
            return hasBondedHydrogen(atomIndex, topology, atoms) ? "SA" : "S";
        }
        return "S";
    }

    private boolean hasBondedHydrogen(
            int atomIndex,
            ProteinTopology topology,
            List<AtomOwner> atoms) {
        return topology.neighbors(atomIndex).stream()
                .anyMatch(index -> "H".equals(element(
                        atoms.get(index).atom(), atoms.get(index).residue())));
    }

    private boolean isAromaticCarbon(String atomName, String template) {
        return switch (template) {
            case "PHE", "TYR", "TYS" ->
                    atomName.matches("CG|CD[12]|CE[12]|CZ");
            case "TRP" -> atomName.matches("CG|CD[12]|CE[23]|CZ[23]|CH2");
            case "HID", "HIE", "HIP", "HIS" ->
                    atomName.matches("CG|CD2|CE1");
            default -> false;
        };
    }

    private String baseTemplateName(ResidueState state) {
        String template = state.amberTemplateName();
        if (template.length() == 4
                && (template.charAt(0) == 'N' || template.charAt(0) == 'C')) {
            return template.substring(1);
        }
        return template;
    }

    private String legal(String type) {
        if (!LEGAL_TYPES.contains(type)) {
            throw new IllegalArgumentException("Illegal AD4 atom type: " + type);
        }
        return type;
    }

    private String element(Atom atom, Residue residue) {
        return ElementResolver.resolve(atom, residue).toUpperCase(Locale.ROOT);
    }

    private String residueKey(String chainId, Residue residue) {
        Character insertion = residue.getInsertionCode();
        return chainId + ":" + residue.getNumber()
                + (insertion == null ? "" : insertion);
    }

    private String residueLabel(String chainId, Residue residue) {
        return residue.getName() + " " + residueKey(chainId, residue);
    }

    private record AtomOwner(String chainId, Residue residue, Atom atom) {
    }
}
