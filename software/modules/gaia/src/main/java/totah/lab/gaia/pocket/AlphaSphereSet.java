package totah.lab.gaia.pocket;

import java.util.List;
import java.util.Objects;

public record AlphaSphereSet(List<AlphaSphere> spheres) {
    public AlphaSphereSet {
        spheres = List.copyOf(Objects.requireNonNull(spheres, "spheres"));
        if (spheres.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "spheres must not contain null elements.");
        }
    }
}
