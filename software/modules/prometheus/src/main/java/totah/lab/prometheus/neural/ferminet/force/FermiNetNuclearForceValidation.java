package totah.lab.prometheus.neural.ferminet.force;

import java.util.List;
import totah.lab.prometheus.molecular.Molecule;

/** Estimator-independent finite, translation, and planar diagnostics. */
public final class FermiNetNuclearForceValidation {

    private FermiNetNuclearForceValidation() {}

    public static Result validate(Molecule molecule, NuclearForceResult result) {
        int expected = 3 * molecule.nuclei().size();
        if (result.components().size() != expected) {
            throw new IllegalArgumentException("incomplete nuclear force vector");
        }
        double[] total = new double[3];
        int finite = 0, nonfinite = 0;
        double maximumPlanar = 0.0;
        boolean planar = molecule.nuclei().stream()
                .map(value -> value.position().inBohr().z()).distinct().count() == 1;
        for (var component : result.components()) {
            double value = component.meanHartreePerBohr();
            if (Double.isFinite(value)) finite++; else nonfinite++;
            total[component.axis()] += value;
            if (planar && component.axis() == 2) {
                maximumPlanar = Math.max(maximumPlanar, Math.abs(value));
            }
        }
        return new Result(
                new Vector3(total[0], total[1], total[2]),
                Math.sqrt(total[0] * total[0] + total[1] * total[1]
                        + total[2] * total[2]),
                planar, maximumPlanar, finite, nonfinite,
                nonfinite == 0 && finite == expected,
                List.of("raw statistics authoritative", "no clipping"));
    }

    public record Result(
            Vector3 totalForceHartreePerBohr,
            double totalForceNorm,
            boolean planarGeometry,
            double maximumAbsoluteOutOfPlaneForce,
            int finiteComponents,
            int nonfiniteComponents,
            boolean completeFiniteVector,
            List<String> policies) {
        public Result { policies = List.copyOf(policies); }
    }

    public record Vector3(double x, double y, double z) {}
}
