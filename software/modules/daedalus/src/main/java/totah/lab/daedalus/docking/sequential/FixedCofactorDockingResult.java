package totah.lab.daedalus.docking.sequential;

import totah.lab.daedalus.docking.VinaDockingResult;
import totah.lab.hephaestus.receptor.assembly.LigandPose;
import totah.lab.hephaestus.receptor.assembly.ReceptorAssembly;

import java.util.Objects;

/** Immutable result of both docking invocations and their receptor assembly. */
public record FixedCofactorDockingResult(
        VinaDockingResult cofactorDocking,
        LigandPose selectedCofactorPose,
        ReceptorAssembly receptorAssembly,
        VinaDockingResult ligandDocking,
        FixedCofactorDockingArtifacts artifacts) {

    public FixedCofactorDockingResult {
        Objects.requireNonNull(cofactorDocking, "cofactorDocking");
        Objects.requireNonNull(selectedCofactorPose, "selectedCofactorPose");
        Objects.requireNonNull(receptorAssembly, "receptorAssembly");
        Objects.requireNonNull(ligandDocking, "ligandDocking");
        Objects.requireNonNull(artifacts, "artifacts");
    }
}
