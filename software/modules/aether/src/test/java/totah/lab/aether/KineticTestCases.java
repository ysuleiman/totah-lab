package totah.lab.aether;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.GaussianTerm;
import totah.lab.aether.basis.PrimitiveGaussian;
import totah.lab.aether.basis.Sto3gHydrogen;
import totah.lab.aether.matrix.KineticMatrix;
import totah.lab.gaia.geometry.Point3D;

/** Shared test geometries; numerical expected values come from frozen libcint output. */
final class KineticTestCases {
    static final Point3D ORIGIN = new Point3D(0, 0, 0);
    static final Point3D H2_SECOND = new Point3D(1.4, 0, 0);

    private KineticTestCases() {}

    static ContractedGaussian single(double exponent, Point3D center) {
        return new ContractedGaussian(List.of(new GaussianTerm(new PrimitiveGaussian(center, exponent), 1)));
    }

    static ContractedGaussian signed(Point3D center) {
        return new ContractedGaussian(List.of(new GaussianTerm(new PrimitiveGaussian(center, 0.5), -0.2),
                new GaussianTerm(new PrimitiveGaussian(center, 2), 0.8)));
    }

    static List<ContractedGaussian> h2() throws IOException {
        var h = Sto3gHydrogen.load();
        return List.of(h.atBohr(ORIGIN), h.atBohr(H2_SECOND));
    }

    static Map<String, KineticMatrix> referenceCases() throws IOException {
        var h = Sto3gHydrogen.load();
        return Map.of(
                "primitive_self", KineticMatrix.compute(List.of(single(1, ORIGIN))),
                "primitive_separated", KineticMatrix.compute(List.of(single(1, ORIGIN), single(1, H2_SECOND))),
                "primitive_unequal", KineticMatrix.compute(List.of(single(0.7, ORIGIN), single(1.3, new Point3D(0.4, -0.7, 1.1)))),
                "sto3g_h", KineticMatrix.compute(List.of(h.atBohr(ORIGIN))),
                "sto3g_h2", KineticMatrix.compute(h2()),
                "primitive_negative", KineticMatrix.compute(List.of(single(1, ORIGIN), single(1, new Point3D(3, 0, 0)))),
                "signed_contraction", KineticMatrix.compute(List.of(signed(ORIGIN), signed(H2_SECOND))));
    }
}
