package totah.lab.ligand.ccd;

import totah.lab.ligand.LigandPreparationResult;
import totah.lab.ligand.LigandPreparer;
import totah.lab.ligand.charge.LigandChargeAssigner;
import totah.lab.ligand.charge.LigandChargeAssignmentResult;
import totah.lab.ligand.hydrogen.LigandHydrogenPlan;
import totah.lab.ligand.hydrogen.LigandHydrogenPlanner;
import totah.lab.ligand.hydrogen.LigandHydrogenationResult;
import totah.lab.ligand.hydrogen.LigandHydrogenator;
import totah.lab.ligand.hydrogen.LigandValenceException;
import totah.lab.ligand.torsion.LigandTorsionTreeBuilder;
import totah.lab.ligand.torsion.LigandTorsionTreeResult;
import totah.lab.ligand.typing.LigandAd4AtomTyper;
import totah.lab.ligand.typing.LigandAd4TypingResult;
import org.biojava.nbio.structure.chem.ChemComp;
import org.biojava.nbio.structure.chem.ChemCompAtom;
import org.biojava.nbio.structure.chem.ChemCompBond;
import org.biojava.nbio.structure.chem.DownloadChemCompProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.io.StructureIO;
import totah.lab.protein.Atom;
import totah.lab.chemistry.BondOrder;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CcdLigandGraphBuilderTest {

    private final CcdLigandGraphBuilder builder = new CcdLigandGraphBuilder();

    @TempDir
    Path tempDir;

    @Test
    void preservesDepositedOrderAndTransfersCcdChemistry() {
        Atom oxygen = atom("O1", "O", 1.0);
        Atom carbon = atom("C1", "C", 2.0);
        Atom nitrogen = atom("N1", "N", 3.0);
        Residue residue = residue(oxygen, carbon, nitrogen);
        ChemComp ccd = component(
                List.of(
                        ccdAtom("C1", "C", 0, "N", "N"),
                        ccdAtom("O1", "O", 0, "N", "N"),
                        ccdAtom("N1", "N", 1, "Y", "N"),
                        ccdAtom("H1", "H", 0, "N", "N")),
                List.of(
                        ccdBond("C1", "O1", "DOUB", "N"),
                        ccdBond("C1", "N1", "AROM", "Y"),
                        ccdBond("N1", "H1", "SING", "N")));

        CcdLigandGraphResult result = builder.build(residue, ccd);

        assertEquals(List.of(oxygen, carbon, nitrogen), result.graph().atoms());
        assertSame(oxygen, result.graph().atoms().get(0));
        assertEquals(List.of("O1", "C1", "N1"),
                result.graph().atomProperties().stream()
                        .map(property -> property.ccdAtomId())
                        .toList());
        assertEquals(0, result.graph().atomProperties().get(0).formalCharge());
        assertTrue(result.graph().atomProperties().get(2).aromatic());
        assertEquals(BondOrder.DOUBLE, result.graph().bonds().get(0).order());
        assertEquals(1, result.graph().bonds().get(0).atomIndexA());
        assertEquals(0, result.graph().bonds().get(0).atomIndexB());
        assertEquals(BondOrder.AROMATIC, result.graph().bonds().get(1).order());
        assertEquals(List.of("H1"), result.validationReport().missingHydrogens());
        assertEquals("H1", result.missingHydrogens().getFirst().ccdAtomId());
        assertEquals(2, result.missingHydrogens().getFirst().parentAtomIndex());
        assertTrue(result.validationReport().valid());

        LigandHydrogenPlan plan = new LigandHydrogenPlanner().plan(result);
        assertEquals(List.of("H1"), plan.hydrogens().stream()
                .map(MissingLigandHydrogen::ccdAtomId)
                .toList());
        assertTrue(plan.valenceReport().valid());
    }

    @Test
    void rejectsMissingDepositedHeavyAtom() {
        ChemComp ccd = component(
                List.of(
                        ccdAtom("C1", "C", 0, "N", "N"),
                        ccdAtom("O1", "O", 0, "N", "N")),
                List.of(ccdBond("C1", "O1", "SING", "N")));

        LigandGraphValidationException exception = assertThrows(
                LigandGraphValidationException.class,
                () -> builder.build(residue(atom("C1", "C", 0.0)), ccd));

        assertEquals(List.of("O1"), exception.getReport().missingHeavyAtoms());
    }

    @Test
    void rejectsExtraDepositedHeavyAtom() {
        ChemComp ccd = component(
                List.of(ccdAtom("C1", "C", 0, "N", "N")),
                List.of());

        LigandGraphValidationException exception = assertThrows(
                LigandGraphValidationException.class,
                () -> builder.build(
                        residue(atom("C1", "C", 0.0), atom("X1", "S", 1.0)),
                        ccd));

        assertEquals(List.of("X1"), exception.getReport().extraHeavyAtoms());
    }

    @Test
    void rejectsDuplicateDepositedAtomNames() {
        ChemComp ccd = component(
                List.of(ccdAtom("C1", "C", 0, "N", "N")),
                List.of());

        assertThrows(IllegalArgumentException.class,
                () -> builder.build(
                        residue(atom("C1", "C", 0.0), atom("C1", "C", 1.0)),
                        ccd));
    }

    @Test
    void rejectsOverValentCarbonBeforeCoordinateGeneration() {
        List<ChemCompAtom> atoms = new java.util.ArrayList<>();
        atoms.add(ccdAtom("C1", "C", 0, "N", "N"));
        List<ChemCompBond> bonds = new java.util.ArrayList<>();
        List<Atom> deposited = new java.util.ArrayList<>();
        deposited.add(atom("C1", "C", 0.0));
        for (int index = 1; index <= 5; index++) {
            String name = "H" + index;
            atoms.add(ccdAtom(name, "H", 0, "N", "N"));
            bonds.add(ccdBond("C1", name, "SING", "N"));
            deposited.add(atom(name, "H", index));
        }

        CcdLigandGraphResult graph = builder.build(
                residue(deposited.toArray(Atom[]::new)),
                component(atoms, bonds));

        LigandValenceException exception = assertThrows(
                LigandValenceException.class,
                () -> new LigandHydrogenPlanner().plan(graph));
        assertTrue(exception.getReport().violations().getFirst()
                .contains("exceeds supported valence"));
    }

    @Test
    void appendsHydrogenUsingDeterministicLocalCcdFrame() throws Exception {
        ChemCompAtom center = ccdAtom("C0", "C", 0, "N", "N");
        ChemCompAtom first = ccdAtom("C1", "C", 0, "N", "N");
        ChemCompAtom second = ccdAtom("C2", "C", 0, "N", "N");
        ChemCompAtom hydrogen = ccdAtom("H1", "H", 0, "N", "N");
        ideal(center, 0.0, 0.0, 0.0);
        ideal(first, 1.0, 0.0, 0.0);
        ideal(second, 0.0, 1.0, 0.0);
        ideal(hydrogen, 0.0, 0.0, 1.0);
        Residue deposited = residue(
                atom("C0", "C", 10.0, 0.0, 0.0),
                atom("C1", "C", 10.0, 1.0, 0.0),
                atom("C2", "C", 9.0, 0.0, 0.0));
        ChemComp ccd = component(
                List.of(center, first, second, hydrogen),
                List.of(
                        ccdBond("C0", "C1", "SING", "N"),
                        ccdBond("C0", "C2", "SING", "N"),
                        ccdBond("C0", "H1", "SING", "N")));

        CcdLigandGraphResult graph = builder.build(deposited, ccd);
        LigandHydrogenationResult result = new LigandHydrogenator().hydrogenate(graph);

        assertEquals(4, result.graph().atoms().size());
        assertSame(deposited.getAtoms().get(0), result.graph().atoms().get(0));
        assertEquals(List.of("H1"), result.generatedHydrogenNames());
        Point3D generated = result.graph().atoms().get(3).getPosition();
        assertEquals(10.0, generated.x(), 1.0e-10);
        assertEquals(0.0, generated.y(), 1.0e-10);
        assertEquals(1.0, generated.z(), 1.0e-10);
        assertEquals(3, result.graph().bonds().getLast().atomIndexB());
        assertEquals(null, result.graph().atomProperties().getLast().depositedAtomIndex());

        Path output = tempDir.resolve("ligand.pdbqt");
        LigandPreparationResult prepared =
                new LigandPreparer().prepareToPath(deposited, ccd, output);
        assertEquals(4, prepared.graph().atoms().size());
        assertEquals(prepared.pdbqt(), java.nio.file.Files.readString(output));
        assertTrue(prepared.pdbqt().startsWith("ROOT"));
        assertTrue(prepared.pdbqt().contains("TORSDOF "));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_ONLINE_CCD_TESTS", matches = "true")
    void buildsValidatedGraphForOneA4wQweFromOnlineCcd() throws Exception {
        Path pdb = Path.of(getClass().getResource("/pipeline/1A4W.pdb").toURI());
        Residue qwe = StructureIO.load(pdb, true, tempDir).getResidues().stream()
                .filter(residue -> "QWE".equals(residue.getName()))
                .filter(residue -> "H".equals(residue.getChain()))
                .filter(residue -> residue.getNumber() == 373)
                .findFirst()
                .orElseThrow();
        DownloadChemCompProvider provider =
                new DownloadChemCompProvider(tempDir.toString());
        ChemComp qweCcd = provider.getChemComp("QWE");

        CcdLigandGraphResult result = builder.build(qwe, qweCcd);

        assertTrue(result.validationReport().valid());
        assertTrue(result.validationReport().missingHeavyAtoms().isEmpty());
        assertTrue(result.validationReport().extraHeavyAtoms().isEmpty());
        assertEquals(qwe.getAtoms(), result.graph().atoms());
        assertEquals(
                qwe.getAtoms().stream().map(Atom::getName).toList(),
                result.graph().atomProperties().stream()
                        .map(property -> property.ccdAtomId())
                        .toList());
        assertTrue(result.graph().bonds().size() > 0);
        LigandHydrogenPlan plan = new LigandHydrogenPlanner().plan(result);
        assertTrue(plan.valenceReport().valid());
        assertEquals(result.validationReport().missingHydrogens().size(),
                plan.hydrogens().size());
        LigandHydrogenationResult hydrogenated =
                new LigandHydrogenator().hydrogenate(result);
        assertEquals(qwe.getAtomCount() + plan.hydrogens().size(),
                hydrogenated.graph().atoms().size());
        for (int index = 0; index < qwe.getAtomCount(); index++) {
            assertSame(qwe.getAtoms().get(index), hydrogenated.graph().atoms().get(index));
        }
        assertTrue(hydrogenated.graph().atoms().stream()
                .allMatch(atom -> Double.isFinite(atom.getPosition().x())
                        && Double.isFinite(atom.getPosition().y())
                        && Double.isFinite(atom.getPosition().z())));
        LigandChargeAssignmentResult charged =
                new LigandChargeAssigner().assign(hydrogenated.graph());
        assertEquals(charged.totalFormalCharge(), charged.totalPartialCharge(), 1.0e-9);
        assertTrue(charged.graph().atoms().stream()
                .allMatch(atom -> Double.isFinite(atom.getCharge())));
        LigandAd4TypingResult typed =
                new LigandAd4AtomTyper().assign(charged.graph());
        assertEquals(charged.graph().atoms().size(), typed.atomCount());
        assertTrue(typed.graph().atoms().stream()
                .allMatch(atom -> atom.getAutoDockType() != null));
        LigandTorsionTreeResult torsionTree =
                new LigandTorsionTreeBuilder().build(typed.graph());
        assertEquals(torsionTree.bondReport().rotatableBondCount(),
                torsionTree.torsionalDegreesOfFreedom());
        assertTrue(java.util.stream.IntStream
                .range(0, typed.graph().atoms().size())
                .allMatch(torsionTree.tree()::containsAtom));
        LigandPreparationResult prepared = new LigandPreparer(provider).prepare(qwe);
        assertEquals(typed.graph().atoms().size(), prepared.graph().atoms().size());
        assertTrue(prepared.pdbqt().startsWith("ROOT"));
        assertTrue(prepared.pdbqt().contains("TORSDOF "));
        validatePreparedPdbqt(prepared);
    }

    private void validatePreparedPdbqt(LigandPreparationResult prepared) {
        List<String> lines = prepared.pdbqt().lines().toList();
        List<String> atomLines = lines.stream()
                .filter(line -> line.startsWith("ATOM"))
                .toList();
        assertEquals(prepared.graph().atoms().size(), atomLines.size());
        assertEquals(1, lines.stream().filter("ROOT"::equals).count());
        assertEquals(1, lines.stream().filter("ENDROOT"::equals).count());
        assertEquals(prepared.torsionTree().torsionalDegreesOfFreedom(),
                lines.stream().filter(line -> line.startsWith("BRANCH ")).count());
        assertEquals(prepared.torsionTree().torsionalDegreesOfFreedom(),
                lines.stream().filter(line -> line.startsWith("ENDBRANCH ")).count());
        assertEquals("TORSDOF " + prepared.torsionTree().torsionalDegreesOfFreedom(),
                lines.getLast());

        Set<Integer> serials = new HashSet<>();
        double formattedChargeTotal = 0.0;
        for (String atomLine : atomLines) {
            String[] fields = atomLine.trim().split("\\s+");
            assertEquals(13, fields.length, atomLine);
            assertTrue(serials.add(Integer.parseInt(fields[1])), atomLine);
            assertTrue(Double.isFinite(Double.parseDouble(fields[6])), atomLine);
            assertTrue(Double.isFinite(Double.parseDouble(fields[7])), atomLine);
            assertTrue(Double.isFinite(Double.parseDouble(fields[8])), atomLine);
            double charge = Double.parseDouble(fields[11]);
            assertTrue(Double.isFinite(charge), atomLine);
            formattedChargeTotal += charge;
            assertTrue(java.util.Arrays.stream(totah.lab.topology.AutoDockType.values())
                    .anyMatch(type -> type.getSymbol().equals(fields[12])), atomLine);
        }
        assertEquals(prepared.graph().atoms().size(), serials.size());
        assertEquals(prepared.chargeAssignment().totalFormalCharge(),
                formattedChargeTotal,
                prepared.graph().atoms().size() * 5.1e-5);

        for (String line : lines) {
            if (!line.startsWith("BRANCH ") && !line.startsWith("ENDBRANCH ")) {
                continue;
            }
            String[] fields = line.split("\\s+");
            assertTrue(serials.contains(Integer.parseInt(fields[1])), line);
            assertTrue(serials.contains(Integer.parseInt(fields[2])), line);
        }
    }

    private Residue residue(Atom... atoms) {
        return Residue.builder()
                .name("LIG")
                .chain("A")
                .number(1)
                .insertionCode(' ')
                .atoms(List.of(atoms))
                .build();
    }

    private Atom atom(String name, String symbol, double x) {
        return atom(name, symbol, x, 0.0, 0.0);
    }

    private Atom atom(String name, String symbol, double x, double y, double z) {
        return Atom.builder()
                .pdbSerial((int) x + 1)
                .name(name)
                .position(new Point3D(x, y, z))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .element(Element.fromSymbol(symbol))
                .build();
    }

    private ChemComp component(List<ChemCompAtom> atoms, List<ChemCompBond> bonds) {
        ChemComp component = new ChemComp();
        component.setId("LIG");
        component.setAtoms(atoms);
        component.setBonds(bonds);
        return component;
    }

    private ChemCompAtom ccdAtom(
            String id,
            String element,
            int charge,
            String aromatic,
            String leaving) {
        ChemCompAtom atom = new ChemCompAtom();
        atom.setAtomId(id);
        atom.setTypeSymbol(element);
        atom.setCharge(charge);
        atom.setPdbxAromaticFlag(aromatic);
        atom.setPdbxLeavingAtomFlag(leaving);
        return atom;
    }

    private ChemCompBond ccdBond(
            String first,
            String second,
            String order,
            String aromatic) {
        ChemCompBond bond = new ChemCompBond();
        bond.setAtomId1(first);
        bond.setAtomId2(second);
        bond.setValueOrder(order);
        bond.setPdbxAromaticFlag(aromatic);
        return bond;
    }

    private void ideal(ChemCompAtom atom, double x, double y, double z) {
        atom.setPdbxModelCartnXIdeal(x);
        atom.setPdbxModelCartnYIdeal(y);
        atom.setPdbxModelCartnZIdeal(z);
    }
}
