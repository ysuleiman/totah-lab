package totah.lab.pipeline.stage;

import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.Stage;
import totah.lab.protein.Atom;
import totah.lab.protein.Pocket;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.protein.Topology;
import totah.lab.topology.AmberResidueTemplateLibrary;
import totah.lab.topology.BondTemplate;
import totah.lab.topology.ResidueTemplate;
import totah.lab.util.PocketGeometry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Stage: build an Amber-template topology over prepared residues.
 *
 * <p>Intra-residue bonds come from the assigned Amber residue templates.
 * Cross-residue peptide and disulfide bonds are added explicitly. Atoms and
 * residues are left untouched.
 */
public class TopologyBuilderStage implements Stage {

    private static final double MIN_PEPTIDE_BOND = 1.15;
    private static final double MAX_PEPTIDE_BOND = 1.55;
    private static final double MIN_DISULFIDE_BOND = 1.75;
    private static final double MAX_DISULFIDE_BOND = 2.35;
    private static final double DEFAULT_POCKET_PROXIMITY_CUTOFF = 8.0;

    @Override
    @SuppressWarnings("unchecked")
    public void run(PipelineContext context) {
        Objects.requireNonNull(context, "context is null");
        List<Residue> residues = (List<Residue>) context.require(ContextKeys.PROTEIN_RESIDUES);
        if (residues.isEmpty()) {
            throw new IllegalStateException("No protein_residues in context. Run HydrogenOptimizationStage first.");
        }
        context.require(ContextKeys.HYDROGEN_OPTIMIZATION_REPORT);
        Map<String, ResidueState> states = (Map<String, ResidueState>) context.require(ContextKeys.RESIDUE_STATES);

        MissingHeavyAtomContext missingHeavyAtomContext = missingHeavyAtomContext(context);
        BuildResult result = buildTopology(residues, states, AmberResidueTemplateLibrary.getInstance(),
                context, missingHeavyAtomContext);
        context.put(ContextKeys.PROTEIN_TOPOLOGY, result.topology());
        context.put(ContextKeys.TOPOLOGY_BUILD_REPORT, result.report());
    }

    private BuildResult buildTopology(List<Residue> residues,
                                      Map<String, ResidueState> states,
                                      AmberResidueTemplateLibrary amber,
                                      PipelineContext context,
                                      MissingHeavyAtomContext missingHeavyAtomContext) {
        List<Atom> allAtoms = new ArrayList<>();
        List<ResidueAtoms> residueAtoms = new ArrayList<>();
        List<String> assignedTemplates = new ArrayList<>();
        List<MissingHeavyAtomReport.Entry> missingHeavyAtoms = new ArrayList<>();
        int atomOffset = 0;
        int templateBondCount = 0;

        LinkedHashSet<EdgeKey> edgeKeys = new LinkedHashSet<>();
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

            Map<String, Integer> atomIndices = indexAtoms(residue, atomOffset);
            missingHeavyAtoms.addAll(missingHeavyAtoms(residue, template, state.amberTemplateName(),
                    atomIndices, missingHeavyAtomContext));
            for (BondTemplate bond : template.getBonds()) {
                Integer i = atomIndices.get(bond.getAtom1());
                Integer j = atomIndices.get(bond.getAtom2());
                if (i == null || j == null) continue;
                edgeKeys.add(new EdgeKey(i, j));
                templateBondCount++;
            }

            allAtoms.addAll(residue.getAtoms());
            residueAtoms.add(new ResidueAtoms(residue, atomIndices));
            assignedTemplates.add(residueKey(residue) + " -> " + state.amberTemplateName());
            atomOffset += residue.getAtoms().size();
        }

