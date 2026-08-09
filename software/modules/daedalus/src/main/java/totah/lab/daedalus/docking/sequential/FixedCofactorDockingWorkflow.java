package totah.lab.daedalus.docking.sequential;

import totah.lab.daedalus.docking.DockingInput;
import totah.lab.daedalus.docking.VinaDockingResult;
import totah.lab.daedalus.docking.VinaDockingRunner;
import totah.lab.hephaestus.receptor.assembly.FixedCofactor;
import totah.lab.hephaestus.receptor.assembly.LigandPose;
import totah.lab.hephaestus.receptor.assembly.PdbqtLigandPoseReader;
import totah.lab.hephaestus.receptor.assembly.ReceptorAssembly;
import totah.lab.hephaestus.receptor.assembly.ReceptorAssemblyWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Docks a cofactor, fixes an explicit pose, then docks a second ligand. */
public final class FixedCofactorDockingWorkflow {

    private final VinaDockingRunner dockingRunner;
    private final PdbqtLigandPoseReader poseReader;
    private final ReceptorAssemblyWriter assemblyWriter;

    public FixedCofactorDockingWorkflow(Path vinaExecutable) {
        this(
                new VinaDockingRunner(vinaExecutable),
                new PdbqtLigandPoseReader(),
                new ReceptorAssemblyWriter());
    }

    FixedCofactorDockingWorkflow(
            VinaDockingRunner dockingRunner,
            PdbqtLigandPoseReader poseReader,
            ReceptorAssemblyWriter assemblyWriter) {
        this.dockingRunner = Objects.requireNonNull(dockingRunner, "dockingRunner");
        this.poseReader = Objects.requireNonNull(poseReader, "poseReader");
        this.assemblyWriter = Objects.requireNonNull(assemblyWriter, "assemblyWriter");
    }

    public FixedCofactorDockingResult run(FixedCofactorDockingRequest request)
            throws IOException, InterruptedException {

        Objects.requireNonNull(request, "request");
        FixedCofactorDockingArtifacts artifacts = request.artifacts();
        VinaDockingResult cofactorDocking = dockingRunner.run(
                new DockingInput(
                        request.proteinPdbqt(),
                        request.cofactorPdbqt(),
                        Optional.empty()),
                request.cofactorDockingOptions(),
                artifacts.cofactorPosesPdbqt());
        requireSuccess(cofactorDocking, "Cofactor docking");
        requireOutput(artifacts.cofactorPosesPdbqt(), "Cofactor pose output");

        List<LigandPose> poses = poseReader.read(
                artifacts.cofactorPosesPdbqt(),
                request.preparedCofactor(),
                request.runId());
        LigandPose selectedPose = selectModel(
                poses, request.cofactorModelNumber());
        ReceptorAssembly assembly = ReceptorAssembly.of(
                request.preparedProtein()).withFixedCofactor(new FixedCofactor(
                request.cofactorId(),
                request.componentCode(),
                selectedPose));

        assemblyWriter.writePdb(assembly, artifacts.receptorAssemblyPdb());
        assemblyWriter.writeRigidPdbqt(
                assembly, artifacts.receptorAssemblyPdbqt());

        VinaDockingResult ligandDocking = dockingRunner.run(
                new DockingInput(
                        artifacts.receptorAssemblyPdbqt(),
                        request.ligandPdbqt(),
                        Optional.empty()),
                request.ligandDockingOptions(),
                artifacts.ligandPosesPdbqt());
        requireSuccess(ligandDocking, "Ligand docking");
        requireOutput(artifacts.ligandPosesPdbqt(), "Ligand pose output");
        return new FixedCofactorDockingResult(
                cofactorDocking,
                selectedPose,
                assembly,
                ligandDocking,
                artifacts);
    }

    private static LigandPose selectModel(
            List<LigandPose> poses,
            int modelNumber) {
        String expected = Integer.toString(modelNumber);
        return poses.stream()
                .filter(pose -> expected.equals(
                        pose.provenance().get("pdbqt-model")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cofactor PDBQT does not contain requested model "
                                + modelNumber));
    }

    private static void requireSuccess(
            VinaDockingResult result,
            String description) throws IOException {
        if (result.exitCode() != 0) {
            throw new IOException(description + " failed with exit code "
                    + result.exitCode() + ": " + result.output());
        }
    }

    private static void requireOutput(Path output, String description)
            throws IOException {
        if (!Files.isRegularFile(output)) {
            throw new IOException(description + " was not created: " + output);
        }
    }
}
