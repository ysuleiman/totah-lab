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
import totah.lab.aether.integral.SNuclearAttraction;
import totah.lab.aether.integral.SOverlap;
import totah.lab.aether.matrix.KineticMatrix;
import totah.lab.aether.matrix.OverlapMatrix;
import totah.lab.aether.matrix.NuclearAttractionMatrix;
import totah.lab.aether.model.NuclearCenter;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.gaia.geometry.Point3D;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.aether.KineticTestCases.*;
import static totah.lab.aether.NuclearTestCases.*;

class AetherNuclearAttractionTest {
    private static final double TOLERANCE = 2e-13;

    @ParameterizedTest
    @ValueSource(doubles = {0.01, 0.7, 1, 100})
    void analyticPrimitiveSelfAtNucleus(double alpha) {
        var p = new PrimitiveGaussian(ORIGIN, alpha);
        assertEquals(-2 * StrictMath.sqrt(2 * alpha / StrictMath.PI),
                SNuclearAttraction.between(p, p, hydrogen(ORIGIN)), TOLERANCE);
    }

    @Test
    void analyticUnequalCoincidentAndSeparatedProductCenter() {
        var a = new PrimitiveGaussian(new Point3D(-1, 0, 0), 0.5);
        var b = new PrimitiveGaussian(new Point3D(1, 0, 0), 1.5);
        var nucleus = hydrogen(new Point3D(0.5, 0, 0));
        double expected = -2 * StrictMath.sqrt(2 / StrictMath.PI)
                * StrictMath.pow(StrictMath.sqrt(3) / 2, 1.5) * StrictMath.exp(-1.5);
        assertEquals(expected, SNuclearAttraction.between(a, b, nucleus), TOLERANCE);
        assertEquals(SNuclearAttraction.between(a, b, nucleus), SNuclearAttraction.between(b, a, nucleus));
        var coincidentA = new PrimitiveGaussian(ORIGIN, 0.5);
        var coincidentB = new PrimitiveGaussian(ORIGIN, 1.5);
        assertEquals(expected / StrictMath.exp(-1.5),
                SNuclearAttraction.between(coincidentA, coincidentB, hydrogen(ORIGIN)), TOLERANCE);
    }

    @Test
    void analyticDistantNucleusAndChargeScaling() {
        var a = new PrimitiveGaussian(ORIGIN, 1);
        double near = SNuclearAttraction.between(a, a, hydrogen(ORIGIN));
        assertEquals(2.5 * near, SNuclearAttraction.between(a, a, new NuclearCenter(ORIGIN, 2.5)), TOLERANCE);
        for (double r : new double[]{10, 100, 1e6}) {
            assertEquals(-1 / r, SNuclearAttraction.between(a, a, hydrogen(new Point3D(r, 0, 0))), 2e-15 / r);
        }
        var b = new PrimitiveGaussian(H2_SECOND, 1);
        // Product center is the midpoint: asymptotic potential is -Z S_ab / distance.
        double r = 1000;
        assertEquals(-SOverlap.between(a, b) / r,
                SNuclearAttraction.between(a, b, hydrogen(new Point3D(0.7, r, 0))), TOLERANCE);
    }

    @Test
    void contractedChargeScalingAdditivityAndSymmetry() throws IOException {
        var basis = h2();
        var a = basis.get(0);
        var b = signed(new Point3D(0.4, -0.7, 1.1));
        double all = SNuclearAttraction.between(a, b, h2Nuclei());
        double sum = SNuclearAttraction.between(a, b, List.of(h2Nuclei().get(0)))
                + SNuclearAttraction.between(a, b, List.of(h2Nuclei().get(1)));
        assertEquals(sum, all, TOLERANCE);
        assertEquals(all, SNuclearAttraction.between(b, a, h2Nuclei()), TOLERANCE);
        var doubled = h2Nuclei().stream().map(n -> new NuclearCenter(n.centerBohr(), 2 * n.charge())).toList();
        assertEquals(2 * all, SNuclearAttraction.between(a, b, doubled), TOLERANCE);
        var scaled = new ContractedGaussian(a.terms().stream()
                .map(t -> new GaussianTerm(t.primitive(), 2 * t.coefficient())).toList());
        assertEquals(all, SNuclearAttraction.between(scaled, b, h2Nuclei()), TOLERANCE);
    }

    @Test
    void completeSystemTranslationInvariance() throws IOException {
        checkTransform(p -> new Point3D(p.x() + 2.25, p.y() - 3.5, p.z() + 0.75));
    }

    @Test
    void completeSystemRotationInvariance() throws IOException {
        checkTransform(p -> new Point3D(p.z(), 0.6 * p.x() - 0.8 * p.y(), 0.8 * p.x() + 0.6 * p.y()));
    }

