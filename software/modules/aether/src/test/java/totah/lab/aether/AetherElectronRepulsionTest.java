package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.GaussianTerm;
import totah.lab.aether.basis.PrimitiveGaussian;
import totah.lab.aether.integral.ElectronRepulsionIntegral;
import totah.lab.aether.integral.SOverlap;
import totah.lab.aether.matrix.CoreHamiltonianCalculator;
import totah.lab.aether.matrix.ElectronRepulsionCalculator;
import totah.lab.aether.matrix.ElectronRepulsionTensor;
import totah.lab.aether.matrix.KineticMatrix;
import totah.lab.aether.matrix.OverlapMatrix;
import totah.lab.aether.model.NuclearCenter;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.gaia.geometry.Point3D;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.aether.KineticTestCases.*;
import static totah.lab.aether.EriTestCases.calculate;
import static totah.lab.aether.NuclearTestCases.h2Nuclei;
import static totah.lab.aether.NuclearTestCases.hydrogen;

class AetherElectronRepulsionTest {
    private static final double TOLERANCE = 2e-13;

    @ParameterizedTest
    @ValueSource(doubles = {0.01, 0.7, 1, 100})
    void analyticPrimitiveSelfRepulsion(double exponent) {
        var p = new PrimitiveGaussian(ORIGIN, exponent);
        assertEquals(2 * StrictMath.sqrt(exponent / StrictMath.PI),
                ElectronRepulsionIntegral.between(p, p, p, p), TOLERANCE);
    }

    @Test
    void analyticFourUnequalExponentsAtCoincidentCenters() {
        var a = new PrimitiveGaussian(ORIGIN, 0.5);
        var b = new PrimitiveGaussian(ORIGIN, 1.5);
        var c = new PrimitiveGaussian(ORIGIN, 0.75);
        var d = new PrimitiveGaussian(ORIGIN, 1.25);
        double p = a.exponent() + b.exponent();
        double q = c.exponent() + d.exponent();
        // Independent textbook unnormalized prefactor, multiplied by primitive normalizations.
        double expected = 2 * StrictMath.pow(StrictMath.PI, 2.5) / (p * q * StrictMath.sqrt(p + q))
                * a.normalization() * b.normalization() * c.normalization() * d.normalization();
        assertEquals(expected, ElectronRepulsionIntegral.between(a, b, c, d), TOLERANCE);
    }

    @Test
    void analyticSeparatedPrimitivesWithCoincidentProductCenters() {
        var a = new PrimitiveGaussian(new Point3D(-1, 0, 0), 1);
        var b = new PrimitiveGaussian(new Point3D(1, 0, 0), 1);
        var c = new PrimitiveGaussian(new Point3D(-2, 0, 0), 1);
        var d = new PrimitiveGaussian(new Point3D(2, 0, 0), 1);
        assertEquals(2 / StrictMath.sqrt(StrictMath.PI) * StrictMath.exp(-10),
                ElectronRepulsionIntegral.between(a, b, c, d), 1e-18);
    }

    @ParameterizedTest
    @ValueSource(doubles = {1e-24, 1e-16, 1e-8, 1e-4})
    void analyticNearCoincidentDensityPairsAndSmallBoysArguments(double t) {
        var a = new PrimitiveGaussian(ORIGIN, 1);
        var b = new PrimitiveGaussian(new Point3D(StrictMath.sqrt(t), 0, 0), 1);
        double series = 1 - t / 3 + t * t / 10 - t * t * t / 42;
        assertEquals(2 / StrictMath.sqrt(StrictMath.PI) * series,
                ElectronRepulsionIntegral.between(a, a, b, b), 2e-15);
    }

    @ParameterizedTest
    @ValueSource(doubles = {6, 10, 10000})
    void analyticLargeBoysArgumentCoulombLimit(double distance) {
        var a = new PrimitiveGaussian(ORIGIN, 1);
        var b = new PrimitiveGaussian(new Point3D(distance, 0, 0), 1);
        // erf(distance)/distance; erfc(6) < 2.2e-17, below binary64 comparison precision.
        assertEquals(1 / distance, ElectronRepulsionIntegral.between(a, a, b, b), 2e-15 / distance);
    }

