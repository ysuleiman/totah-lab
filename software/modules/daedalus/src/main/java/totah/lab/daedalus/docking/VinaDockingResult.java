package totah.lab.daedalus.docking;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Outcome of one AutoDock Vina process invocation. */
public record VinaDockingResult(
        int exitCode,
        List<VinaPose> poses,
        String output) {

    public VinaDockingResult {
        poses = List.copyOf(Objects.requireNonNull(poses, "poses"));
        Objects.requireNonNull(output, "output");
    }

    /** The first table row; Vina lists poses best-affinity first. */
    public Optional<VinaPose> bestPose() {
        return poses.isEmpty() ? Optional.empty() : Optional.of(poses.getFirst());
    }
}
