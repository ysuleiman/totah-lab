package totah.lab.athena.landscape;

import totah.lab.gaia.geometry.Point3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Cartesian deterministic grid reusable unchanged across systems. */
public final class DeterministicPerturbationGrid {
    private DeterministicPerturbationGrid() { }

    public static List<Perturbation> rigidBody(double translationRange,
            double translationStep, double rotationRange, double rotationStep) {
        validateRange(translationRange, translationStep, "translation");
        validateRange(rotationRange, rotationStep, "rotation");
        List<Double> translations = axis(translationRange, translationStep);
        List<Double> rotations = axis(rotationRange, rotationStep);
        List<Perturbation> result = new ArrayList<>();
        int index = 0;
        for (double x : translations) for (double y : translations)
            for (double z : translations) for (double rx : rotations)
                for (double ry : rotations) for (double rz : rotations) {
                    result.add(new Perturbation(String.format(Locale.ROOT,
                            "q%06d", index++), new Point3D(x, y, z),
                            new LigandConfiguration.EulerRotation(rx, ry, rz), Map.of()));
                }
        return List.copyOf(result);
    }

    private static List<Double> axis(double range, double step) {
        int halfSteps = (int) Math.floor(range / step + 1.0e-12);
        List<Double> values = new ArrayList<>();
        for (int i = -halfSteps; i <= halfSteps; i++) values.add(i * step);
        return List.copyOf(values);
    }

    private static void validateRange(double range, double step, String name) {
        if (!Double.isFinite(range) || range < 0.0 || !Double.isFinite(step)
                || step <= 0.0) {
            throw new IllegalArgumentException(name + " range/step invalid");
        }
    }
}