        if (!missingHeavyAtoms.isEmpty()) {
            MissingHeavyAtomReport report = new MissingHeavyAtomReport(
                    missingHeavyAtoms.size(),
                    missingHeavyAtomContext.pocketCenter() != null,
                    missingHeavyAtomContext.proximityCutoff(),
                    missingHeavyAtoms);
            context.put(ContextKeys.MISSING_HEAVY_ATOM_REPORT, report);
            MissingHeavyAtomReport.Entry first = missingHeavyAtoms.getFirst();
            throw new IllegalStateException("Missing heavy atom '" + first.atomName()
                    + "' required by Amber template '" + first.templateName()
                    + "' for " + first.residueLabel()
                    + "; " + missingHeavyAtoms.size()
                    + " missing heavy atom(s) recorded in "
                    + ContextKeys.MISSING_HEAVY_ATOM_REPORT);
        }

        int peptideBondCount = addPeptideBonds(residueAtoms, edgeKeys);
        int disulfideBondCount = addDisulfideBonds(residueAtoms, states, edgeKeys);
        List<Topology.Edge> edges = edgeKeys.stream()
                .map(edge -> new Topology.Edge(edge.indexA(), edge.indexB(),
                        distance(allAtoms.get(edge.indexA()), allAtoms.get(edge.indexB()))))
                .toList();

