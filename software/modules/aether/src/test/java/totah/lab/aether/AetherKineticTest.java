package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.GaussianTerm;
import totah.lab.aether.basis.PrimitiveGaussian;
import totah.lab.aether.integral.SKinetic;
import totah.lab.aether.integral.SOverlap;
import totah.lab.aether.matrix.KineticMatrix;
import totah.lab.aether.matrix.OverlapMatrix;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.gaia.geometry.Point3D;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.aether.KineticTestCases.*;

class AetherKineticTest {
    private static final double TOLERANCE = 2e-13;

    @ParameterizedTest
    @ValueSource(doubles = {1e-100, 0.01, 0.7, 1, 100, 1e100})
    void analyticPrimitiveSelfIsThreeAlphaOverTwo(double alpha) {
        var p = new PrimitiveGaussian(ORIGIN, alpha);
        assertEquals(1.5 * alpha, SKinetic.between(p, p), 2e-14 * alpha);
    }

    @Test
    void analyticEqualExponentsSeparated() {
        var a = new PrimitiveGaussian(ORIGIN, 1);
        var b = new PrimitiveGaussian(H2_SECOND, 1);
        assertEquals(0.52 * StrictMath.exp(-0.98), SKinetic.between(a, b), TOLERANCE);
    }

    @Test
    void analyticUnequalExponentsAtSameCenter() {
        var a = new PrimitiveGaussian(ORIGIN, 0.5);
        var b = new PrimitiveGaussian(ORIGIN, 1.5);
        double overlap = StrictMath.pow(StrictMath.sqrt(3) / 2, 1.5);
        assertEquals(1.125 * overlap, SKinetic.between(a, b), TOLERANCE);
        assertEquals(SKinetic.between(a, b), SKinetic.between(b, a));
    }

    @Test
    void analyticUnequalSeparatedFromLaplacianMoment() {
        double alpha = 0.7;
        double beta = 1.3;
        var a = new PrimitiveGaussian(ORIGIN, alpha);
        var b = new PrimitiveGaussian(new Point3D(0.4, -0.7, 1.1), beta);
        double p = alpha + beta;
        // Gaussian product's second moment around B, from the unsymmetrized Laplacian.
        double moment = 3 / (2 * p) + alpha * alpha * (0.16 + 0.49 + 1.21) / (p * p);
        double expected = beta * (3 - 2 * beta * moment) * SOverlap.between(a, b);
        assertEquals(expected, SKinetic.between(a, b), TOLERANCE);
        assertEquals(SKinetic.between(a, b), SKinetic.between(b, a));
    }

    @Test
    void zeroAndNegativeOffDiagonalAreNotClipped() {
        var a = new PrimitiveGaussian(ORIGIN, 3);
        assertEquals(0, SKinetic.between(a, new PrimitiveGaussian(new Point3D(1, 0, 0), 3)));
        var b = new PrimitiveGaussian(ORIGIN, 1);
        double negative = SKinetic.between(b, new PrimitiveGaussian(new Point3D(3, 0, 0), 1));
        assertEquals(-3 * StrictMath.exp(-4.5), negative, TOLERANCE);
        assertTrue(negative < 0);
    }

    @Test
    void contractedSymmetryAndNormalizationReuse() {
        var a = signed(ORIGIN);
        var b = single(0.7, H2_SECOND);
        assertEquals(SKinetic.between(a, b), SKinetic.between(b, a), TOLERANCE);
        var scaled = new ContractedGaussian(a.terms().stream()
                .map(t -> new GaussianTerm(t.primitive(), 2 * t.coefficient())).toList());
        assertEquals(SKinetic.between(a, a), SKinetic.between(scaled, scaled), TOLERANCE);
        var reversed = new ContractedGaussian(a.terms().reversed());
        assertEquals(SKinetic.between(a, b), SKinetic.between(reversed, b), TOLERANCE);
        var one = new PrimitiveGaussian(ORIGIN, 0.7);
        assertEquals(SKinetic.between(one, one), SKinetic.between(single(0.7, ORIGIN), single(0.7, ORIGIN)), TOLERANCE);
    }

    @Test
    void translationInvarianceForPrimitiveAndContractedMatrices() throws IOException {
        assertTransformInvariant(p -> new Point3D(p.x() + 2.25, p.y() - 3.5, p.z() + 0.75));
    }

    @Test
    void rotationInvarianceForPrimitiveAndContractedMatrices() throws IOException {
        // Proper rotation around z with cos=3/5 and sin=4/5, followed by axis cycling.
        assertTransformInvariant(p -> new Point3D(p.z(), 0.6 * p.x() - 0.8 * p.y(), 0.8 * p.x() + 0.6 * p.y()));
    }

