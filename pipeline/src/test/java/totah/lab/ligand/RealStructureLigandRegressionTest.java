package totah.lab.ligand;

import totah.lab.ligand.selection.LigandPreparationOrchestrator;
import totah.lab.ligand.selection.LigandSelection;
import totah.lab.ligand.selection.LigandSelectionException;
import totah.lab.ligand.selection.LigandSelectionFailure;
import org.biojava.nbio.structure.chem.ReducedChemCompProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.io.StructureIO;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.cleanup.StructureCleanupResult;
import totah.lab.pipeline.stage.StructureCleanupStage;
import totah.lab.protein.Atom;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.net.URL;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealStructureLigandRegressionTest {

    @TempDir
    Path tempDir;

    @Test
    void preparesDepositedFourE1jGlycerolDeterministicallyOffline() throws Exception {
        Residue deposited = glycerol("A");
        List<String> depositedNames = atomNames(deposited);
        List<Point3D> depositedCoordinates = positions(deposited);
        LigandPreparer preparer = new LigandPreparer(new ReducedChemCompProvider());

        LigandPreparationResult first = preparer.prepare(deposited);
        LigandPreparationResult second = preparer.prepare(deposited);

        assertEquals(6, deposited.getAtomCount());
        assertEquals(14, first.graph().atoms().size());
        assertEquals(8, first.hydrogenation().generatedHydrogenNames().size());
        assertEquals(depositedNames, atomNames(first.graph().atoms().subList(0, 6)));
        assertEquals(depositedCoordinates, positions(first.graph().atoms().subList(0, 6)));
        assertTrue(first.graph().atoms().stream()
                .allMatch(atom -> Double.isFinite(atom.getPosition().x())
                        && Double.isFinite(atom.getPosition().y())
                        && Double.isFinite(atom.getPosition().z())));
        assertTrue(first.graph().atoms().stream()
                .allMatch(atom -> Double.isFinite(atom.getCharge())));
        assertEquals(
                first.chargeAssignment().totalFormalCharge(),
                first.chargeAssignment().totalPartialCharge(),
                1.0e-9);
        assertTrue(first.graph().atoms().stream()
                .allMatch(atom -> atom.getAutoDockType() != null));
        assertTrue(first.torsionTree().torsionalDegreesOfFreedom() > 0);
        assertEquals(first.pdbqt(), second.pdbqt());
        assertPdbqtIntegrity(first);
    }

    @Test
    void rejectsMissingHeavyAtomFromDepositedFourE1jLigand() throws Exception {
        Residue deposited = glycerol("A");
        Residue missingOxygen = deposited.toBuilder()
                .atoms(deposited.getAtoms().stream()
                        .filter(atom -> !"O3".equals(atom.getName()))
                        .toList())
                .build();

        UnsupportedLigandException exception = assertThrows(
                UnsupportedLigandException.class,
                () -> new LigandPreparer(new ReducedChemCompProvider())
                        .prepare(missingOxygen));

        assertEquals(LigandUnsupportedReason.MISSING_HEAVY_ATOMS, exception.getReason());
        assertTrue(exception.getMessage().contains("O3"));
    }

    @Test
    void cleanupRequiresSelectionForTwoDepositedGlycerolsThenPolicyExcludesThem()
            throws Exception {
        List<Residue> loaded = StructureIO.load(resourcePath()).getResidues();
        PipelineContext context = new PipelineContext(tempDir, tempDir.resolve("run"));
        context.put(ContextKeys.PROTEIN_RESIDUES, loaded);
        new StructureCleanupStage().run(context);
        StructureCleanupResult cleanup =
                context.require(ContextKeys.STRUCTURE_CLEANUP_RESULT);

        assertEquals(
                List.of("A", "B"),
                cleanup.extractedLigands().stream()
                        .filter(classified -> "GOL".equals(classified.residue().getName()))
                        .map(classified -> classified.residue().getChain())
                        .toList());

        LigandPreparationOrchestrator orchestrator =
                new LigandPreparationOrchestrator(
                        new LigandPreparer(new ReducedChemCompProvider()));
        LigandSelectionException ambiguous = assertThrows(
                LigandSelectionException.class,
                () -> orchestrator.prepareOnly(cleanup));
        assertEquals(
                LigandSelectionFailure.AMBIGUOUS_SELECTION,
                ambiguous.getFailure());

        LigandSelectionException excluded = assertThrows(
                LigandSelectionException.class,
                () -> orchestrator.prepare(
                        cleanup,
                        new LigandSelection("GOL", "A", 601, ' ')));
        assertEquals(
                LigandSelectionFailure.EXCLUDED_BY_POLICY,
                excluded.getFailure());
    }

    private Residue glycerol(String chain) throws Exception {
        return StructureIO.load(resourcePath()).getResidues().stream()
                .filter(residue -> "GOL".equals(residue.getName()))
                .filter(residue -> chain.equals(residue.getChain()))
                .filter(residue -> residue.getNumber() == 601)
                .findFirst()
                .orElseThrow();
    }

    private Path resourcePath() throws Exception {
        URL resource = getClass().getResource("/ligand/4E1J-glycerol-panel.pdb");
        if (resource == null) {
            throw new AssertionError("4E1J glycerol resource is missing");
        }
        return Path.of(resource.toURI());
    }

    private List<String> atomNames(Residue residue) {
        return atomNames(residue.getAtoms());
    }

    private List<String> atomNames(List<Atom> atoms) {
        return atoms.stream().map(Atom::getName).toList();
    }

    private List<Point3D> positions(Residue residue) {
        return positions(residue.getAtoms());
    }

    private List<Point3D> positions(List<Atom> atoms) {
        return atoms.stream().map(Atom::getPosition).toList();
    }

    private void assertPdbqtIntegrity(LigandPreparationResult prepared) {
        List<String> lines = prepared.pdbqt().lines().toList();
        List<String> atomLines = lines.stream()
                .filter(line -> line.startsWith("ATOM"))
                .toList();
        assertEquals(prepared.graph().atoms().size(), atomLines.size());
        assertEquals(1, lines.stream().filter("ROOT"::equals).count());
        assertEquals(1, lines.stream().filter("ENDROOT"::equals).count());
        assertEquals(
                prepared.torsionTree().torsionalDegreesOfFreedom(),
                lines.stream().filter(line -> line.startsWith("BRANCH ")).count());
        assertEquals(
                prepared.torsionTree().torsionalDegreesOfFreedom(),
                lines.stream().filter(line -> line.startsWith("ENDBRANCH ")).count());
        assertEquals(
                "TORSDOF " + prepared.torsionTree().torsionalDegreesOfFreedom(),
                lines.getLast());

        Set<Integer> serials = new HashSet<>();
        for (String line : atomLines) {
            serials.add(Integer.parseInt(line.substring(6, 11).trim()));
        }
        assertEquals(prepared.graph().atoms().size(), serials.size());
    }
}
