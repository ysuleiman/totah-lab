package totah.lab.pipeline.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.protein.Topology;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopologyBuilderStageTest {

    @TempDir
    Path tempDir;

    @Test
    void requiresHydrogenOptimizationReport() {
        PipelineContext context = contextWith(List.of(nAlanine(1), cLysine(2)));
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "ALA", "NALA"),
                state("A:2", "LYS", "CLYS")));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new TopologyBuilderStage().run(context));

        assertTrue(error.getMessage().contains(ContextKeys.HYDROGEN_OPTIMIZATION_REPORT));
    }

    @Test
    void rejectsEmptyResidues() {
        PipelineContext context = contextWith(List.of());
        context.put(ContextKeys.HYDROGEN_OPTIMIZATION_REPORT, optimizationReport(0));
        context.put(ContextKeys.RESIDUE_STATES, Map.of());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new TopologyBuilderStage().run(context));

        assertTrue(error.getMessage().contains("Run HydrogenOptimizationStage first"));
    }

    @Test
    void rejectsResidueWithoutState() {
        PipelineContext context = contextWith(List.of(nAlanine(1), cLysine(2)));
        context.put(ContextKeys.HYDROGEN_OPTIMIZATION_REPORT, optimizationReport(2));
        context.put(ContextKeys.RESIDUE_STATES, states(state("A:1", "ALA", "NALA")));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new TopologyBuilderStage().run(context));

        assertTrue(error.getMessage().contains("Missing residue state"));
    }

    @Test
    void buildsAmberTemplateTopologyWithPeptideBond() {
        PipelineContext context = contextWith(List.of(nAlanine(1), cLysine(2)));
        context.put(ContextKeys.HYDROGEN_OPTIMIZATION_REPORT, optimizationReport(2));
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "ALA", "NALA"),
                state("A:2", "LYS", "CLYS")));

        new TopologyBuilderStage().run(context);

        Topology topology = context.require(ContextKeys.PROTEIN_TOPOLOGY);
        assertTrue(hasEdge(topology, 0, 1), "NALA N-CA bond missing");
        assertTrue(hasEdge(topology, 1, 2), "NALA CA-C bond missing");
        assertTrue(hasEdge(topology, 1, 4), "NALA CA-CB bond missing");
        assertTrue(hasEdge(topology, 2, 3), "NALA C-O bond missing");
        assertTrue(hasEdge(topology, 2, 5), "peptide C-N bond missing");
        assertTrue(hasEdge(topology, 7, 8), "CLYS C-O bond missing");
        assertTrue(hasEdge(topology, 7, 14), "CLYS C-OXT bond missing");
        assertTrue(hasEdge(topology, 13, 14) == false, "OXT should not bond to NZ");
        assertFalse(hasEdge(topology, 0, 2), "non-template N-C 1-3 pair bonded");

        TopologyBuildReport report = context.require(ContextKeys.TOPOLOGY_BUILD_REPORT);
        assertEquals(2, report.residueCount());
        assertEquals(15, report.atomCount());
        assertEquals(1, report.peptideBondCount());
        assertEquals(0, report.disulfideBondCount());
        assertEquals(topology.getBondCount(), report.bondCount());
        assertEquals(List.of("A:1 -> NALA", "A:2 -> CLYS"), report.assignedTemplates());
    }

    @Test
    void rejectsMissingTemplateHeavyAtom() {
        Residue broken = cLysine(2).toBuilder()
                .atoms(cLysine(2).getAtoms().stream()
                        .filter(atom -> !"NZ".equals(atom.getName()))
                        .toList())
                .build();
        PipelineContext context = contextWith(List.of(nAlanine(1), broken));
        context.put(ContextKeys.HYDROGEN_OPTIMIZATION_REPORT, optimizationReport(2));
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "ALA", "NALA"),
                state("A:2", "LYS", "CLYS")));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new TopologyBuilderStage().run(context));

        assertTrue(error.getMessage().contains("Missing heavy atom 'NZ'"));
    }

    @Test
    void rejectsOutOfRangePeptideBondDistance() {
        Residue farLys = cLysine(2).toBuilder()
                .atoms(cLysine(2).getAtoms().stream()
                        .map(atom -> atom.toBuilder()
                                .position(new Point3D(atom.getPosition().x() + 10.0,
                                        atom.getPosition().y(), atom.getPosition().z()))
                                .build())
                        .toList())
                .build();
        PipelineContext context = contextWith(List.of(nAlanine(1), farLys));
        context.put(ContextKeys.HYDROGEN_OPTIMIZATION_REPORT, optimizationReport(2));
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "ALA", "NALA"),
                state("A:2", "LYS", "CLYS")));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new TopologyBuilderStage().run(context));

        assertTrue(error.getMessage().contains("Peptide bond distance out of range"));
    }

    @Test
    void addsDisulfideBondFromResidueStates() {
        PipelineContext context = contextWith(List.of(nCyx(1), cCyx(2)));
        context.put(ContextKeys.HYDROGEN_OPTIMIZATION_REPORT, optimizationReport(2));
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "CYS", "NCYX", true),
                state("A:2", "CYS", "CCYX", true)));

        new TopologyBuilderStage().run(context);

        Topology topology = context.require(ContextKeys.PROTEIN_TOPOLOGY);
        assertTrue(hasEdge(topology, 5, 11), "SG-SG disulfide bond missing");

        TopologyBuildReport report = context.require(ContextKeys.TOPOLOGY_BUILD_REPORT);
        assertEquals(1, report.disulfideBondCount());
    }

    @Test
    void reportListsAreDefensiveCopies() {
        PipelineContext context = contextWith(List.of(nAlanine(1), cLysine(2)));
        context.put(ContextKeys.HYDROGEN_OPTIMIZATION_REPORT, optimizationReport(2));
        context.put(ContextKeys.RESIDUE_STATES, states(
                state("A:1", "ALA", "NALA"),
                state("A:2", "LYS", "CLYS")));

        new TopologyBuilderStage().run(context);

        TopologyBuildReport report = context.require(ContextKeys.TOPOLOGY_BUILD_REPORT);
        assertThrows(UnsupportedOperationException.class,
                () -> report.assignedTemplates().add("A:9 -> ALA"));
    }

    private PipelineContext contextWith(List<Residue> residues) {
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));
        context.put(ContextKeys.PROTEIN_RESIDUES, residues);
        return context;
    }

    private HydrogenOptimizationReport optimizationReport(int residueCount) {
        return new HydrogenOptimizationReport(residueCount, residueCount, 0, 0, List.of());
    }

    private Map<String, ResidueState> states(ResidueState... states) {
        Map<String, ResidueState> map = new LinkedHashMap<>();
        for (ResidueState state : states) {
            map.put(state.residueKey(), state);
        }
        return map;
    }

    private ResidueState state(String key, String residueName, String amberTemplate) {
        return state(key, residueName, amberTemplate, false);
    }

    private ResidueState state(String key, String residueName, String amberTemplate, boolean disulfide) {
        return new ResidueState(key, residueName, residueName, amberTemplate,
                amberTemplate.startsWith("N"), amberTemplate.startsWith("C"), disulfide, "");
    }

    private Residue nAlanine(int number) {
        return residue("ALA", number,
                atom("N", "N", 0.000, 0.000, 0.000),
                atom("CA", "C", 1.460, 0.000, 0.000),
                atom("C", "C", 2.005, 1.419, 0.000),
                atom("O", "O", 1.230, 2.375, 0.000),
                atom("CB", "C", 1.460, -1.089, -1.089));
    }

    private Residue cLysine(int number) {
        return residue("LYS", number,
                atom("N", "N", 3.319, 1.628, 0.000),
                atom("CA", "C", 4.259, 0.510, 0.000),
                atom("C", "C", 3.523, -0.822, 0.000),
                atom("O", "O", 4.159, -1.876, 0.000),
                atom("CB", "C", 5.716, 1.015, 0.000),
                atom("CG", "C", 6.689, -0.182, 0.000),
                atom("CD", "C", 8.146, 0.323, 0.000),
                atom("CE", "C", 9.119, -0.874, 0.000),
                atom("NZ", "N", 10.500, -0.395, 0.000),
                atom("OXT", "O", 2.350, -1.910, 0.000));
    }

    private Residue nCyx(int number) {
        return residue("CYS", number,
                atom("N", "N", 0.0, 0.0, 0.0),
                atom("CA", "C", 1.4, 0.0, 0.0),
                atom("C", "C", 2.0, 1.3, 0.0),
                atom("O", "O", 1.4, 2.3, 0.0),
                atom("CB", "C", 1.6, -0.8, 1.2),
                atom("SG", "S", 1.8, -0.8, 2.9));
    }

    private Residue cCyx(int number) {
        return residue("CYS", number,
                atom("N", "N", 3.2, 1.5, 0.0),
                atom("CA", "C", 4.2, 0.4, 0.0),
                atom("C", "C", 3.5, -0.9, 0.0),
                atom("O", "O", 4.1, -1.9, 0.0),
                atom("CB", "C", 3.7, 0.6, 1.3),
                atom("SG", "S", 1.8, -0.8, 4.9),
                atom("OXT", "O", 2.4, -1.9, 0.0));
    }

    private Residue residue(String name, int number, Atom... atoms) {
        return Residue.builder()
                .name(name)
                .chain("A")
                .number(number)
                .insertionCode(' ')
                .atoms(List.of(atoms))
                .build();
    }

    private Atom atom(String name, String element, double x, double y, double z) {
        return Atom.builder()
                .name(name)
                .position(new Point3D(x, y, z))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(80.0)
                .element(Element.builder()
                        .symbol(element)
                        .atomicNumber(0)
                        .atomicMass(0.0)
                        .covalentRadius(0.0)
                        .vdwRadius(0.0)
                        .build())
                .build();
    }

    private boolean hasEdge(Topology topology, int i, int j) {
        return topology.getEdges().stream()
                .anyMatch(edge -> (edge.indexA() == i && edge.indexB() == j)
                        || (edge.indexA() == j && edge.indexB() == i));
    }
}
