package totah.lab.prometheus.execution;

import java.nio.file.Path;
import java.util.List;

/** Authoritative fixed-geometry PySCF energy/gradient result. */
public record PyscfEnergyGradientResult(
        String specificationChecksum,
        String geometryIdentity,
        String inputGeometrySha256,
        double energyHartree,
        List<List<Double>> gradientHartreePerBohr,
        List<List<Double>> forceHartreePerBohr,
        double gradientNormHartreePerBohr,
        double finiteDifferenceGradientHartreePerBohr,
        double analyticGradientProjectionHartreePerBohr,
        double finiteDifferenceAbsoluteErrorHartreePerBohr,
        double finiteDifferencePlusEnergyHartree,
        double finiteDifferenceMinusEnergyHartree,
        boolean scfConverged,
        String pyscfVersion,
        String dftd3Version,
        Path resultJson) {
}
