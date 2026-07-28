package totah.lab.ligand.selection;

import totah.lab.ligand.LigandPreparer;
import org.biojava.nbio.structure.chem.ChemComp;
import org.biojava.nbio.structure.chem.ChemCompAtom;
import org.biojava.nbio.structure.chem.ChemCompBond;
import org.junit.jupiter.api.Test;
import totah.lab.pipeline.cleanup.ClassifiedResidue;
import totah.lab.pipeline.cleanup.ResidueDisposition;
import totah.lab.pipeline.cleanup.ResidueRole;
import totah.lab.pipeline.cleanup.StructureCleanupResult;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LigandPreparationOrchestratorTest {

    @Test
    void reportsNoBoundLigandWithoutCallingChemistry() {
        AtomicInteger providerCalls = new AtomicInteger();
        LigandPreparationOrchestrator orchestrator = orchestrator(id -> {
            providerCalls.incrementAndGet();
            return component(id);
        });

        Optional<SelectedLigandPreparation> result =
                orchestrator.prepareOnly(cleanup(List.of(), List.of(), List.of(), List.of()));

        assertTrue(result.isEmpty());
        assertEquals(0, providerCalls.get());
    }

    @Test
    void preparesSoleCcdConfirmedExtractedLigand() {
        Residue ligand = residue("LIG", "A", 101, ' ');
        LigandPreparationOrchestrator orchestrator =
                orchestrator(id -> component(id));

        SelectedLigandPreparation result = orchestrator.prepareOnly(cleanup(
                        List.of(),
                        List.of(extracted(ligand, ResidueRole.LIGAND)),
                        List.of(),
                        List.of()))
                .orElseThrow();

        assertEquals(ligand, result.selectedLigand().residue());
        assertFalse(result.preparation().pdbqt().isBlank());
    }

    @Test
    void requiresExplicitSelectionWhenMultipleComponentsWereExtracted() {
        StructureCleanupResult cleanup = cleanup(
                List.of(),
                List.of(
                        extracted(residue("LIG", "A", 101, ' '), ResidueRole.LIGAND),
                        extracted(residue("DRG", "B", 202, 'A'), ResidueRole.LIGAND)),
                List.of(),
                List.of());

        LigandSelectionException exception = assertThrows(
                LigandSelectionException.class,
                () -> orchestrator(id -> component(id)).prepareOnly(cleanup));

        assertEquals(
                LigandSelectionFailure.AMBIGUOUS_SELECTION,
                exception.getFailure());
    }

    @Test
    void preparesExplicitlySelectedResidueIdentity() {
        Residue first = residue("LIG", "A", 101, ' ');
        Residue second = residue("DRG", "B", 202, 'A');
        AtomicReference<String> requestedComponent = new AtomicReference<>();
        StructureCleanupResult cleanup = cleanup(
                List.of(),
                List.of(
                        extracted(first, ResidueRole.LIGAND),
                        extracted(second, ResidueRole.LIGAND)),
                List.of(),
                List.of());

        SelectedLigandPreparation result = orchestrator(id -> {
            requestedComponent.set(id);
            return component(id);
        }).prepare(cleanup, new LigandSelection("DRG", "B", 202, 'A'));

        assertEquals(second, result.selectedLigand().residue());
        assertEquals("DRG", requestedComponent.get());
    }

    @Test
    void rejectsRetainedRemovedUnknownAndMissingSelectionsBeforeChemistry() {
        Residue receptor = residue("ALA", "A", 1, ' ');
        Residue unknown = residue("UNK", "A", 300, ' ');
        Residue water = residue("HOH", "A", 400, ' ');
        Residue ion = residue("ZN", "A", 500, ' ');
        StructureCleanupResult cleanup = cleanup(
                List.of(classified(
                        receptor,
                        ResidueRole.STANDARD_AMINO_ACID,
                        ResidueDisposition.KEEP_IN_RECEPTOR)),
                List.of(extracted(unknown, ResidueRole.UNKNOWN)),
                List.of(classified(
                        water,
                        ResidueRole.WATER,
                        ResidueDisposition.REMOVE)),
                List.of(classified(
                        ion,
                        ResidueRole.METAL_OR_ION,
                        ResidueDisposition.REMOVE)));
        LigandPreparationOrchestrator orchestrator = orchestrator(
                id -> {
                    throw new AssertionError("Rejected selections must not invoke chemistry");
                });

        assertFailure(
                LigandSelectionFailure.NOT_EXTRACTED_AS_LIGAND,
                () -> orchestrator.prepare(cleanup, LigandSelection.from(receptor)));
        assertFailure(
                LigandSelectionFailure.NOT_EXTRACTED_AS_LIGAND,
                () -> orchestrator.prepare(cleanup, LigandSelection.from(water)));
        assertFailure(
                LigandSelectionFailure.NOT_EXTRACTED_AS_LIGAND,
                () -> orchestrator.prepare(cleanup, LigandSelection.from(ion)));
        assertFailure(
                LigandSelectionFailure.UNSUPPORTED_CLASSIFICATION,
                () -> orchestrator.prepare(cleanup, LigandSelection.from(unknown)));
        assertFailure(
                LigandSelectionFailure.SELECTION_NOT_FOUND,
                () -> orchestrator.prepare(
                        cleanup,
                        new LigandSelection("ABS", "Z", 999, ' ')));
    }

    @Test
    void rejectsSoleUnknownFallbackInsteadOfSilentlyPreparingIt() {
        StructureCleanupResult cleanup = cleanup(
                List.of(),
                List.of(extracted(
                        residue("UNK", "A", 300, ' '),
                        ResidueRole.UNKNOWN)),
                List.of(),
                List.of());

        LigandSelectionException exception = assertThrows(
                LigandSelectionException.class,
                () -> orchestrator(id -> component(id)).prepareOnly(cleanup));

        assertEquals(
                LigandSelectionFailure.UNSUPPORTED_CLASSIFICATION,
                exception.getFailure());
    }

    private LigandPreparationOrchestrator orchestrator(
            org.biojava.nbio.structure.chem.ChemCompProvider provider) {
        return new LigandPreparationOrchestrator(new LigandPreparer(provider));
    }

    private StructureCleanupResult cleanup(
            List<ClassifiedResidue> receptor,
            List<ClassifiedResidue> ligands,
            List<ClassifiedResidue> waters,
            List<ClassifiedResidue> metals) {
        return new StructureCleanupResult(
                receptor, ligands, waters, metals, List.of());
    }

    private ClassifiedResidue extracted(Residue residue, ResidueRole role) {
        return classified(
                residue, role, ResidueDisposition.EXTRACT_AS_LIGAND);
    }

    private ClassifiedResidue classified(
            Residue residue,
            ResidueRole role,
            ResidueDisposition disposition) {
        return new ClassifiedResidue(residue, role, disposition, "test fixture");
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
                .atoms(List.of(
                        atom("C1", 0.0),
                        atom("C2", 1.5)))
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

    private void assertFailure(
            LigandSelectionFailure failure,
            org.junit.jupiter.api.function.Executable executable) {
        LigandSelectionException exception = assertThrows(
                LigandSelectionException.class, executable);
        assertEquals(failure, exception.getFailure());
    }
}