    @Test
    void logScalingPreservesRepresentableEriAfterOverlapUnderflows() {
        double alpha = 1e200;
        double x = StrictMath.sqrt(1500 / alpha);
        var a = new PrimitiveGaussian(new Point3D(-x / 2, 0, 0), alpha);
        var b = new PrimitiveGaussian(new Point3D(x / 2, 0, 0), alpha);
        var c = new PrimitiveGaussian(ORIGIN, alpha);
        assertEquals(0, SOverlap.between(a, b));
        double expected = 2 * StrictMath.sqrt(alpha / StrictMath.PI) * StrictMath.exp(-700) * StrictMath.exp(-50);
        double actual = ElectronRepulsionIntegral.between(a, b, c, c);
        assertTrue(actual > 0);
        assertEquals(expected, actual, expected * 2e-12);
    }

    @Test
    void primitiveAndContractedKernelsObeyAllEightPermutations() throws IOException {
        var basis = new ArrayList<>(h2());
        basis.add(signed(new Point3D(0.4, -0.7, 1.1)));
        basis.add(single(0.7, new Point3D(-0.8, 0.5, 0.2)));
        double primitive = Double.NaN;
        double contracted = Double.NaN;
        for (int[] order : permutations(0, 1, 2, 3)) {
            var a = basis.get(order[0]); var b = basis.get(order[1]);
            var c = basis.get(order[2]); var d = basis.get(order[3]);
            double p = ElectronRepulsionIntegral.between(a.terms().getFirst().primitive(), b.terms().getFirst().primitive(),
                    c.terms().getFirst().primitive(), d.terms().getFirst().primitive());
            double contractedValue = ElectronRepulsionIntegral.between(a, b, c, d);
            if (Double.isNaN(primitive)) { primitive = p; contracted = contractedValue; }
            assertEquals(primitive, p, TOLERANCE);
            assertEquals(contracted, contractedValue, TOLERANCE);
        }
    }

    private static int[][] permutations(int i, int j, int k, int l) {
        return new int[][]{{i,j,k,l}, {j,i,k,l}, {i,j,l,k}, {j,i,l,k},
                {k,l,i,j}, {l,k,i,j}, {k,l,j,i}, {l,k,j,i}};
    }

    @Test
    void packedTensorHasEightfoldBitIdenticalSymmetryAndNoMissingEntries() {
        var tensor = calculate(EriTestCases.fourDistinct());
        assertEquals(55, tensor.uniqueQuartetCount());
        var canonicalQuartets = new HashSet<String>();
        for (int i = 0; i < tensor.size(); i++) for (int j = 0; j < tensor.size(); j++) {
            for (int k = 0; k < tensor.size(); k++) for (int l = 0; l < tensor.size(); l++) {
                double value = tensor.get(i, j, k, l);
                for (var order : permutations(i, j, k, l)) {
                    assertEquals(Double.doubleToLongBits(value),
                            Double.doubleToLongBits(tensor.get(order[0], order[1], order[2], order[3])));
                }
                // Compare every remapped slot with a direct kernel evaluation, not merely itself.
                var b = tensor.functions();
                assertEquals(ElectronRepulsionIntegral.between(b.get(i), b.get(j), b.get(k), b.get(l)), value, TOLERANCE);
                if (i >= j && k >= l && (i > k || (i == k && j >= l))) {
                    canonicalQuartets.add(i + "," + j + "," + k + "," + l);
                }
            }
        }
        assertEquals(canonicalQuartets.size(), tensor.uniqueQuartetCount());
    }

    @Test
    void contractedSelfRepulsionAndCoefficientScaling() throws IOException {
        var h = h2().getFirst();
        var signed = signed(ORIGIN);
        for (var function : List.of(h, signed)) {
            double self = ElectronRepulsionIntegral.between(function, function, function, function);
            assertTrue(self > 0);
            for (double scale : new double[]{2, -2, 1e60, 1e-3}) {
                var scaled = new ContractedGaussian(function.terms().stream()
                        .map(t -> new GaussianTerm(t.primitive(), scale * t.coefficient())).toList());
                assertEquals(self, ElectronRepulsionIntegral.between(scaled, scaled, scaled, scaled), TOLERANCE);
                assertEquals(StrictMath.copySign(self, scale),
                        ElectronRepulsionIntegral.between(scaled, function, function, function), TOLERANCE);
            }
        }
    }

