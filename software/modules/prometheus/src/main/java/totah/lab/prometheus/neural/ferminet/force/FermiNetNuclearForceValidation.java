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
        requireCompleteCartesianIdentity(molecule, result);
        double[] total = new double[3];
        int finite = 0, nonfinite = 0;
        double maximumPlanar = 0.0;
        double[] normal = molecularPlaneNormal(molecule);
        boolean planar = normal != null;
        for (var component : result.components()) {
            double value = component.meanHartreePerBohr();
            if (Double.isFinite(value)) finite++; else nonfinite++;
            total[component.axis()] += value;
        }
        if (planar) {
            for (int nucleus = 0; nucleus < molecule.nuclei().size(); nucleus++) {
                double[] force = new double[3];
                for (var component : result.components()) if (component.nucleus() == nucleus) {
                    force[component.axis()] = component.meanHartreePerBohr();
                }
                maximumPlanar = Math.max(maximumPlanar, Math.abs(force[0] * normal[0]
                        + force[1] * normal[1] + force[2] * normal[2]));
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
        requireCompleteCartesianIdentity(molecule, result);
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

    private static void requireCompleteCartesianIdentity(Molecule molecule,
            NuclearForceResult result) {
        boolean[][] seen = new boolean[molecule.nuclei().size()][3];
        for (var component : result.components()) {
            if (component.nucleus() < 0 || component.nucleus() >= seen.length
                    || component.axis() < 0 || component.axis() >= 3) {
                throw new IllegalArgumentException("nuclear force component index out of range");
            }
            if (seen[component.nucleus()][component.axis()]) {
                throw new IllegalArgumentException("duplicate nuclear force component identity");
            }
            seen[component.nucleus()][component.axis()] = true;
        }
        for (boolean[] nucleus : seen) for (boolean axis : nucleus) if (!axis) {
            throw new IllegalArgumentException("missing nuclear force component identity");
        }
    }

    private static double[] molecularPlaneNormal(Molecule molecule) {
        if (molecule.nuclei().size() < 3) return new double[] {0.0, 0.0, 1.0};
        var origin = molecule.nuclei().get(0).position().inBohr();
        for (int i = 1; i < molecule.nuclei().size() - 1; i++) {
            var a = molecule.nuclei().get(i).position().inBohr();
            for (int j = i + 1; j < molecule.nuclei().size(); j++) {
                var b = molecule.nuclei().get(j).position().inBohr();
                double[] u = {a.x() - origin.x(), a.y() - origin.y(), a.z() - origin.z()};
                double[] v = {b.x() - origin.x(), b.y() - origin.y(), b.z() - origin.z()};
                double[] n = {u[1] * v[2] - u[2] * v[1],
                        u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]};
                double length = norm(n);
                if (length <= PLANAR_GEOMETRY_TOLERANCE_BOHR) continue;
                for (int axis = 0; axis < 3; axis++) n[axis] /= length;
                for (var nucleus : molecule.nuclei()) {
                    var p = nucleus.position().inBohr();
                    double distance = Math.abs((p.x() - origin.x()) * n[0]
                            + (p.y() - origin.y()) * n[1]
                            + (p.z() - origin.z()) * n[2]);
                    if (distance > PLANAR_GEOMETRY_TOLERANCE_BOHR) return null;
                }
                return n;
            }
        }
        return new double[] {0.0, 0.0, 1.0};
    }

    public record PhysicalDiagnostics(boolean finiteVector,
            Vector3 netForceHartreePerBohr, double netForceNorm,
            Vector3 torqueHartree, double torqueNorm,
            String policy) {}
}
