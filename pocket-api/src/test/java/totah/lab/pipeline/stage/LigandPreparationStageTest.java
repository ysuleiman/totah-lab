package totah.lab.pipeline.stage;

import org.biojava.nbio.structure.chem.ChemComp;
import org.biojava.nbio.structure.chem.ChemCompAtom;
import org.biojava.nbio.structure.chem.ChemCompBond;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.ligand.LigandPreparer;
import totah.lab.ligand.selection.LigandPreparationOrchestrator;
import totah.lab.ligand.selection.LigandSelection;
import totah.lab.ligand.selection.LigandSelectionException;
import totah.lab.ligand.selection.LigandSelectionFailure;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LigandPreparationStageTest {

    @TempDir
    Path tempPath;

    @Test
    void noExtractedLigandLeavesOutputsAbsent() throws Exception {
        PipelineContext context = context(cleanup(List.of()));

        stage().run(context);

        assertFalse(context.containsKey(ContextKeys.LIGAND_PREPARATION_RESULT));
        assertFalse(context.containsKey(ContextKeys.LIGAND_PDBQT));
        assertFalse(context.containsKey(ContextKeys.LIGAND_PDBQT_PATH));
        assertFalse(Files.exists(tempPath.resolve("prepared_ligand.pdbqt")));
    }

    @Test
    void preparesSoleLigandAndPublishesTypedAndFileOutputs() throws Exception {
        Residue ligand = residue("LIG", "A", 101, ' ');
        PipelineContext context = context(cleanup(List.of(extracted(ligand))));

        stage().run(context);

        SelectedLigandPreparation result =
                context.require(ContextKeys.LIGAND_PREPARATION_RESULT);
        String pdbqt = context.require(ContextKeys.LIGAND_PDBQT);
        Path outputPath = context.require(ContextKeys.LIGAND_PDBQT_PATH);
        assertSame(ligand, result.selectedLigand().residue());
        assertEquals(result.preparation().pdbqt(), pdbqt);
        assertEquals(tempPath.resolve("prepared_ligand.pdbqt"), outputPath);
        assertEquals(pdbqt, Files.readString(outputPath));
        assertTrue(pdbqt.contains("TORSDOF"));
    }

    @Test
    void usesExplicitSelectionForMultipleExtractedLigands() throws Exception {
        Residue first = residue("LIG", "A", 101, ' ');
        Residue second = residue("DRG", "B", 202, 'A');
        PipelineContext context = context(cleanup(List.of(
                extracted(first), extracted(second))));
        context.put(
                ContextKeys.LIGAND_SELECTION,
                new LigandSelection("DRG", "B", 202, 'A'));

        stage().run(context);

        SelectedLigandPreparation result =
                context.require(ContextKeys.LIGAND_PREPARATION_RESULT);
        assertSame(second, result.selectedLigand().residue());
    }

    @Test
    void multipleLigandsWithoutSelectionRemainAnExplicitFailure() {
        PipelineContext context = context(cleanup(List.of(
                extracted(residue("LIG", "A", 101, ' ')),
                extracted(residue("DRG", "B", 202, 'A')))));

        LigandSelectionException exception = assertThrows(
                LigandSelectionException.class,
                () -> stage().run(context));

        assertEquals(
                LigandSelectionFailure.AMBIGUOUS_SELECTION,
                exception.getFailure());
        assertFalse(Files.exists(tempPath.resolve("prepared_ligand.pdbqt")));
    }

    private LigandPreparationStage stage() {
        LigandPreparer preparer = new LigandPreparer(this::component);
        return new LigandPreparationStage(
                new LigandPreparationOrchestrator(preparer));
    }

    private PipelineContext context(StructureCleanupResult cleanupResult) {
        return new PipelineContext(tempPath, tempPath)
                .with(ContextKeys.STRUCTURE_CLEANUP_RESULT, cleanupResult);
    }

    private StructureCleanupResult cleanup(List<ClassifiedResidue> ligands) {
        return new StructureCleanupResult(
                List.of(), ligands, List.of(), List.of(), List.of());
    }

    private ClassifiedResidue extracted(Residue residue) {
        return new ClassifiedResidue(
                residue,
                ResidueRole.LIGAND,
                ResidueDisposition.EXTRACT_AS_LIGAND,
                "test fixture");
    }

    private Residue residue(
            String name,
            String chain,
            int number,
            char insertionCode) {
        return Residue.builder()
                .name(name)
                .chain(chain)
                .number(number)
                .insertionCode(insertionCode)
                .atoms(List.of(atom("C1", 0.0), atom("C2", 1.5)))
                .build();
    }

    private Atom atom(String name, double x) {
        return Atom.builder()
                .name(name)
                .element(Element.fromSymbol("C"))
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
