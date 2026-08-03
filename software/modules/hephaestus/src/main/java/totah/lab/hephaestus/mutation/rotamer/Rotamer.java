package totah.lab.hephaestus.mutation.rotamer;

import java.util.List;

public record Rotamer(String id, List<Double> chiAnglesRadians, double probability) {
    public Rotamer {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        chiAnglesRadians = List.copyOf(chiAnglesRadians);
        if (!Double.isFinite(probability) || probability <= 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be in (0, 1]");
        }
    }

    public double firstChiOrZero() {
        return chiAnglesRadians.isEmpty() ? 0.0 : chiAnglesRadians.getFirst();
    }
}
