package totah.lab.hephaestus.charge;

import totah.lab.gaia.chemistry.ElementResolver;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.amber.AmberResidueTemplateLibrary;
import totah.lab.hephaestus.amber.ResidueTemplate;
import totah.lab.hephaestus.receptor.residue.ResidueState;
import totah.lab.hephaestus.topology.ProteinTopology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AmberChargeAssigner implements ChargeAssigner {

    private static final Map<String, IonCharge> FIXED_IONS = Map.of(
            "ZN", new IonCharge("Zn", 2.0),
            "MG", new IonCharge("Mg", 2.0),
            "CA", new IonCharge("Ca", 2.0),
            "NA", new IonCharge("Na", 1.0),
            "K", new IonCharge("K", 1.0),
            "CL", new IonCharge("Cl", -1.0));

    private static final Set<String> AMBIGUOUS_IONS = Set.of(
            "FE", "MN", "CU", "CO", "NI");

    private final AmberResidueTemplateLibrary templates;

    public AmberChargeAssigner() {
        this(AmberResidueTemplateLibrary.getInstance());
    }

    public AmberChargeAssigner(
            AmberResidueTemplateLibrary templates) {
        this.templates = Objects.requireNonNull(templates, "templates");
    }

    @Override
    public AssignmentResult assign(
            Structure structure,
            ProteinTopology topology,
            Map<String, ResidueState> residueStates) {

        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(residueStates, "residueStates");

        if (topology.atomCount() != structure.getAtomCount()) {
            throw new ChargeAssignmentException(
                    "Topology atom count " + topology.atomCount()
                            + " does not match structure atom count "
                            + structure.getAtomCount() + ".");
        }

        List<Chain> chargedChains = new ArrayList<>(structure.getChainCount());
        List<AssignedCharge> assignedCharges = new ArrayList<>();
        List<String> assignedTemplates = new ArrayList<>();
        int amberCount = 0;
        int ionCount = 0;
        int atomIndex = 0;

        for (Chain chain : structure.getChains()) {
            List<Residue> chargedResidues = new ArrayList<>(chain.residueCount());

            for (Residue residue : chain.residues()) {
                String key = residueKey(chain.id(), residue);
                List<Atom> chargedAtoms = new ArrayList<>(residue.getAtomCount());
                IonCharge ion = fixedIon(residue);

                if (ion != null) {
                    Atom atom = residue.getAtoms().getFirst();
                    Atom chargedAtom = atom.toBuilder()
                            .charge(ion.charge())
                            .amberType(ion.amberType())
                            .build();
                    chargedAtoms.add(chargedAtom);
                    assignedCharges.add(assignedCharge(
                            atomIndex++, chain.id(), residue, chargedAtom,
                            "FIXED_ION"));
                    assignedTemplates.add(
                            key + " -> ION:" + ion.amberType());
                    ionCount++;
                } else {
                    rejectAmbiguousIon(residue, chain.id());
                    ResidueState state = residueStates.get(key);
                    if (state == null) {
                        throw new ChargeAssignmentException(
                                "Missing residue state for "
                                        + label(chain.id(), residue));
                    }
                    ResidueTemplate template = templates.getTemplate(
                            state.amberTemplateName());
                    if (template == null) {
                        throw new ChargeAssignmentException(
                                "No Amber template '"
                                        + state.amberTemplateName()
                                        + "' for "
                                        + label(chain.id(), residue));
                    }

                    for (Atom atom : residue.getAtoms()) {
                        var atomTemplate = template.getAtomMap().get(atom.getName());
                        if (atomTemplate == null) {
                            throw new ChargeAssignmentException(
                                    "No Amber atom '" + atom.getName()
                                            + "' in template '"
                                            + state.amberTemplateName()
                                            + "' for "
                                            + label(chain.id(), residue));
                        }
                        Atom chargedAtom = atom.toBuilder()
                                .charge(atomTemplate.getCharge())
                                .amberType(atomTemplate.getAmberType())
                                .build();
                        chargedAtoms.add(chargedAtom);
                        assignedCharges.add(assignedCharge(
                                atomIndex++, chain.id(), residue, chargedAtom,
                                "AMBER:" + state.amberTemplateName()));
                        amberCount++;
                    }
                    assignedTemplates.add(
                            key + " -> " + state.amberTemplateName());
                }

                chargedResidues.add(
                        residue.toBuilder().atoms(chargedAtoms).build());
            }

            chargedChains.add(new Chain(chain.id(), chargedResidues));
        }

        ChargeAssignment assignment = new ChargeAssignment(
                "AMBER",
                assignedCharges);
        Structure chargedStructure = new Structure(chargedChains);
        ChargeAssignmentReport report = new ChargeAssignmentReport(
                structure.getResidueCount(),
                structure.getAtomCount(),
                amberCount,
                ionCount,
                assignment.source(),
                assignment.totalCharge(),
                assignedTemplates);

        return new AssignmentResult(chargedStructure, assignment, report);
    }

    private AssignedCharge assignedCharge(
            int atomIndex,
            String chainId,
            Residue residue,
            Atom atom,
            String provenance) {
        return new AssignedCharge(
                atomIndex,
                chainId,
                residue.getNumber(),
                residue.getInsertionCode(),
                atom.getName(),
                atom.getCharge(),
                atom.getAmberType(),
                provenance);
    }

    private IonCharge fixedIon(Residue residue) {
        if (residue.getAtomCount() != 1) return null;
        return FIXED_IONS.get(elementKey(residue));
    }

    private void rejectAmbiguousIon(Residue residue, String chainId) {
        if (residue.getAtomCount() == 1
                && AMBIGUOUS_IONS.contains(elementKey(residue))) {
            throw new ChargeAssignmentException(
                    "No fixed charge for " + label(chainId, residue)
                            + "; oxidation state is ambiguous.");
        }
    }

    private String elementKey(Residue residue) {
        return ElementResolver.resolve(
                        residue.getAtoms().getFirst(), residue)
                .toUpperCase(Locale.ROOT);
    }

    private String residueKey(String chainId, Residue residue) {
        Character insertion = residue.getInsertionCode();
        return chainId + ":" + residue.getNumber()
                + (insertion == null ? "" : insertion);
    }

    private String label(String chainId, Residue residue) {
        return residue.getName() + " " + residueKey(chainId, residue);
    }

    private record IonCharge(String amberType, double charge) {
    }
}
