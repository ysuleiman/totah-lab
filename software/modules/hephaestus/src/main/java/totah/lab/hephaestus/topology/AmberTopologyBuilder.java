package totah.lab.hephaestus.topology;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.amber.AmberResidueTemplateLibrary;
import totah.lab.hephaestus.amber.ResidueTemplate;
import totah.lab.hephaestus.receptor.residue.ResidueState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AmberTopologyBuilder implements TopologyBuilder {

    private static final double MIN_PEPTIDE_BOND = 1.15;
    private static final double MAX_PEPTIDE_BOND = 1.55;
    private static final double MIN_DISULFIDE_BOND = 1.75;
    private static final double MAX_DISULFIDE_BOND = 2.35;

    private final AmberResidueTemplateLibrary templates;

    public AmberTopologyBuilder() {
        this(AmberResidueTemplateLibrary.getInstance());
    }

    public AmberTopologyBuilder(
            AmberResidueTemplateLibrary templates) {
        this.templates = Objects.requireNonNull(templates, "templates");
    }

    @Override
    public BuildResult build(
            Structure structure,
            Map<String, ResidueState> residueStates) {

        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(residueStates, "residueStates");

        List<Atom> atoms = new ArrayList<>();
        List<LocatedResidue> located = new ArrayList<>();
        List<String> assignments = new ArrayList<>();
        List<MissingHeavyAtomReport.Entry> missing = new ArrayList<>();
        Set<EdgeKey> edgeKeys = new LinkedHashSet<>();
        int templateBondCount = 0;

        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                String key = residueKey(chain.id(), residue);
                ResidueState state = residueStates.get(key);
                if (state == null) {
                    throw new IllegalStateException(
                            "Missing residue state for " + label(chain.id(), residue));
                }

                ResidueTemplate template = templates.getTemplate(
                        state.amberTemplateName());
                if (template == null) {
                    throw new IllegalArgumentException(
                            "No Amber template '" + state.amberTemplateName()
                                    + "' for " + label(chain.id(), residue));
                }

                Map<String, Integer> indices = indexAtoms(
                        chain.id(), residue, atoms.size());

                for (var atomTemplate : template.getAtoms()) {
                    if (!isHydrogenName(atomTemplate.getName())
                            && !indices.containsKey(atomTemplate.getName())) {
                        missing.add(new MissingHeavyAtomReport.Entry(
                                key,
                                label(chain.id(), residue),
                                state.amberTemplateName(),
                                atomTemplate.getName()));
                    }
                }

                for (var bond : template.getBonds()) {
                    Integer first = indices.get(bond.getAtom1());
                    Integer second = indices.get(bond.getAtom2());
                    if (first != null && second != null
                            && edgeKeys.add(new EdgeKey(first, second))) {
                        templateBondCount++;
                    }
                }

                atoms.addAll(residue.getAtoms());
                located.add(new LocatedResidue(chain.id(), residue, indices));
                assignments.add(key + " -> " + state.amberTemplateName());
            }
        }

        if (!missing.isEmpty()) {
            MissingHeavyAtomReport report = new MissingHeavyAtomReport(
                    missing.size(), missing);
            MissingHeavyAtomReport.Entry first = missing.getFirst();
            throw new MissingHeavyAtomException(
                    "Missing heavy atom '" + first.atomName()
                            + "' required by Amber template '"
                            + first.templateName() + "' for "
                            + first.residueLabel(),
                    report);
        }

        int peptideBonds = addPeptideBonds(
                structure, located, atoms, edgeKeys);
        int disulfideBonds = addDisulfideBonds(
                located, residueStates, atoms, edgeKeys);

        List<Edge> edges = edgeKeys.stream()
                .map(edge -> new Edge(
                        edge.first(),
                        edge.second(),
                        atoms.get(edge.first()).getPosition().distance(
                                atoms.get(edge.second()).getPosition())))
                .toList();
        ProteinTopology topology = new ProteinTopology(atoms.size(), edges);

        return new BuildResult(
                topology,
                new TopologyBuildReport(
                        structure.getResidueCount(),
                        atoms.size(),
                        topology.bondCount(),
                        templateBondCount,
                        peptideBonds,
                        disulfideBonds,
                        assignments));
    }

    private int addPeptideBonds(
            Structure structure,
            List<LocatedResidue> located,
            List<Atom> atoms,
            Set<EdgeKey> edges) {

        Map<String, LocatedResidue> byKey = new LinkedHashMap<>();
        for (LocatedResidue value : located) {
            byKey.put(residueKey(value.chainId(), value.residue()), value);
        }

        int count = 0;
        for (Chain chain : structure.getChains()) {
            List<Residue> residues = chain.residues();
            for (int index = 0; index + 1 < residues.size(); index++) {
                Residue currentResidue = residues.get(index);
                Residue nextResidue = residues.get(index + 1);
                if (!isConsecutive(currentResidue, nextResidue)) {
                    continue;
                }

                LocatedResidue current = byKey.get(
                        residueKey(chain.id(), currentResidue));
                LocatedResidue next = byKey.get(
                        residueKey(chain.id(), nextResidue));
                Integer carbon = current.atomIndices().get("C");
                Integer nitrogen = next.atomIndices().get("N");
                if (carbon == null || nitrogen == null) {
                    throw new IllegalStateException(
                            "Cannot add peptide bond between "
                                    + label(chain.id(), currentResidue)
                                    + " and " + label(chain.id(), nextResidue)
                                    + "; missing C or N.");
                }

                double distance = distance(atoms, carbon, nitrogen);
                if (distance < MIN_PEPTIDE_BOND
                        || distance > MAX_PEPTIDE_BOND) {
                    throw new IllegalStateException(
                            "Peptide bond distance out of range between "
                                    + label(chain.id(), currentResidue)
                                    + " and " + label(chain.id(), nextResidue)
                                    + ": " + distance);
                }
                if (edges.add(new EdgeKey(carbon, nitrogen))) {
                    count++;
                }
            }
        }
        return count;
    }

    private int addDisulfideBonds(
            List<LocatedResidue> residues,
            Map<String, ResidueState> states,
            List<Atom> atoms,
            Set<EdgeKey> edges) {

        int count = 0;
        for (int firstIndex = 0; firstIndex < residues.size(); firstIndex++) {
            LocatedResidue first = residues.get(firstIndex);
            ResidueState firstState = states.get(
                    residueKey(first.chainId(), first.residue()));
            if (firstState == null || !firstState.disulfide()) continue;
            Integer firstSulfur = requireSulfur(first);

            for (int secondIndex = firstIndex + 1;
                 secondIndex < residues.size(); secondIndex++) {
                LocatedResidue second = residues.get(secondIndex);
                ResidueState secondState = states.get(
                        residueKey(second.chainId(), second.residue()));
                if (secondState == null || !secondState.disulfide()) continue;
                Integer secondSulfur = requireSulfur(second);
                double distance = distance(atoms, firstSulfur, secondSulfur);
                if (distance >= MIN_DISULFIDE_BOND
                        && distance <= MAX_DISULFIDE_BOND
                        && edges.add(new EdgeKey(firstSulfur, secondSulfur))) {
                    count++;
                }
            }
        }
        return count;
    }

    private Integer requireSulfur(LocatedResidue residue) {
        Integer sulfur = residue.atomIndices().get("SG");
        if (sulfur == null) {
            throw new IllegalStateException(
                    "Disulfide residue missing SG: "
                            + label(residue.chainId(), residue.residue()));
        }
        return sulfur;
    }

    private Map<String, Integer> indexAtoms(
            String chainId, Residue residue, int offset) {
        Map<String, Integer> indices = new LinkedHashMap<>();
        for (int index = 0; index < residue.getAtomCount(); index++) {
            String name = residue.getAtoms().get(index).getName();
            if (indices.put(name, offset + index) != null) {
                throw new IllegalArgumentException(
                        "Duplicate atom '" + name + "' in "
                                + label(chainId, residue));
            }
        }
        return Map.copyOf(indices);
    }

    private double distance(List<Atom> atoms, int first, int second) {
        return atoms.get(first).getPosition().distance(
                atoms.get(second).getPosition());
    }

    private boolean isConsecutive(Residue previous, Residue current) {
        return current.getNumber() == previous.getNumber()
                || current.getNumber() == previous.getNumber() + 1;
    }

    private boolean isHydrogenName(String name) {
        return name != null && name.startsWith("H");
    }

    private String residueKey(String chainId, Residue residue) {
        Character insertion = residue.getInsertionCode();
        return chainId + ":" + residue.getNumber()
                + (insertion == null ? "" : insertion);
    }

    private String label(String chainId, Residue residue) {
        return residue.getName() + " " + residueKey(chainId, residue);
    }

    private record LocatedResidue(
            String chainId,
            Residue residue,
            Map<String, Integer> atomIndices) {
    }

    private record EdgeKey(int first, int second) {
        private EdgeKey {
            if (first > second) {
                int swap = first;
                first = second;
                second = swap;
            }
        }
    }

    public static final class MissingHeavyAtomException
            extends IllegalStateException {
        private final MissingHeavyAtomReport report;

        public MissingHeavyAtomException(
                String message,
                MissingHeavyAtomReport report) {
            super(message);
            this.report = Objects.requireNonNull(report, "report");
        }

        public MissingHeavyAtomReport report() {
            return report;
        }
    }
}