    private static void assertTransformInvariant(UnaryOperator<Point3D> transform) throws IOException {
        var basis = new ArrayList<>(h2());
        basis.add(signed(new Point3D(0.4, -0.7, 1.1)));
        basis.add(single(0.7, new Point3D(-0.2, 0.3, 0.6)));
        var transformed = basis.stream().map(c -> new ContractedGaussian(c.terms().stream()
                .map(t -> new GaussianTerm(new PrimitiveGaussian(transform.apply(t.primitive().centerBohr()),
                        t.primitive().exponent()), t.coefficient())).toList())).toList();
        var original = KineticMatrix.compute(basis);
        var moved = KineticMatrix.compute(transformed);
        for (int i = 0; i < basis.size(); i++) {
            for (int j = 0; j < basis.size(); j++) {
                assertEquals(original.get(i, j), moved.get(i, j), TOLERANCE);
            }
        }
        var a = basis.getFirst().terms().getFirst().primitive();
        var b = basis.get(2).terms().getFirst().primitive();
        assertEquals(SKinetic.between(a, b), SKinetic.between(
                new PrimitiveGaussian(transform.apply(a.centerBohr()), a.exponent()),
                new PrimitiveGaussian(transform.apply(b.centerBohr()), b.exponent())), TOLERANCE);
    }

