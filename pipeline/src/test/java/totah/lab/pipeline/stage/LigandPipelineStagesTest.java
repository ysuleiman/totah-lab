package totah.lab.pipeline.stage;

import org.biojava.nbio.structure.chem.ChemComp;
import org.biojava.nbio.structure.chem.ChemCompAtom;
import org.biojava.nbio.structure.chem.ChemCompBond;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.ligand.LigandPreparer;
import totah.lab.ligand.selection.LigandSelectionPolicy;
import totah.lab.ligand.selection.SelectedLigandPreparation;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.cleanup.ClassifiedResidue;
import totah.lab.pipeline.cleanup.ResidueDisposition;
import totah.lab.pipeline.cleanup.ResidueRole;
import totah.lab.pipeline.cleanup.StructureCleanupResult;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LigandPipelineStagesTest {

    @TempDir
    Path tempPath;

    @Test
    void publishesEachPreparationBoundaryIndependently() throws Exception {
        Residue ligand = residue();
        PipelineContext context = context(ligand);
        LigandPreparer preparer = new LigandPreparer(this::component);

        new LigandSelectionStage(true, new LigandSelectionPolicy()).run(context);
        assertSame(
                ligand,
                ((ClassifiedResidue) context.require(
                        ContextKeys.SELECTED_LIGAND)).residue());

        new LigandGraphBuilderStage(preparer).run(context);
        totah.lab.ligand.ccd.CcdLigandGraphResult graph =
                context.require(ContextKeys.LIGAND_GRAPH_RESULT);
        assertEquals(2, graph.graph().atoms().size());

        new LigandHydrogenationStage(preparer).run(context);
        assertTrue(context.containsKey(ContextKeys.LIGAND_HYDROGENATION_RESULT));

        new LigandChargeAssignmentStage(preparer).run(context);
        assertTrue(context.containsKey(ContextKeys.LIGAND_CHARGE_ASSIGNMENT_RESULT));

        new LigandAtomTypingStage(preparer).run(context);
        assertTrue(context.containsKey(ContextKeys.LIGAND_AD4_TYPING_RESULT));

        new LigandTorsionTreeStage(preparer).run(context);
        assertTrue(context.containsKey(ContextKeys.LIGAND_TORSION_TREE_RESULT));

        new LigandPdbqtExporterStage(preparer).run(context);
        SelectedLigandPreparation result =
                context.require(ContextKeys.LIGAND_PREPARATION_RESULT);
        Path output = context.require(ContextKeys.LIGAND_PDBQT_PATH);
        assertSame(ligand, result.selectedLigand().residue());
        assertTrue(Files.readString(output).contains("TORSDOF"));
    }

    @Test
    void graphStageReportsItsMissingPredecessor() {
        PipelineContext context = new PipelineContext(tempPath, tempPath);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new LigandGraphBuilderStage().run(context));

        assertTrue(exception.getMessage().contains(ContextKeys.SELECTED_LIGAND));
    }

    @Test
    void exporterReportsMissingIntermediateResult() {
        PipelineContext context = context(residue());
        new LigandSelectionStage(true, new LigandSelectionPolicy()).run(context);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new LigandPdbqtExporterStage().run(context));

        assertTrue(exception.getMessage().contains(ContextKeys.LIGAND_GRAPH_RESULT));
    }

    private PipelineContext context(Residue ligand) {
        ClassifiedResidue classified = new ClassifiedResidue(
                ligand,
                ResidueRole.LIGAND,
                ResidueDisposition.EXTRACT_AS_LIGAND,
                "test fixture");
        StructureCleanupResult cleanup = new StructureCleanupResult(
                List.of(), List.of(classified), List.of(), List.of(), List.of());
        return new PipelineContext(tempPath, tempPath)
                .with(ContextKeys.STRUCTURE_CLEANUP_RESULT, cleanup);
    }

    private Residue residue() {
        return Residue.builder()
                .name("LIG")
                .chain("A")
                .number(101)
                .insertionCode(' ')
                .atoms(List.of(atom("C1", 0.0), atom("C2", 1.5)))
                .build();
    }

    private Atom atom(String name, double x) {
        return Atom.builder()
                .name(name)
                .element(Element.C)
                .position(new Point3D(x, 0.0, 0.0))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .build();
    }

    private ChemComp component(String id) {
        ChemComp component = new ChemComp();
        component.setId(id);
        component.setAtoms(List.of(ccdAtom("C1"), ccdAtom("C2")));
        ChemCompBond bond = new ChemCompBond();
        bond.setAtomId1("C1");
        bond.setAtomId2("C2");
        bond.setValueOrder("SING");
        bond.setPdbxAromaticFlag("N");
        component.setBonds(List.of(bond));
        return component;
    }

    private ChemCompAtom ccdAtom(String id) {
        ChemCompAtom atom = new ChemCompAtom();
        atom.setAtomId(id);
        atom.setTypeSymbol("C");
        atom.setCharge(0);
        atom.setPdbxAromaticFlag("N");
        atom.setPdbxLeavingAtomFlag("N");
        return atom;
    }
}
