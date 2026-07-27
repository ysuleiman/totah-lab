package totah.lab.pipeline.stage;

import totah.lab.math.charges.ChargeModel;
import totah.lab.math.charges.ChargeSystem;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.Stage;
import totah.lab.protein.Atom;
import totah.lab.protein.ElementResolver;
import totah.lab.protein.Residue;
import totah.lab.protein.Topology;
import totah.lab.topology.AmberResidueTemplateLibrary;
import totah.lab.topology.AtomTemplate;
import totah.lab.topology.ResidueTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Assigns receptor partial charges.
 *
 * <p>Amber template charges are the pipeline default. The pluggable model is
 * retained as an explicit override path for experiments or non-Amber workflows.
 */
public class ChargeAssignmentStage implements Stage {

    private final ChargeModel model;

    public ChargeAssignmentStage(ChargeModel model) {
        this.model = model;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run(PipelineContext context) throws Exception {
        Objects.requireNonNull(context, "context is null");
        List<Residue> residues = (List<Residue>) context.require(ContextKeys.PROTEIN_RESIDUES);
        if (residues.isEmpty()) {
            throw new IllegalStateException("No protein_residues in context. Run TopologyBuilderStage first.");
        }
        Topology topology = context.require(ContextKeys.PROTEIN_TOPOLOGY);
        context.require(ContextKeys.TOPOLOGY_BUILD_REPORT);
        Map<String, ResidueState> states = (Map<String, ResidueState>) context.require(ContextKeys.RESIDUE_STATES);

        AmberAssignment amberAssignment = assignAmberCharges(residues, states, AmberResidueTemplateLibrary.getInstance());
        List<Residue> charged = amberAssignment.residues();
        String source = "AMBER";

        if (parseBoolean(context.get(ContextKeys.OVERRIDE_CHARGES_WITH_MODEL), false)) {
            if (model == null) {
                throw new IllegalStateException("overrideChargesWithModel=true but no ChargeModel was configured");
            }
            ChargeSystem system = new ResidueChargeSystem(charged, topology);
            double totalCharge = totalCharge(charged);
            double[] charges = model.computeCharges(system, totalCharge);
            zeroUnsupportedElements(system, charges, totalCharge);
            charged = applyCharges(charged, charges);
            source = model.getClass().getSimpleName();
        }

        context.put(ContextKeys.PROTEIN_RESIDUES, charged);
        context.put(ContextKeys.CHARGE_ASSIGNMENT_REPORT,
                new ChargeAssignmentReport(charged.size(), atomCount(charged), source,
                        totalCharge(charged), amberAssignment.assignedTemplates()));
    }

    private AmberAssignment assignAmberCharges(List<Residue> residues, Map<String, ResidueState> states,
                                               AmberResidueTemplateLibrary amber) {
        List<Residue> result = new ArrayList<>();
        List<String> assignedTemplates = new ArrayList<>();
        for (Residue residue : residues) {
            ResidueState state = states.get(residueKey(residue));
            if (state == null) {
                throw new IllegalStateException("Missing residue state for " + residueLabel(residue));
            }
            ResidueTemplate template = amber.getTemplate(state.amberTemplateName());
            if (template == null) {
                throw new IllegalArgumentException("No Amber template '" + state.amberTemplateName()
                        + "' for " + residueLabel(residue));
            }
            List<Atom> atoms = new ArrayList<>();
            for (Atom atom : residue.getAtoms()) {
                AtomTemplate atomTemplate = template.getAtom(atom.getName());
                if (atomTemplate == null) {
                    throw new IllegalStateException("No Amber atom '" + atom.getName()
                            + "' in template '" + state.amberTemplateName()
                            + "' for " + residueLabel(residue));
                }
                atoms.add(atom.toBuilder()
                        .charge(atomTemplate.getCharge())
                        .amberType(atomTemplate.getAmberType())
                        .build());
            }
            result.add(residue.toBuilder().atoms(atoms).build());
            assignedTemplates.add(residueKey(residue) + " -> " + state.amberTemplateName());
        }
        return new AmberAssignment(List.copyOf(result), assignedTemplates);
    }

    /**
     * Atoms whose element the model cannot parameterize get charge 0.0. Zeroing
     * after the solve breaks the total-charge constraint, so the deficit is
     * spread evenly over the supported atoms to restore it.
     */
    private void zeroUnsupportedElements(ChargeSystem system, double[] charges, double totalCharge) {
        int n = system.size();
        boolean[] supported = new boolean[n];
        for (int i = 0; i < n; i++) {
            supported[i] = model.hasParameters(system.getElement(i));
            if (!supported[i]) {
                charges[i] = 0.0;
            }
        }

        double sum = 0.0;
        int supportedCount = 0;
        for (int i = 0; i < n; i++) {
            if (supported[i]) {
                sum += charges[i];
                supportedCount++;
            }
        }
        if (supportedCount == 0 || supportedCount == n) return;
        double correction = (totalCharge - sum) / supportedCount;
        for (int i = 0; i < n; i++) {
            if (supported[i]) {
                charges[i] += correction;
            }
        }
    }

    private List<Residue> applyCharges(List<Residue> residues, double[] charges) {
        int idx = 0;
        List<Residue> result = new ArrayList<>();
        for (Residue residue : residues) {
            List<Atom> atoms = new ArrayList<>();
            for (Atom atom : residue.getAtoms()) {
                atoms.add(atom.toBuilder().charge(charges[idx++]).build());
            }
            result.add(residue.toBuilder().atoms(atoms).build());
        }
        return result;
    }

    private double totalCharge(List<Residue> residues) {
        return residues.stream()
                .flatMap(residue -> residue.getAtoms().stream())
                .mapToDouble(Atom::getCharge)
                .sum();
    }

    private int atomCount(List<Residue> residues) {
        return residues.stream().mapToInt(Residue::getAtomCount).sum();
    }

    private boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    private String residueKey(Residue residue) {
        return residue.getChain() + ":" + residue.getNumber();
    }

    private String residueLabel(Residue residue) {
        String insertion = residue.getInsertionCode() == null || residue.getInsertionCode() == ' '
                ? ""
                : residue.getInsertionCode().toString();
        return residue.getName() + " " + residue.getChain() + ":" + residue.getNumber() + insertion;
    }

    private record AmberAssignment(List<Residue> residues, List<String> assignedTemplates) {
        private AmberAssignment {
            assignedTemplates = List.copyOf(assignedTemplates);
        }
    }

    /**
     * Adapts protein Residues to ChargeSystem interface.
     * Bond neighbors come from the Topology, which is keyed by the same flat
     * atom index (residue iteration order) used here.
     */
    static class ResidueChargeSystem implements ChargeSystem {
        private final List<Atom> atoms;
        private final List<Residue> parentResidues;
        private final List<List<Integer>> neighbors;

        ResidueChargeSystem(List<Residue> residues, Topology topology) {
            this.atoms = new ArrayList<>();
            this.parentResidues = new ArrayList<>();
            for (Residue res : residues) {
                for (Atom atom : res.getAtoms()) {
                    this.atoms.add(atom);
                    this.parentResidues.add(res);
                }
            }

            this.neighbors = new ArrayList<>();
            for (int i = 0; i < atoms.size(); i++) {
                neighbors.add(topology.getNeighbors(i));
            }
        }

        @Override public int size() { return atoms.size(); }
        @Override public double getX(int i) { return atoms.get(i).getPosition().x(); }
        @Override public double getY(int i) { return atoms.get(i).getPosition().y(); }
        @Override public double getZ(int i) { return atoms.get(i).getPosition().z(); }

        @Override
        public String getElement(int i) {
            return ElementResolver.resolveSymbol(atoms.get(i), parentResidues.get(i));
        }

        @Override public List<Integer> getNeighbors(int i) { return neighbors.get(i); }
    }
}
