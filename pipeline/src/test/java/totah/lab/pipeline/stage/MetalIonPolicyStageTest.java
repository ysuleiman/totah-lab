package totah.lab.pipeline.stage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.report.TopologyBuildReport;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.protein.Topology;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetalIonPolicyStageTest {

    @TempDir
    Path tempDir;

    @Test
    void chargeAndAd4TypingAssignSupportedMonoatomicZincIon() throws Exception {
        Residue zinc = ionResidue("ZN", "ZN", "Zn", 2);
        PipelineContext context = contextFor(List.of(zinc));

        new ChargeAssignmentStage(null).run(context);
        Residue charged = residues(context).getFirst();
        Atom chargedAtom = charged.getAtoms().getFirst();
        assertEquals(2.0, chargedAtom.getCharge(), 1e-6);
        assertEquals("Zn", chargedAtom.getAmberType());

        new AD4AtomTypingStage().run(context);
        Residue typed = residues(context).getFirst();
        assertEquals("Zn", typed.getAtoms().getFirst().getAutoDockType());
    }

    @Test
    void chargeAssignmentRejectsAmbiguousIronOxidationState() {
        Residue iron = ionResidue("FE", "FE", "Fe", 26);
        PipelineContext context = contextFor(List.of(iron));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ChargeAssignmentStage(null).run(context));
        assertTrue(error.getMessage().contains("iron oxidation state is ambiguous"));
    }

    @Test
    void ad4TypingRejectsIonWithoutSupportedAutoDockType() throws Exception {
        Residue sodium = ionResidue("NA", "NA", "Na", 11);
        PipelineContext context = contextFor(List.of(sodium));
        new ChargeAssignmentStage(null).run(context);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new AD4AtomTypingStage().run(context));
        assertTrue(error.getMessage().contains("No supported AutoDock4 atom type"));
    }

    private PipelineContext contextFor(List<Residue> residues) {
        return new PipelineContext(tempDir, tempDir)
                .with(ContextKeys.PROTEIN_RESIDUES, residues)
                .with(ContextKeys.PROTEIN_TOPOLOGY, new Topology(atomCount(residues), List.of()))
                .with(ContextKeys.TOPOLOGY_BUILD_REPORT,
                        new TopologyBuildReport(residues.size(), atomCount(residues), 0, 0, 0, 0, List.of()))
                .with(ContextKeys.RESIDUE_STATES, Map.of());
    }

    private int atomCount(List<Residue> residues) {
        return residues.stream().mapToInt(Residue::getAtomCount).sum();
    }

    @SuppressWarnings("unchecked")
    private List<Residue> residues(PipelineContext context) {
        return (List<Residue>) context.require(ContextKeys.PROTEIN_RESIDUES);
    }

    private Residue ionResidue(String residueName, String atomName, String symbol, int atomicNumber) {
        return Residue.builder()
                .name(residueName)
                .number(1)
                .chain("A")
                .atoms(List.of(Atom.builder()
                        .pdbSerial(1)
                        .name(atomName)
                        .position(new Point3D(0.0, 0.0, 0.0))
                        .charge(0.0)
                        .occupancy(1.0)
                        .bFactor(20.0)
                        .element(Element.fromSymbol(symbol))
                        .build()))
                .build();
    }
}
