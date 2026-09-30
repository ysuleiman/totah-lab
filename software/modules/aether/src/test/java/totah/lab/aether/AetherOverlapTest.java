package totah.lab.aether;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.GaussianTerm;
import totah.lab.aether.basis.PrimitiveGaussian;
import totah.lab.aether.basis.Sto3gHydrogen;
import totah.lab.aether.integral.SOverlap;
import totah.lab.aether.matrix.OverlapMatrix;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.gaia.geometry.Point3D;

import static org.junit.jupiter.api.Assertions.*;

class AetherOverlapTest {
    private static final double TOLERANCE = 2e-13;
    private static final Point3D ORIGIN = new Point3D(0, 0, 0);

    private static PrimitiveGaussian primitive(double exponent, double x, double y, double z) {
        return new PrimitiveGaussian(new Point3D(x, y, z), exponent);
    }

    private static ContractedGaussian single(PrimitiveGaussian primitive) {
        return new ContractedGaussian(List.of(new GaussianTerm(primitive, 1)));
    }

    @ParameterizedTest
    @ValueSource(doubles = {1e-100, 0.01, 0.7, 1, 100, 1e100})
    void normalizedPrimitiveSelfOverlap(double exponent) {
        var p = primitive(exponent, 0, 0, 0);
        assertEquals(1, SOverlap.between(p, p), TOLERANCE);
        // Independently integrate N^2 exp(-2 alpha r^2) over three axes.
        assertEquals(1, p.normalization() * p.normalization()
                * StrictMath.pow(StrictMath.PI / (2 * exponent), 1.5), TOLERANCE);
    }

    @Test
    void analyticEqualAndUnequalExponentCases() {
        var a = primitive(1, 0, 0, 0);
        assertEquals(StrictMath.exp(-0.98), SOverlap.between(a, primitive(1, 1.4, 0, 0)), TOLERANCE);
        var b = primitive(2, 0, 0, 0);
        assertEquals(StrictMath.pow(2 * StrictMath.sqrt(2) / 3, 1.5), SOverlap.between(a, b), TOLERANCE);
        assertEquals(SOverlap.between(a, b), SOverlap.between(b, a));
    }

    @Test
    void translationAndRotationInvariance() {
        var a = primitive(0.7, 0.25, -0.5, 1);
        var b = primitive(1.3, 1.75, 0.5, -1);
        double expected = SOverlap.between(a, b);
        assertEquals(expected, SOverlap.between(primitive(0.7, 4.25, 1.5, -2),
                primitive(1.3, 5.75, 2.5, -4)), TOLERANCE);
        // 90 degree rotation about z: (x,y,z) -> (-y,x,z).
        assertEquals(expected, SOverlap.between(primitive(0.7, 0.5, 0.25, 1),
                primitive(1.3, -0.5, 1.75, -1)), TOLERANCE);
    }

