package totah.lab.ligand;

import org.biojava.nbio.structure.chem.ChemComp;
import totah.lab.protein.Residue;
import totah.lab.structure.io.pdbqt.LigandPDBQTWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Native CCD-backed ligand preparation workflow.
 */
public final class LigandPreparer {

    private final CcdLigandGraphBuilder graphBuilder;
    private final LigandHydrogenator hydrogenator;
    private final LigandChargeAssigner chargeAssigner;
    private final LigandAd4AtomTyper atomTyper;
    private final LigandTorsionTreeBuilder torsionTreeBuilder;

    public LigandPreparer() {
        this(
                new CcdLigandGraphBuilder(),
                new LigandHydrogenator(),
                new LigandChargeAssigner(),
                new LigandAd4AtomTyper(),
                new LigandTorsionTreeBuilder());
    }

    LigandPreparer(
            CcdLigandGraphBuilder graphBuilder,
            LigandHydrogenator hydrogenator,
            LigandChargeAssigner chargeAssigner,
            LigandAd4AtomTyper atomTyper,
            LigandTorsionTreeBuilder torsionTreeBuilder) {
        this.graphBuilder = Objects.requireNonNull(graphBuilder, "graphBuilder is null");
        this.hydrogenator = Objects.requireNonNull(hydrogenator, "hydrogenator is null");
        this.chargeAssigner = Objects.requireNonNull(chargeAssigner, "chargeAssigner is null");
        this.atomTyper = Objects.requireNonNull(atomTyper, "atomTyper is null");
        this.torsionTreeBuilder = Objects.requireNonNull(
                torsionTreeBuilder, "torsionTreeBuilder is null");
    }

    public LigandPreparationResult prepare(Residue selectedLigand, ChemComp chemComp) {
        Objects.requireNonNull(selectedLigand, "selectedLigand is null");
        Objects.requireNonNull(chemComp, "chemComp is null");

        CcdLigandGraphResult initial = graphBuilder.build(selectedLigand, chemComp);
        LigandHydrogenationResult hydrogenated = hydrogenator.hydrogenate(initial);
        LigandChargeAssignmentResult charged = chargeAssigner.assign(hydrogenated.graph());
        LigandAd4TypingResult typed = atomTyper.assign(charged.graph());
        LigandTorsionTreeResult torsion = torsionTreeBuilder.build(typed.graph());
        Residue preparedResidue = selectedLigand.toBuilder()
                .atoms(typed.graph().atoms())
                .build();
        String pdbqt = writePdbqt(preparedResidue, torsion);

        return new LigandPreparationResult(
                typed.graph(),
                initial.validationReport(),
                hydrogenated,
                charged,
                typed,
                torsion,
                pdbqt);
    }

    public LigandPreparationResult prepareToPath(
            Residue selectedLigand,
            ChemComp chemComp,
            Path outputPath) throws IOException {
        Objects.requireNonNull(outputPath, "outputPath is null");
        LigandPreparationResult result = prepare(selectedLigand, chemComp);
        Files.writeString(outputPath, result.pdbqt(), StandardCharsets.UTF_8);
        return result;
    }

    private String writePdbqt(
            Residue preparedLigand,
            LigandTorsionTreeResult torsion) {
        StringWriter output = new StringWriter();
        new LigandPDBQTWriter(output).write(
                preparedLigand,
                torsion.tree(),
                torsion.torsionalDegreesOfFreedom());
        return output.toString();
    }
}
