package totah.lab.hephaestus.receptor.load;


import java.nio.file.Path;
import java.util.Objects;

public record TargetLoadRequest(
        Path input,
        String targetId) {

    public TargetLoadRequest {
        Objects.requireNonNull(input, "input");

        if (targetId == null || targetId.isBlank()) {
            targetId = input.getFileName().toString();
        } else {
            targetId = targetId.trim();
        }
    }

    public TargetLoadRequest(Path input) {
        this(input, null);
    }
}