    @Test
    void signedContractionSummationAndPrimitiveReordering() {
        var p = new PrimitiveGaussian(ORIGIN, 1);
        var cancellation = new ContractedGaussian(List.of(new GaussianTerm(p, 3), new GaussianTerm(p, -2), new GaussianTerm(p, 0)));
        double expected = 2 / StrictMath.sqrt(StrictMath.PI);
        assertEquals(expected, ElectronRepulsionIntegral.between(cancellation, cancellation, cancellation, cancellation), TOLERANCE);
        var reversed = new ContractedGaussian(cancellation.terms().reversed());
        assertEquals(expected, ElectronRepulsionIntegral.between(reversed, reversed, reversed, reversed), TOLERANCE);
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
        var movedBasis = basis.stream().map(c -> new ContractedGaussian(c.terms().stream().map(t ->
                new GaussianTerm(new PrimitiveGaussian(transform.apply(t.primitive().centerBohr()), t.primitive().exponent()),
                        t.coefficient())).toList())).toList();
        var original = calculate(basis);
        var moved = calculate(movedBasis);
        assertNotEquals(original.receipt().systemHash(), moved.receipt().systemHash());
        assertNotEquals(original.receipt().basisGeometryHash(), moved.receipt().basisGeometryHash());
        compareRemapped(original, moved, new int[]{0,1,2});
    }

    @Test
    void basisPermutationPreservesEntriesUnderExplicitRemapping() {
        var basis = EriTestCases.fourDistinct();
        var system = calculate(basis).system();
        var original = new ElectronRepulsionCalculator(system, basis).calculate();
        int[] permutation = {2,0,3,1};
        var reordered = List.of(basis.get(2), basis.get(0), basis.get(3), basis.get(1));
        var moved = new ElectronRepulsionCalculator(system, reordered).calculate();
        compareRemapped(original, moved, permutation);
        assertEquals(original.receipt().systemHash(), moved.receipt().systemHash());
        assertNotEquals(original.receipt().basisGeometryHash(), moved.receipt().basisGeometryHash());
        assertNotEquals(original.receipt().receiptHash(), moved.receipt().receiptHash());
    }

    private static void compareRemapped(ElectronRepulsionTensor original, ElectronRepulsionTensor remapped, int[] old) {
        for (int i = 0; i < old.length; i++) for (int j = 0; j < old.length; j++) {
            for (int k = 0; k < old.length; k++) for (int l = 0; l < old.length; l++) {
                assertEquals(original.get(old[i], old[j], old[k], old[l]), remapped.get(i,j,k,l), TOLERANCE);
            }
        }
    }

    @Test
    void contextIdentityReusesHcoreAndDistinguishesSystemsAndBases() throws IOException {
        var basis = h2();
        var h = CoreReceiptReplay.h2Matrix();
        var eri = new ElectronRepulsionCalculator(h.system(), basis).calculate();
        assertEquals(h.receipt().basisGeometryHash(), eri.receipt().basisGeometryHash());
        assertEquals(h.receipt().systemHash(), eri.receipt().systemHash());
        assertNotEquals(h.receipt().calculationHash(), eri.receipt().calculationHash());
        for (var system : List.of(new QuantumSystem(h2Nuclei(), 1, 2), new QuantumSystem(h2Nuclei(), 0, 3),
                new QuantumSystem(List.of(hydrogen(ORIGIN), hydrogen(new Point3D(1.5, 0, 0))), 0, 1))) {
            var other = new ElectronRepulsionCalculator(system, basis).calculate();
            // ERIs depend on basis functions, not on point nuclei or electron count/spin.
            assertEquals(eri.receipt().resultHash(), other.receipt().resultHash());
            assertNotEquals(eri.receipt().systemHash(), other.receipt().systemHash());
            assertNotEquals(eri.receipt().receiptHash(), other.receipt().receiptHash());
        }
        var changedBasis = List.of(single(1, ORIGIN), basis.get(1));
        assertNotEquals(eri.receipt().basisGeometryHash(), new ElectronRepulsionCalculator(h.system(), changedBasis)
                .calculate().receipt().basisGeometryHash());
    }