    @Test
    void largeSeparationDecaysWithoutInventingFailureEvidence() {
        var a = primitive(1, 0, 0, 0);
        assertTrue(SOverlap.between(a, primitive(1, 10, 0, 0)) < 1e-20);
        assertEquals(0, SOverlap.between(a, primitive(1, 1000, 0, 0)));
        var huge = primitive(1, Double.MAX_VALUE, 0, 0);
        assertThrows(ArithmeticException.class, () -> OverlapMatrix.compute(List.of(single(a), single(huge))));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void invalidExponentsAreRejected(double exponent) {
        assertThrows(IllegalArgumentException.class, () -> new PrimitiveGaussian(ORIGIN, exponent));
    }

    @Test
    void invalidContractionsAreRejected() {
        var p = primitive(1, 0, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> new ContractedGaussian(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new GaussianTerm(p, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ContractedGaussian(List.of(new GaussianTerm(p, 0))));
        assertThrows(IllegalArgumentException.class, () -> new ContractedGaussian(List.of(
                new GaussianTerm(p, 1), new GaussianTerm(p, -1))));
        assertThrows(IllegalArgumentException.class, () -> new ContractedGaussian(List.of(
                new GaussianTerm(p, 1), new GaussianTerm(primitive(1, 1, 0, 0), 1))));
        assertThrows(IllegalArgumentException.class, () -> OverlapMatrix.compute(List.of()));
        assertThrows(NullPointerException.class, () -> new PrimitiveGaussian(null, 1));
    }

    @Test
    void signedCoefficientsAndScaleNormalization() {
        var terms = List.of(new GaussianTerm(primitive(0.5, 0, 0, 0), -0.2),
                new GaussianTerm(primitive(2, 0, 0, 0), 0.8));
        var a = new ContractedGaussian(terms);
        var scaled = new ContractedGaussian(terms.stream()
                .map(t -> new GaussianTerm(t.primitive(), 2 * t.coefficient())).toList());
        assertEquals(1, SOverlap.between(a, a), TOLERANCE);
        assertEquals(1, SOverlap.between(a, scaled), TOLERANCE);
        var reversed = new ContractedGaussian(List.of(terms.get(1), terms.get(0)));
        assertEquals(1, SOverlap.between(a, reversed), TOLERANCE);
    }

    @Test
    void basisAndMatrixAreImmutableAndPreserveOrder() throws IOException {
        var h = Sto3gHydrogen.load();
        var a = h.atBohr(ORIGIN);
        var b = h.atBohr(new Point3D(1.4, 0, 0));
        var c = single(primitive(0.7, -0.4, 0.5, 1));
        var input = new ArrayList<>(List.of(a, b, c));
        var matrix = OverlapMatrix.compute(input);
        input.clear();
        assertEquals(3, matrix.size());
        assertSame(a, matrix.functions().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> matrix.functions().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.terms().clear());
        assertThrows(UnsupportedOperationException.class, () -> h.exponents().clear());
        var permuted = OverlapMatrix.compute(List.of(c, a, b));
        int[] old = {2, 0, 1};
        for (int i = 0; i < 3; i++) {
            assertEquals(1, matrix.get(i, i), TOLERANCE);
            for (int j = 0; j < 3; j++) {
                assertEquals(matrix.get(i, j), matrix.get(j, i));
                assertEquals(matrix.get(old[i], old[j]), permuted.get(i, j), TOLERANCE);
            }
        }
        assertNotEquals(matrix.receipt().calculationHash(), permuted.receipt().calculationHash());
    }

    @Test
    void deterministicReplayAndHashSensitivity() throws IOException {
        var h = Sto3gHydrogen.load();
        var basis = List.of(h.atBohr(ORIGIN), h.atBohr(new Point3D(1.4, 0, 0)));
        var first = OverlapMatrix.compute(basis);
        for (int i = 0; i < 20; i++) assertEquals(first.receipt(), OverlapMatrix.compute(basis).receipt());
        assertEquals(ScientificStatus.SCREENING_ONLY, first.receipt().status());
        assertFalse(first.receipt().reason().isBlank());
        var changedGeometry = OverlapMatrix.compute(List.of(basis.get(0), h.atBohr(new Point3D(1.5, 0, 0))));
        assertNotEquals(first.receipt().calculationHash(), changedGeometry.receipt().calculationHash());
        assertNotEquals(first.receipt().resultHash(), changedGeometry.receipt().resultHash());
        var one = OverlapMatrix.compute(List.of(single(primitive(1, 0, 0, 0))));
        var otherExponent = OverlapMatrix.compute(List.of(single(primitive(2, 0, 0, 0))));
        assertEquals(one.get(0, 0), otherExponent.get(0, 0));
        assertNotEquals(one.receipt().calculationHash(), otherExponent.receipt().calculationHash());
        var otherCoefficient = OverlapMatrix.compute(List.of(new ContractedGaussian(List.of(
                new GaussianTerm(primitive(1, 0, 0, 0), 2)))));
        assertNotEquals(one.receipt().calculationHash(), otherCoefficient.receipt().calculationHash());
        var negativeZero = OverlapMatrix.compute(List.of(single(primitive(1, -0.0, 0, 0))));
        assertEquals(one.receipt(), negativeZero.receipt());
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            assertEquals(first.receipt(), OverlapMatrix.compute(basis).receipt());
        } finally {
            Locale.setDefault(previous);
        }
        System.out.println("AETHER_H2_RECEIPT=" + first.receipt());
    }

    @Test
    void bundledBasisHasVerifiedProvenance() throws IOException {
        var h = Sto3gHydrogen.load();
        assertTrue(h.source().contains("/v2.10.0/"));
        assertTrue(h.version().contains("EMSL"));
        assertEquals("28eda9121e200c2260a03f96367b52ab4756e3b5446e9c2f90fa839b6be4fd4c", h.contentHash());
        assertEquals(List.of(3.42525091, 0.62391373, 0.16885540), h.exponents());
        assertEquals(1, SOverlap.between(h.atBohr(ORIGIN), h.atBohr(ORIGIN)), TOLERANCE);
    }

    @Test
    void independentPyscfLibcintReferences() throws IOException {
        var h = Sto3gHydrogen.load();
        Map<String, OverlapMatrix> cases = Map.of(
                "primitive_self", OverlapMatrix.compute(List.of(single(primitive(1, 0, 0, 0)))),
                "primitive_separated", OverlapMatrix.compute(List.of(single(primitive(1, 0, 0, 0)), single(primitive(1, 1.4, 0, 0)))),
                "primitive_unequal", OverlapMatrix.compute(List.of(single(primitive(0.7, 0, 0, 0)), single(primitive(1.3, 0.4, -0.7, 1.1)))),
                "sto3g_h", OverlapMatrix.compute(List.of(h.atBohr(ORIGIN))),
                "sto3g_h2", OverlapMatrix.compute(List.of(h.atBohr(ORIGIN), h.atBohr(new Point3D(1.4, 0, 0)))));
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("reference/overlap.csv")) {
            assertNotNull(input);
            bytes = input.readAllBytes();
        }
        assertEquals("2d6cf00fc8e045f70e58c62a3a40c752cced735b4696a90b18292b44356b2429", ContentHash.sha256(bytes));
        double maximumError = 0;
        int count = 0;
        try (var reader = new BufferedReader(new InputStreamReader(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8))) {
            assertEquals("case,row,column,overlap", reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",");
                var matrix = cases.get(columns[0]);
                assertNotNull(matrix);
                double actual = matrix.get(Integer.parseInt(columns[1]), Integer.parseInt(columns[2]));
                double expected = Double.parseDouble(columns[3]);
                maximumError = StrictMath.max(maximumError, StrictMath.abs(actual - expected));
                assertEquals(expected, actual, TOLERANCE, line);
                count++;
            }
        }
        assertEquals(14, count);
        System.out.println("AETHER_EXTERNAL_REFERENCE_ENTRIES=" + count + " MAX_REFERENCE_ERROR=" + maximumError);
        System.out.println("AETHER_H2_S_01=" + cases.get("sto3g_h2").get(0, 1));
    }
}