    @Test
    void immutableMatrixPreservesOrderingAndSymmetry() throws IOException {
        var input = new ArrayList<>(h2());
        input.add(signed(new Point3D(0.4, -0.7, 1.1)));
        var matrix = KineticMatrix.compute(input);
        var permuted = KineticMatrix.compute(List.of(input.get(2), input.get(0), input.get(1)));
        input.clear();
        assertEquals(3, matrix.size());
        assertThrows(UnsupportedOperationException.class, () -> matrix.functions().clear());
        assertThrows(UnsupportedOperationException.class, () -> matrix.functions().getFirst().terms().clear());
        int[] old = {2, 0, 1};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(matrix.get(i, j), matrix.get(j, i));
                assertEquals(matrix.get(old[i], old[j]), permuted.get(i, j), TOLERANCE);
            }
        }
        assertNotEquals(matrix.receipt().calculationHash(), permuted.receipt().calculationHash());
    }

    @Test
    void failureReturnsNoMatrixAndFiniteDecayIsAllowed() {
        assertThrows(IllegalArgumentException.class, () -> KineticMatrix.compute(List.of()));
        assertThrows(NullPointerException.class, () -> KineticMatrix.compute(null));
        var origin = single(1, ORIGIN);
        var far = single(1, new Point3D(Double.MAX_VALUE, 0, 0));
        var failure = assertThrows(ArithmeticException.class, () -> KineticMatrix.compute(List.of(origin, far)));
        assertTrue(failure.getMessage().contains("NUMERICAL_FAILURE"));
        assertEquals(0, SKinetic.between(new PrimitiveGaussian(ORIGIN, 1),
                new PrimitiveGaussian(new Point3D(1000, 0, 0), 1)), 0);
        var tooDiffuse = new PrimitiveGaussian(ORIGIN, Double.MIN_VALUE);
        assertThrows(ArithmeticException.class, () -> SKinetic.between(tooDiffuse, tooDiffuse));
        var tooTight = single(Double.MAX_VALUE, ORIGIN);
        assertThrows(ArithmeticException.class, () -> KineticMatrix.compute(List.of(tooTight)));
    }

    @Test
    void logScalingDoesNotLoseRepresentableKineticWhenOverlapUnderflows() {
        double alpha = 1e200;
        var a = new PrimitiveGaussian(ORIGIN, alpha);
        var b = new PrimitiveGaussian(new Point3D(StrictMath.sqrt(1500 / alpha), 0, 0), alpha);
        assertEquals(0, SOverlap.between(a, b));
        // Independent, safely factored analytic equal-exponent expression.
        double expected = (alpha / 2) * (3 - 1500) * StrictMath.exp(-700) * StrictMath.exp(-50);
        assertTrue(expected < 0);
        assertEquals(expected, SKinetic.between(a, b), StrictMath.abs(expected) * 2e-12);
    }

    @Test
    void replayAndCacheIdentityAreOperatorSpecific() throws IOException {
        var basis = h2();
        var kinetic = KineticMatrix.compute(basis);
        for (int i = 0; i < 20; i++) assertEquals(kinetic.receipt(), KineticMatrix.compute(h2()).receipt());
        assertEquals(ScientificStatus.SCREENING_ONLY, kinetic.receipt().status());
        assertFalse(kinetic.receipt().reason().isBlank());
        assertTrue(kinetic.receipt().protocol().contains("hartree"));
        var overlap = OverlapMatrix.compute(basis);
        assertEquals(overlap.receipt().basisGeometryHash(), kinetic.receipt().basisGeometryHash());
        assertNotEquals(overlap.receipt().calculationHash(), kinetic.receipt().calculationHash());
        assertNotEquals(overlap.receipt().resultHash(), kinetic.receipt().resultHash());
        assertNotEquals(overlap.receipt().receiptHash(), kinetic.receipt().receiptHash());
        assertEquals("3f57c97401fa3b1bcc9e620245f4771842f3d35ff316fca0b23343cfa40c4dc9", overlap.receipt().receiptHash());
        var changed = KineticMatrix.compute(List.of(basis.getFirst(), single(1, new Point3D(1.5, 0, 0))));
        assertNotEquals(kinetic.receipt().calculationHash(), changed.receipt().calculationHash());
        var one = KineticMatrix.compute(List.of(single(1, ORIGIN)));
        var two = KineticMatrix.compute(List.of(single(2, ORIGIN)));
        assertNotEquals(one.receipt().calculationHash(), two.receipt().calculationHash());
        var scaled = new ContractedGaussian(List.of(new GaussianTerm(new PrimitiveGaussian(ORIGIN, 1), 2)));
        assertNotEquals(one.receipt().calculationHash(), KineticMatrix.compute(List.of(scaled)).receipt().calculationHash());
        assertEquals(one.receipt(), KineticMatrix.compute(List.of(single(1, new Point3D(-0.0, 0, 0)))).receipt());
    }

    @Test
    void independentPyscfLibcintKineticReferences() throws IOException {
        var cases = referenceCases();
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("reference/kinetic.csv")) {
            assertNotNull(input);
            bytes = input.readAllBytes();
        }
        assertEquals("85ed6ed231cbb1dc3d0e263112fee048e6805aa9beb26892f2405af058b0170f", ContentHash.sha256(bytes));
        var lines = new String(bytes, StandardCharsets.UTF_8).lines().toList();
        assertEquals("case,row,column,kinetic_hartree", lines.getFirst());
        double maxError = 0;
        var seen = new HashSet<String>();
        for (String line : lines.subList(1, lines.size())) {
            String[] columns = line.split(",");
            assertTrue(seen.add(String.join(",", columns[0], columns[1], columns[2])), "Duplicate reference entry");
            var matrix = cases.get(columns[0]);
            assertNotNull(matrix);
            double expected = Double.parseDouble(columns[3]);
            double actual = matrix.get(Integer.parseInt(columns[1]), Integer.parseInt(columns[2]));
            maxError = StrictMath.max(maxError, StrictMath.abs(actual - expected));
            assertEquals(expected, actual, TOLERANCE, line);
            assertEquals(ScientificStatus.SCREENING_ONLY, matrix.receipt().status());
        }
        assertEquals(22, seen.size());
        assertEquals(cases.values().stream().mapToInt(m -> m.size() * m.size()).sum(), seen.size());
        System.out.println("AETHER_KINETIC_EXTERNAL_REFERENCE_ENTRIES=" + seen.size() + " MAX_REFERENCE_ERROR=" + maxError);
        var h2 = cases.get("sto3g_h2");
        System.out.println("H2_T_MATRIX_1_4_BOHR=[[" + h2.get(0, 0) + ", " + h2.get(0, 1)
                + "], [" + h2.get(1, 0) + ", " + h2.get(1, 1) + "]] hartree");
    }

    @Test
    void separateJava21ProcessesProduceByteIdenticalReceipts(@TempDir Path directory) throws IOException, InterruptedException {
        byte[] first = runReceiptProcess(directory, "en", "US");
        byte[] second = runReceiptProcess(directory, "tr", "TR");
        assertArrayEquals(first, second);
        byte[] local = KineticMatrix.compute(h2()).receipt().toString().getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(local, first);
        System.out.println("AETHER_KINETIC_SEPARATE_JVM_RECEIPT_BYTES=PASS; UTF8_SHA256=" + ContentHash.sha256(first));
        System.out.println("AETHER_KINETIC_RECEIPT=" + new String(first, StandardCharsets.UTF_8));
    }

    private static byte[] runReceiptProcess(Path directory, String language, String country) throws IOException, InterruptedException {
        Path output = directory.resolve(language + ".receipt");
        Path errors = directory.resolve(language + ".stderr");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Duser.language=" + language, "-Duser.country=" + country, "-cp", classpath,
                KineticReceiptReplay.class.getName()).redirectOutput(output.toFile()).redirectError(errors.toFile()).start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Separate JVM timed out");
            assertEquals(0, process.exitValue(), () -> {
                try { return Files.readString(errors); }
                catch (IOException e) { return e.toString(); }
            });
            return Files.readAllBytes(output);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