    @Test
    void deterministicReceiptAndImmutableInputOutput() throws IOException {
        var basis = new ArrayList<>(h2());
        var system = new QuantumSystem(h2Nuclei(), 0, 1);
        var calculator = new ElectronRepulsionCalculator(system, basis);
        basis.clear();
        var first = calculator.calculate();
        for (int i = 0; i < 20; i++) assertEquals(first.receipt(), calculator.calculate().receipt());
        assertEquals("266041fdbefabafb4797f23c8f40b13e885c161496ab6010d4a8358272e33d51", first.receipt().receiptHash());
        assertEquals(ScientificStatus.SCREENING_ONLY, first.receipt().status());
        assertThrows(UnsupportedOperationException.class, () -> first.functions().clear());
        assertThrows(UnsupportedOperationException.class, () -> first.system().nuclei().clear());
        assertFalse(first.receipt().toString().contains("elapsed"));
    }

    @Test
    void countersMatchOrderedPrimitiveExpansionsAndUniqueStorage() throws IOException {
        var h2 = calculate(h2());
        var counts = h2.performanceCounters();
        assertEquals(6, h2.uniqueQuartetCount());
        assertEquals(6, counts.contractedQuartetsEvaluated());
        assertEquals(6, counts.symmetryUniqueQuartets());
        assertEquals(486, counts.primitiveQuartetsEvaluated()); // 6 * 3^4
        assertEquals(0, counts.cacheHits());
        assertTrue(counts.elapsedNanos() >= 0);
        for (int i = 0; i < 10; i++) h2.get(1,0,1,0);
        assertSame(counts, h2.performanceCounters()); // retrieval does not mutate evaluation evidence
        var mixed = calculate(List.of(single(1, ORIGIN), h2().get(1)));
        // (00|00),(10|00),(10|10),(11|00),(11|10),(11|11): 1+3+9+9+27+81.
        assertEquals(130, mixed.performanceCounters().primitiveQuartetsEvaluated());
        System.out.println("AETHER_ERI_H2_PERFORMANCE=" + counts);
    }

    @Test
    void invalidInputIndicesAndUnrepresentableArithmeticFailClosed() throws IOException {
        var system = new QuantumSystem(h2Nuclei(), 0, 1);
        assertThrows(IllegalArgumentException.class, () -> new ElectronRepulsionCalculator(system, List.of()));
        assertThrows(NullPointerException.class, () -> new ElectronRepulsionCalculator(null, h2()));
        assertThrows(NullPointerException.class, () -> new ElectronRepulsionCalculator(system, null));
        assertThrows(IllegalArgumentException.class, () -> new ElectronRepulsionCalculator(system,
                java.util.Collections.nCopies(362, single(1, ORIGIN))).calculate());
        var t = calculate(h2());
        for (int[] indices : new int[][]{{-1,0,0,0},{0,2,0,0},{0,0,2,0},{0,0,0,-1}}) {
            assertThrows(IndexOutOfBoundsException.class, () -> t.get(indices[0],indices[1],indices[2],indices[3]));
        }
        var p = new PrimitiveGaussian(ORIGIN, 1);
        var far = new PrimitiveGaussian(new Point3D(Double.MAX_VALUE, 0, 0), 1);
        var huge = new PrimitiveGaussian(ORIGIN, Double.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> ElectronRepulsionIntegral.between(p,p,far,far));
        assertThrows(ArithmeticException.class, () -> ElectronRepulsionIntegral.between(huge,huge,p,p));
        var failed = assertThrows(ArithmeticException.class, () -> calculate(List.of(single(1, ORIGIN),
                single(1, new Point3D(Double.MAX_VALUE, 0, 0)))));
        assertTrue(failed.getMessage().contains("NUMERICAL_FAILURE"));
    }

