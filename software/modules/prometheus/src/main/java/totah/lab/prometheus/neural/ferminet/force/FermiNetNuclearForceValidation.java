package totah.lab.prometheus.neural.ferminet.force;

import java.util.List;
import totah.lab.prometheus.molecular.Molecule;

/** Estimator-independent finite, translation, and planar diagnostics. */
public final class FermiNetNuclearForceValidation {

    /** Five times the last decimal place of frozen molecular geometries. */
    public static final double PLANAR_GEOMETRY_TOLERANCE_BOHR = 5.0e-12;

    private FermiNetNuclearForceValidation() {}

    public static Result validate(Molecule molecule, NuclearForceResult result) {
        int expected = 3 * molecule.nuclei().size();
        if (result.components().size() != expected) {
            throw new IllegalArgumentException("incomplete nuclear force vector");
        }
        double[] total = new double[3];
        int finite = 0, nonfinite = 0;
        double maximumPlanar = 0.0;
        double referenceZ = molecule.nuclei().get(0).position().inBohr().z();
        boolean planar = molecule.nuclei().stream().allMatch(value ->
                Math.abs(value.position().inBohr().z() - referenceZ)
                        <= PLANAR_GEOMETRY_TOLERANCE_BOHR);
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

    /** Physical diagnostics only; no new acceptance thresholds are imposed. */
    public static PhysicalDiagnostics physicalDiagnostics(
            Molecule molecule, NuclearForceResult result) {
        int expected = 3 * molecule.nuclei().size();
        if (result.components().size() != expected) {
            throw new IllegalArgumentException("incomplete nuclear force vector");
        }
        double chargeSum = molecule.nuclei().stream()
                .mapToDouble(nucleus -> nucleus.charge().atomicNumber()).sum();
        double ox = 0, oy = 0, oz = 0;
        for (var nucleus : molecule.nuclei()) {
            double weight = nucleus.charge().atomicNumber() / chargeSum;
            var position = nucleus.position().inBohr();
            ox += weight * position.x(); oy += weight * position.y();
            oz += weight * position.z();
        }
        double[] net = new double[3], torque = new double[3];
        boolean finite = true;
        for (var component : result.components()) {
            double value = component.meanHartreePerBohr();
            finite &= Double.isFinite(value);
            net[component.axis()] += value;
        }
        if (finite) {
            for (int nucleusIndex = 0; nucleusIndex < molecule.nuclei().size(); nucleusIndex++) {
                var p = molecule.nuclei().get(nucleusIndex).position().inBohr();
                double[] f = new double[3];
                for (var component : result.components()) {
                    if (component.nucleus() == nucleusIndex) f[component.axis()] = component.meanHartreePerBohr();
                }
                double x = p.x() - ox, y = p.y() - oy, z = p.z() - oz;
                torque[0] += y * f[2] - z * f[1];
                torque[1] += z * f[0] - x * f[2];
                torque[2] += x * f[1] - y * f[0];
            }
        } else {
            java.util.Arrays.fill(net, Double.NaN);
            java.util.Arrays.fill(torque, Double.NaN);
        }
        return new PhysicalDiagnostics(finite,
                new Vector3(net[0], net[1], net[2]), norm(net),
                new Vector3(torque[0], torque[1], torque[2]), norm(torque),
                "torque origin is the nuclear-charge center; diagnostics are reported without newly invented pass thresholds");
    }

    private static double norm(double[] value) {
        return Math.sqrt(value[0] * value[0] + value[1] * value[1]
                + value[2] * value[2]);
    }

    public record PhysicalDiagnostics(boolean finiteVector,
            Vector3 netForceHartreePerBohr, double netForceNorm,
            Vector3 torqueHartree, double torqueNorm,
            String policy) {}
}