    private static void checkTransform(UnaryOperator<Point3D> transform) throws IOException {
        var basis = new ArrayList<>(h2());
        basis.add(signed(new Point3D(0.4, -0.7, 1.1)));
        var nuclei = List.of(h2Nuclei().get(0), h2Nuclei().get(1), hydrogen(new Point3D(-0.8, 0.5, 0.2)));
        var movedBasis = basis.stream().map(c -> new ContractedGaussian(c.terms().stream().map(t ->
                new GaussianTerm(new PrimitiveGaussian(transform.apply(t.primitive().centerBohr()), t.primitive().exponent()),
                        t.coefficient())).toList())).toList();
        var movedNuclei = nuclei.stream().map(n -> new NuclearCenter(transform.apply(n.centerBohr()), n.charge())).toList();
        var original = NuclearAttractionMatrix.compute(basis, nuclei);
        var moved = NuclearAttractionMatrix.compute(movedBasis, movedNuclei);
        for (int i = 0; i < original.size(); i++) {
            for (int j = 0; j < original.size(); j++) assertEquals(original.get(i, j), moved.get(i, j), TOLERANCE);
        }
    }

    @Test
    void matrixIsImmutableSymmetricAndPermutationCovariant() throws IOException {
        var basis = new ArrayList<>(h2());
        basis.add(signed(new Point3D(0.4, -0.7, 1.1)));
        var nuclei = new ArrayList<>(h2Nuclei());
        var matrix = NuclearAttractionMatrix.compute(basis, nuclei);
        var permuted = NuclearAttractionMatrix.compute(List.of(basis.get(2), basis.get(0), basis.get(1)), nuclei.reversed());
        basis.clear(); nuclei.clear();
        assertEquals(3, matrix.size());
        assertEquals(2, matrix.nuclei().size());
        assertThrows(UnsupportedOperationException.class, () -> matrix.nuclei().clear());
        assertThrows(UnsupportedOperationException.class, () -> matrix.functions().clear());
        int[] old = {2, 0, 1};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(matrix.get(i, j), matrix.get(j, i));
                assertEquals(matrix.get(old[i], old[j]), permuted.get(i, j), TOLERANCE);
            }
        }
        assertNotEquals(matrix.receipt().calculationHash(), permuted.receipt().calculationHash());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    void invalidNuclearChargesFail(double charge) {
        assertThrows(IllegalArgumentException.class, () -> new NuclearCenter(ORIGIN, charge));
    }

    @Test
    void invalidInputAndNumericalFailureProduceNoMatrix() {
        assertThrows(NullPointerException.class, () -> new NuclearCenter(null, 1));
        assertThrows(IllegalArgumentException.class, () -> NuclearAttractionMatrix.compute(List.of(), h2Nuclei()));
        assertThrows(IllegalArgumentException.class, () -> NuclearAttractionMatrix.compute(List.of(single(1, ORIGIN)), List.of()));
        var error = assertThrows(ArithmeticException.class, () -> NuclearAttractionMatrix.compute(List.of(single(1, ORIGIN)),
                List.of(hydrogen(new Point3D(Double.MAX_VALUE, 0, 0)))));
        assertTrue(error.getMessage().contains("NUMERICAL_FAILURE"));
        assertThrows(ArithmeticException.class, () -> NuclearAttractionMatrix.compute(List.of(single(1, ORIGIN)),
                List.of(new NuclearCenter(ORIGIN, Double.MAX_VALUE))));
        assertThrows(ArithmeticException.class, () -> NuclearAttractionMatrix.compute(List.of(single(Double.MAX_VALUE, ORIGIN)), h2Nuclei()));
    }

    @Test
    void logarithmicScalingPreservesRepresentableAttraction() {
        double alpha = 1e200;
        double distance = StrictMath.sqrt(1500 / alpha);
        var a = new PrimitiveGaussian(ORIGIN, alpha);
        var b = new PrimitiveGaussian(new Point3D(distance, 0, 0), alpha);
        var nucleus = hydrogen(new Point3D(distance / 2, 0, 0));
        assertEquals(0, SOverlap.between(a, b));
        double expected = -2 * StrictMath.sqrt(2 * alpha / StrictMath.PI) * StrictMath.exp(-700) * StrictMath.exp(-50);
        assertEquals(expected, SNuclearAttraction.between(a, b, nucleus), StrictMath.abs(expected) * 2e-12);
    }

    @Test
    void replayAndNuclearIdentityPreventWrongCacheReuse() throws IOException {
        var basis = h2();
        var first = h2Matrix();
        for (int i = 0; i < 20; i++) assertEquals(first.receipt(), h2Matrix().receipt());
        var moved = NuclearAttractionMatrix.compute(basis, List.of(hydrogen(ORIGIN), hydrogen(new Point3D(1.5, 0, 0))));
        var charged = NuclearAttractionMatrix.compute(basis, List.of(hydrogen(ORIGIN), new NuclearCenter(H2_SECOND, 2)));
        for (var changed : List.of(moved, charged)) {
            assertEquals(first.receipt().basisGeometryHash(), changed.receipt().basisGeometryHash());
            assertNotEquals(first.receipt().nuclearCentersHash(), changed.receipt().nuclearCentersHash());
            assertNotEquals(first.receipt().calculationHash(), changed.receipt().calculationHash());
            assertNotEquals(first.receipt().resultHash(), changed.receipt().resultHash());
            assertNotEquals(first.receipt().receiptHash(), changed.receipt().receiptHash());
        }
        assertNotEquals(first.get(0, 0), moved.get(0, 0));
        assertNotEquals(first.receipt().calculationHash(), NuclearAttractionMatrix.compute(basis, h2Nuclei().reversed()).receipt().calculationHash());
        var negativeZero = NuclearAttractionMatrix.compute(basis, List.of(hydrogen(new Point3D(-0.0, 0, 0)), hydrogen(H2_SECOND)));
        assertEquals(first.receipt(), negativeZero.receipt());
        assertEquals(ScientificStatus.SCREENING_ONLY, first.receipt().status());
        assertFalse(first.receipt().reason().isBlank());
        var overlap = OverlapMatrix.compute(basis);
        var kinetic = KineticMatrix.compute(basis);
        assertEquals(overlap.receipt().basisGeometryHash(), first.receipt().basisGeometryHash());
        assertNotEquals(overlap.receipt().calculationHash(), first.receipt().calculationHash());
        assertNotEquals(kinetic.receipt().calculationHash(), first.receipt().calculationHash());
        assertEquals("3f57c97401fa3b1bcc9e620245f4771842f3d35ff316fca0b23343cfa40c4dc9", overlap.receipt().receiptHash());
        assertEquals("cf80c3c693451abcedb128ae007f1dc9d733962bec90bba5ac7a4bee7512dbe7", kinetic.receipt().receiptHash());
    }

    @Test
    void independentLibcintNuclearReferences() throws IOException {
        var cases = NuclearTestCases.referenceCases();
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("reference/nuclear.csv")) {
            assertNotNull(input); bytes = input.readAllBytes();
        }
        assertEquals("d4e1ce8edf74f26ca7ce8b2d356816ff868eadb19bb13149e503a447c7029864", ContentHash.sha256(bytes));
        var lines = new String(bytes, StandardCharsets.UTF_8).lines().toList();
        assertEquals("case,row,column,nuclear_hartree", lines.getFirst());
        double maxError = 0;
        var seen = new HashSet<String>();
        for (String line : lines.subList(1, lines.size())) {
            String[] columns = line.split(",");
            assertTrue(seen.add(String.join(",", columns[0], columns[1], columns[2])));
            var matrix = cases.get(columns[0]); assertNotNull(matrix);
            double expected = Double.parseDouble(columns[3]);
            double actual = matrix.get(Integer.parseInt(columns[1]), Integer.parseInt(columns[2]));
            maxError = StrictMath.max(maxError, StrictMath.abs(actual - expected));
            assertEquals(expected, actual, TOLERANCE, line);
            assertEquals(ScientificStatus.SCREENING_ONLY, matrix.receipt().status());
        }
        assertEquals(30, seen.size());
        assertEquals(cases.values().stream().mapToInt(m -> m.size() * m.size()).sum(), seen.size());
        System.out.println("AETHER_NUCLEAR_EXTERNAL_REFERENCE_ENTRIES=" + seen.size() + " MAX_REFERENCE_ERROR=" + maxError);
        var h2 = cases.get("sto3g_h2");
        System.out.println("H2_V_MATRIX_1_4_BOHR=[[" + h2.get(0, 0) + ", " + h2.get(0, 1)
                + "], [" + h2.get(1, 0) + ", " + h2.get(1, 1) + "]] hartree");
    }

    @Test
    void separateJava21ReceiptsAreByteIdentical(@TempDir Path directory) throws IOException, InterruptedException {
        byte[] first = replay(directory, "en", "US");
        byte[] second = replay(directory, "tr", "TR");
        assertArrayEquals(first, second);
        assertArrayEquals(h2Matrix().receipt().toString().getBytes(StandardCharsets.UTF_8), first);
        System.out.println("AETHER_NUCLEAR_SEPARATE_JVM_RECEIPT=PASS; UTF8_SHA256=" + ContentHash.sha256(first));
        System.out.println("AETHER_NUCLEAR_RECEIPT=" + new String(first, StandardCharsets.UTF_8));
    }

    private static byte[] replay(Path directory, String language, String country) throws IOException, InterruptedException {
        Path output = directory.resolve(language + ".receipt");
        Path error = directory.resolve(language + ".stderr");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Duser.language=" + language, "-Duser.country=" + country, "-cp", classpath,
                NuclearReceiptReplay.class.getName()).redirectOutput(output.toFile()).redirectError(error.toFile()).start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Separate JVM timed out");
            assertEquals(0, process.exitValue(), Files.readString(error));
            return Files.readAllBytes(output);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