        Topology topology = new Topology(allAtoms.size(), edges);
        TopologyBuildReport report = new TopologyBuildReport(
                residues.size(),
                allAtoms.size(),
                topology.getBondCount(),
                templateBondCount,
                peptideBondCount,
                disulfideBondCount,
                assignedTemplates);
        return new BuildResult(topology, report);
    }

    private Map<String, Integer> indexAtoms(Residue residue, int atomOffset) {
        Map<String, Integer> atomIndices = new LinkedHashMap<>();
        List<Atom> atoms = residue.getAtoms();
        for (int i = 0; i < atoms.size(); i++) {
            Atom atom = atoms.get(i);
            Integer previous = atomIndices.put(atom.getName(), atomOffset + i);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate atom '" + atom.getName()
                        + "' in " + residueLabel(residue));
            }
        }
        return atomIndices;
    }

    private List<MissingHeavyAtomReport.Entry> missingHeavyAtoms(Residue residue, ResidueTemplate template,
                                                                 String templateName,
                                                                 Map<String, Integer> atomIndices,
                                                                 MissingHeavyAtomContext context) {
        List<MissingHeavyAtomReport.Entry> missing = new ArrayList<>();
        Double distanceToPocketCenter = residueDistanceToPocketCenter(residue, context.pocketCenter());
        boolean nearPocket = distanceToPocketCenter != null
                && distanceToPocketCenter <= context.proximityCutoff();
        for (var atomTemplate : template.getAtoms()) {
            String atomName = atomTemplate.getName();
            if (isHydrogenName(atomName)) continue;
            if (!atomIndices.containsKey(atomName)) {
                missing.add(new MissingHeavyAtomReport.Entry(
                        residueKey(residue),
                        residueLabel(residue),
                        templateName,
                        atomName,
                        distanceToPocketCenter,
                        nearPocket));
            }
        }
        return missing;
    }

    private MissingHeavyAtomContext missingHeavyAtomContext(PipelineContext context) {
        Point3D pocketCenter = null;
        Object pocketValue = context.get(ContextKeys.POCKET);
        if (pocketValue instanceof Pocket pocket) {
            try {
                pocketCenter = PocketGeometry.calculateCenter(pocket);
            } catch (IllegalArgumentException ignored) {
                pocketCenter = null;
            }
        }
        double cutoff = parseDouble(context.get(ContextKeys.POCKET_PROXIMITY_CUTOFF),
                DEFAULT_POCKET_PROXIMITY_CUTOFF);
        return new MissingHeavyAtomContext(pocketCenter, cutoff);
    }

    private Double residueDistanceToPocketCenter(Residue residue, Point3D pocketCenter) {
        if (pocketCenter == null) {
            return null;
        }
        Point3D center = residueHeavyAtomCenter(residue);
        return center == null ? null : distance(center, pocketCenter);
    }

    private Point3D residueHeavyAtomCenter(Residue residue) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        int count = 0;
        for (Atom atom : residue.getAtoms()) {
            if (isHydrogenName(atom.getName())) continue;
            Point3D position = atom.getPosition();
            if (position == null) continue;
            x += position.x();
            y += position.y();
            z += position.z();
            count++;
        }
        if (count == 0) {
            return null;
        }
        return new Point3D(x / count, y / count, z / count);
    }

    private int addPeptideBonds(List<ResidueAtoms> residues, Set<EdgeKey> edges) {
        int count = 0;
        for (int i = 0; i < residues.size() - 1; i++) {
            ResidueAtoms current = residues.get(i);
            ResidueAtoms next = residues.get(i + 1);
            if (!isConsecutive(current.residue(), next.residue())) continue;
            Integer cIndex = current.atomIndices().get("C");
            Integer nIndex = next.atomIndices().get("N");
            if (cIndex == null || nIndex == null) {
                throw new IllegalStateException("Cannot add peptide bond between "
                        + residueLabel(current.residue()) + " and " + residueLabel(next.residue())
                        + "; missing C or N");
            }
            double distance = distance(atomAt(current, cIndex), atomAt(next, nIndex));
            if (distance < MIN_PEPTIDE_BOND || distance > MAX_PEPTIDE_BOND) {
                throw new IllegalStateException("Peptide bond distance out of range between "
                        + residueLabel(current.residue()) + " C and " + residueLabel(next.residue())
                        + " N: " + distance);
            }
            if (edges.add(new EdgeKey(cIndex, nIndex))) {
                count++;
            }
        }
        return count;
    }

    private int addDisulfideBonds(List<ResidueAtoms> residues, Map<String, ResidueState> states,
                                  Set<EdgeKey> edges) {
        int count = 0;
        for (int i = 0; i < residues.size(); i++) {
            ResidueAtoms first = residues.get(i);
            ResidueState firstState = states.get(residueKey(first.residue()));
            if (firstState == null || !firstState.disulfide()) continue;
            Integer firstSg = first.atomIndices().get("SG");
            if (firstSg == null) {
                throw new IllegalStateException("Disulfide residue missing SG: " + residueLabel(first.residue()));
            }
            for (int j = i + 1; j < residues.size(); j++) {
                ResidueAtoms second = residues.get(j);
                ResidueState secondState = states.get(residueKey(second.residue()));
                if (secondState == null || !secondState.disulfide()) continue;
                Integer secondSg = second.atomIndices().get("SG");
                if (secondSg == null) {
                    throw new IllegalStateException("Disulfide residue missing SG: " + residueLabel(second.residue()));
                }
                double distance = distance(atomAt(first, firstSg), atomAt(second, secondSg));
                if (distance >= MIN_DISULFIDE_BOND && distance <= MAX_DISULFIDE_BOND) {
                    if (edges.add(new EdgeKey(firstSg, secondSg))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private Atom atomAt(ResidueAtoms residueAtoms, int flatIndex) {
        int local = flatIndex - residueAtoms.atomIndices().values().stream().mapToInt(Integer::intValue).min().orElse(0);
        return residueAtoms.residue().getAtoms().get(local);
    }

    private boolean isConsecutive(Residue previous, Residue current) {
        return Objects.equals(previous.getChain(), current.getChain())
                && (current.getNumber() == previous.getNumber()
                || current.getNumber() == previous.getNumber() + 1);
    }

    private boolean isHydrogenName(String atomName) {
        return atomName != null && atomName.startsWith("H");
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

    private double distance(Atom a, Atom b) {
        return distance(a.getPosition(), b.getPosition());
    }

    private double distance(Point3D a, Point3D b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double parseDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.doubleValue();
        return Double.parseDouble(value.toString());
    }

    private record ResidueAtoms(Residue residue, Map<String, Integer> atomIndices) {
    }

    private record MissingHeavyAtomContext(Point3D pocketCenter, double proximityCutoff) {
    }

    private record EdgeKey(int indexA, int indexB) {
        private EdgeKey {
            if (indexA > indexB) {
                int swap = indexA;
                indexA = indexB;
                indexB = swap;
            }
        }
    }

    private record BuildResult(Topology topology, TopologyBuildReport report) {
    }
}