    @Test
    void allPreviousSTVHcoreReceiptsRemainByteIdentical() throws IOException {
        var receipts = new StringBuilder();
        for (var entry : new TreeMap<>(NuclearTestCases.referenceCases()).entrySet()) {
            var v = entry.getValue();
            receipts.append(entry.getKey()).append('\n').append(OverlapMatrix.compute(v.functions()).receipt())
                    .append('\n').append(KineticMatrix.compute(v.functions()).receipt()).append('\n').append(v.receipt()).append('\n');
            if (v.nuclei().stream().allMatch(n -> n.charge() == 1)) {
                var system = new QuantumSystem(v.nuclei(), 0, v.nuclei().size() % 2 + 1);
                receipts.append(new CoreHamiltonianCalculator(system, v.functions()).calculate().receipt()).append('\n');
            }
        }
        // Pre-Milestone-4 snapshot: 45 S/T/V plus 14 Hcore complete receipts.
        assertEquals("befc6eb930845c916cdd03d378327d2f1be1811a0a5caed9934a3211ea54991a", ContentHash.sha256(receipts.toString()));
    }

    @Test
    void independentLibcintIndividualTensorEntries() throws IOException {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("reference/eri.csv")) {
            assertNotNull(input); bytes = input.readAllBytes();
        }
        assertEquals("1abfe76bd096a5020fc3c37232683077f4732884cfb9a8da02dffbd0be515960", ContentHash.sha256(bytes));
        var lines = new String(bytes, StandardCharsets.UTF_8).lines().toList();
        assertEquals("case,i,j,k,l,eri_hartree", lines.getFirst());
        var cases = EriTestCases.referenceCases();
        double maxError = 0;
        var seen = new HashSet<String>();
        for (var line : lines.subList(1, lines.size())) {
            var c = line.split(",");
            assertTrue(seen.add(String.join(",", c[0], c[1], c[2], c[3], c[4])));
            var tensor = cases.get(c[0]); assertNotNull(tensor);
            double actual = tensor.get(Integer.parseInt(c[1]),Integer.parseInt(c[2]),Integer.parseInt(c[3]),Integer.parseInt(c[4]));
            double expected = Double.parseDouble(c[5]);
            maxError = StrictMath.max(maxError, StrictMath.abs(expected - actual));
            assertEquals(expected, actual, TOLERANCE, line);
        }
        assertEquals(370, seen.size());
        assertEquals(cases.values().stream().mapToInt(t -> t.size()*t.size()*t.size()*t.size()).sum(), seen.size());
        System.out.println("AETHER_ERI_EXTERNAL_REFERENCE_ENTRIES=" + seen.size() + " MAX_REFERENCE_ERROR=" + maxError);
        var h = cases.get("sto3g_h2");
        System.out.println("H2_UNIQUE_ERI_VALUES_1_4_BOHR=(00|00):" + h.get(0,0,0,0)
                + ";(10|00):" + h.get(1,0,0,0) + ";(10|10):" + h.get(1,0,1,0)
                + ";(11|00):" + h.get(1,1,0,0) + ";(11|10):" + h.get(1,1,1,0) + ";(11|11):" + h.get(1,1,1,1));
    }

    @Test
    void separateJava21ReceiptsAreByteIdentical(@TempDir Path directory) throws IOException, InterruptedException {
        byte[] first = replay(directory, "en", "US");
        byte[] second = replay(directory, "tr", "TR");
        assertArrayEquals(first, second);
        assertArrayEquals(calculate(h2()).receipt().toString().getBytes(StandardCharsets.UTF_8), first);
        assertEquals("9158f2010e88615052a45e9613ba3e562d4a5346014b5feffd91f1cb8bc00ab6", ContentHash.sha256(first));
        System.out.println("AETHER_ERI_SEPARATE_JVM_RECEIPT=PASS; UTF8_SHA256=" + ContentHash.sha256(first));
        System.out.println("AETHER_ERI_RECEIPT_HASH=" + calculate(h2()).receipt().receiptHash());
    }

    private static byte[] replay(Path directory, String language, String country) throws IOException, InterruptedException {
        Path output = directory.resolve(language + ".receipt");
        Path error = directory.resolve(language + ".stderr");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Duser.language=" + language, "-Duser.country=" + country, "-cp", classpath,
                EriReceiptReplay.class.getName()).redirectOutput(output.toFile()).redirectError(error.toFile()).start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Separate JVM timed out");
            assertEquals(0, process.exitValue(), Files.readString(error));
            return Files.readAllBytes(output);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
