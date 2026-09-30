package totah.lab.aether;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.Sto3gHydrogen;
import totah.lab.aether.matrix.NuclearAttractionMatrix;
import totah.lab.aether.model.NuclearCenter;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;

import static totah.lab.aether.KineticTestCases.*;

final class NuclearTestCases {
    private NuclearTestCases() {}

    static NuclearCenter hydrogen(Point3D point) {
        return new NuclearCenter(point, Element.H.getAtomicNumber());
    }

    static List<NuclearCenter> h2Nuclei() {
        return List.of(hydrogen(ORIGIN), hydrogen(H2_SECOND));
    }

    static NuclearAttractionMatrix h2Matrix() throws IOException {
        return NuclearAttractionMatrix.compute(h2(), h2Nuclei());
    }

    static Map<String, NuclearAttractionMatrix> referenceCases() throws IOException {
        var h = Sto3gHydrogen.load();
        Map<String, List<ContractedGaussian>> bases = Map.of(
                "primitive_self", List.of(single(1, ORIGIN)),
                "primitive_separated", List.of(single(1, ORIGIN), single(1, H2_SECOND)),
                "primitive_unequal", List.of(single(0.7, ORIGIN), single(1.3, new Point3D(0.4, -0.7, 1.1))),
                "sto3g_h", List.of(h.atBohr(ORIGIN)),
                "sto3g_h2", h2(),
                "primitive_negative", List.of(single(1, ORIGIN), single(1, new Point3D(3, 0, 0))),
                "signed_contraction", List.of(signed(ORIGIN), signed(H2_SECOND)));
        var cases = new LinkedHashMap<String, NuclearAttractionMatrix>();
        bases.forEach((name, basis) -> cases.put(name, NuclearAttractionMatrix.compute(basis,
                basis.stream().map(c -> hydrogen(c.terms().getFirst().primitive().centerBohr())).toList())));
        double[] distances = {0, 1e-8, 0.05, 0.5, 3, 100, 1e6};
        for (int i = 0; i < distances.length; i++) {
            cases.put("external_center_" + i, NuclearAttractionMatrix.compute(List.of(single(1, ORIGIN)),
                    List.of(hydrogen(new Point3D(distances[i], 0, 0)))));
        }
        cases.put("charge_scaled", NuclearAttractionMatrix.compute(List.of(single(1, ORIGIN)),
                List.of(new NuclearCenter(new Point3D(0.4, -0.7, 1.1), 2.5))));
        return cases;
    }
}
