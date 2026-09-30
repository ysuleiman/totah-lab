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
import org.junit.jupiter.params.provider.CsvSource;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.GaussianTerm;
import totah.lab.aether.basis.PrimitiveGaussian;
import totah.lab.aether.matrix.CoreHamiltonianCalculator;
import totah.lab.aether.matrix.CoreHamiltonianMatrix;
import totah.lab.aether.matrix.KineticMatrix;
import totah.lab.aether.matrix.NuclearAttractionMatrix;
import totah.lab.aether.matrix.OverlapMatrix;
import totah.lab.aether.model.NuclearCenter;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.gaia.geometry.Point3D;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.aether.KineticTestCases.*;
import static totah.lab.aether.NuclearTestCases.h2Nuclei;
import static totah.lab.aether.NuclearTestCases.hydrogen;

class AetherCoreHamiltonianTest {
    private static final double TOLERANCE = 2e-13;

    private static QuantumSystem h2System() { return new QuantumSystem(h2Nuclei(), 0, 1); }

    private static CoreHamiltonianCalculator calculator(List<ContractedGaussian> basis) {
        return new CoreHamiltonianCalculator(h2System(), basis);
    }

    @Test
    void elementWiseSumSymmetryAndUnmodifiedSources() throws IOException {
        for (var v : NuclearTestCases.referenceCases().values()) {
            if (v.nuclei().stream().anyMatch(n -> n.charge() != 1)) continue;
            var system = new QuantumSystem(v.nuclei(), 0, v.nuclei().size() % 2 + 1);
            var calculator = new CoreHamiltonianCalculator(system, v.functions());
            var t = KineticMatrix.compute(v.functions());
            var h = calculator.calculate(calculator.bind(t), calculator.bind(v));
            assertSame(t.receipt(), h.receipt().kineticReceipt());
            assertSame(v.receipt(), h.receipt().nuclearAttractionReceipt());
            assertSame(system, h.system());
            assertEquals(t.receipt().basisGeometryHash(), h.receipt().basisGeometryHash());
            assertEquals(ScientificStatus.SCREENING_ONLY, h.receipt().status());
            assertEquals(calculator.calculate().receipt(), h.receipt());
            for (int i = 0; i < h.size(); i++) {
                for (int j = 0; j < h.size(); j++) {
                    assertEquals(t.get(i, j) + v.get(i, j), h.get(i, j));
                    assertEquals(h.get(i, j), h.get(j, i));
                }
            }
        }
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
        var original = new CoreHamiltonianCalculator(new QuantumSystem(nuclei, 0, 2), basis).calculate();
        var moved = new CoreHamiltonianCalculator(new QuantumSystem(movedNuclei, 0, 2), movedBasis).calculate();
        assertNotEquals(original.receipt().systemHash(), moved.receipt().systemHash());
        assertNotEquals(original.receipt().basisGeometryHash(), moved.receipt().basisGeometryHash());
        for (int i = 0; i < original.size(); i++) {
            for (int j = 0; j < original.size(); j++) assertEquals(original.get(i, j), moved.get(i, j), TOLERANCE);
        }
    }

    @Test
    void basisPermutationIsCovariantAndChangesIdentity() throws IOException {
        var basis = new ArrayList<>(h2());
        basis.add(signed(new Point3D(0.4, -0.7, 1.1)));
        var original = calculator(basis).calculate();
        var permuted = calculator(List.of(basis.get(2), basis.get(0), basis.get(1))).calculate();
        int[] old = {2, 0, 1};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) assertEquals(original.get(old[i], old[j]), permuted.get(i, j), TOLERANCE);
        }
        assertNotEquals(original.receipt().basisGeometryHash(), permuted.receipt().basisGeometryHash());
        assertNotEquals(original.receipt().calculationHash(), permuted.receipt().calculationHash());
    }

    @Test
    void sameSizeReorderedMatricesAreRejected() throws IOException {
        var basis = h2();
        var calculator = calculator(basis);
        assertThrows(IllegalArgumentException.class, () -> calculator.bind(KineticMatrix.compute(basis.reversed())));
        assertThrows(IllegalArgumentException.class, () -> calculator.bind(NuclearAttractionMatrix.compute(basis.reversed(), h2Nuclei())));
        var other = calculator(basis.reversed());
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(other.bind(KineticMatrix.compute(basis.reversed())),
                calculator.bind(NuclearAttractionMatrix.compute(basis, h2Nuclei()))));
    }

    @Test
    void incompatibleBasisExponentsAndCoefficientsAreRejected() throws IOException {
        var basis = h2();
        var calculator = calculator(basis);
        var changedExponent = List.of(single(1, ORIGIN), basis.get(1));
        var first = basis.getFirst();
        var terms = new ArrayList<>(first.terms());
        terms.set(0, new GaussianTerm(terms.getFirst().primitive(), terms.getFirst().coefficient() + 0.01));
        var changedCoefficient = List.of(new ContractedGaussian(terms), basis.get(1));
        for (var changed : List.of(changedExponent, changedCoefficient)) {
            assertThrows(IllegalArgumentException.class, () -> calculator.bind(KineticMatrix.compute(changed)));
            assertThrows(IllegalArgumentException.class, () -> calculator.bind(NuclearAttractionMatrix.compute(changed, h2Nuclei())));
        }
    }

    @Test
    void differentPrimitiveOrderingFailsClosedEvenWhenMathematicallyEquivalent() throws IOException {
        var basis = h2();
        var changed = List.of(new ContractedGaussian(basis.getFirst().terms().reversed()), basis.get(1));
        assertThrows(IllegalArgumentException.class, () -> calculator(basis).bind(KineticMatrix.compute(changed)));
    }

    @Test
    void incompatibleBasisGeometryIsRejected() throws IOException {
        var basis = h2();
        var movedFunction = new ContractedGaussian(basis.get(1).terms().stream().map(t -> new GaussianTerm(
                new PrimitiveGaussian(new Point3D(1.5, 0, 0), t.primitive().exponent()), t.coefficient())).toList());
        var moved = List.of(basis.getFirst(), movedFunction);
        assertThrows(IllegalArgumentException.class, () -> calculator(basis).bind(KineticMatrix.compute(moved)));
        assertThrows(IllegalArgumentException.class, () -> calculator(basis).bind(NuclearAttractionMatrix.compute(moved, h2Nuclei())));
    }

    @Test
    void incompatibleNuclearGeometryChargeAndOrderingAreRejected() throws IOException {
        var basis = h2();
        var calculator = calculator(basis);
        for (var changed : List.of(
                List.of(hydrogen(ORIGIN), hydrogen(new Point3D(1.5, 0, 0))),
                List.of(hydrogen(ORIGIN), new NuclearCenter(H2_SECOND, 2)),
                h2Nuclei().reversed(), List.of(hydrogen(ORIGIN)))) {
            var v = NuclearAttractionMatrix.compute(basis, changed);
            assertEquals(basis.size(), v.size());
            assertThrows(IllegalArgumentException.class, () -> calculator.bind(v));
        }
    }

    @ParameterizedTest
    @CsvSource({"1,2", "0,3", "-1,2"})
    void incompatibleElectronicSystemBindingsAreRejected(int charge, int multiplicity) throws IOException {
        var basis = h2();
        var expected = calculator(basis);
        var other = new CoreHamiltonianCalculator(new QuantumSystem(h2Nuclei(), charge, multiplicity), basis);
        var t = KineticMatrix.compute(basis);
        var v = NuclearAttractionMatrix.compute(basis, h2Nuclei());
        assertThrows(IllegalArgumentException.class, () -> expected.calculate(other.bind(t), expected.bind(v)));
        assertThrows(IllegalArgumentException.class, () -> expected.calculate(expected.bind(t), other.bind(v)));
        assertThrows(IllegalArgumentException.class, () -> expected.calculate(other.bind(t), other.bind(v)));
        // Electron count/spin do not change these operators; explicit rebinding is valid reuse.
        var original = expected.calculate(expected.bind(t), expected.bind(v));
        var rebound = other.calculate(other.bind(t), other.bind(v));
        assertEquals(original.receipt().resultHash(), rebound.receipt().resultHash());
        assertNotEquals(original.receipt().systemHash(), rebound.receipt().systemHash());
        assertNotEquals(original.receipt().receiptHash(), rebound.receipt().receiptHash());
    }

    @Test
    void nuclearSystemBindingsCannotCrossContexts() throws IOException {
        var basis = h2();
        var expected = calculator(basis);
        var movedNuclei = List.of(hydrogen(ORIGIN), hydrogen(new Point3D(1.5, 0, 0)));
        var other = new CoreHamiltonianCalculator(new QuantumSystem(movedNuclei, 0, 1), basis);
        var t = KineticMatrix.compute(basis);
        var v = NuclearAttractionMatrix.compute(basis, movedNuclei);
        assertThrows(IllegalArgumentException.class, () -> expected.calculate(other.bind(t), other.bind(v)));
        assertThrows(IllegalArgumentException.class, () -> expected.calculate(expected.bind(t), other.bind(v)));
    }

    @Test
    void equivalentIndependentContextsAndSignedZeroAreCompatible() throws IOException {
        var expected = calculator(h2());
        var other = new CoreHamiltonianCalculator(new QuantumSystem(List.of(hydrogen(new Point3D(-0.0, 0, 0)),
                hydrogen(H2_SECOND)), 0, 1), h2());
        var h = expected.calculate(other.bind(KineticMatrix.compute(h2())),
                other.bind(NuclearAttractionMatrix.compute(h2(), h2Nuclei())));
        assertEquals(expected.calculate().receipt(), h.receipt());
    }

    @Test
    void systemBasisAndResultAreImmutable() throws IOException {
        var nuclei = new ArrayList<>(h2Nuclei());
        var basis = new ArrayList<>(h2());
        var system = new QuantumSystem(nuclei, 0, 1);
        var calculator = new CoreHamiltonianCalculator(system, basis);
        nuclei.clear(); basis.clear();
        var h = calculator.calculate();
        assertEquals(2, h.size());
        assertEquals(2, h.system().nuclei().size());
        assertThrows(UnsupportedOperationException.class, () -> h.functions().clear());
        assertThrows(UnsupportedOperationException.class, () -> h.system().nuclei().clear());
        assertThrows(IndexOutOfBoundsException.class, () -> h.get(2, 0));
    }

    @Test
    void invalidContextsAndMissingInputsFailClosed() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new QuantumSystem(List.of(), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new QuantumSystem(h2Nuclei(), 3, 1));
        assertThrows(IllegalArgumentException.class, () -> new QuantumSystem(h2Nuclei(), 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new QuantumSystem(h2Nuclei(), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new QuantumSystem(List.of(new NuclearCenter(ORIGIN, 2)), 0, 1));
        assertThrows(IllegalArgumentException.class, () -> calculator(List.of()));
        assertThrows(NullPointerException.class, () -> new CoreHamiltonianCalculator(null, h2()));
        var calculator = calculator(h2());
        assertThrows(NullPointerException.class, () -> calculator.bind((KineticMatrix) null));
        assertThrows(NullPointerException.class, () -> calculator.bind((NuclearAttractionMatrix) null));
        assertThrows(NullPointerException.class, () -> calculator.calculate(null, null));
    }

    @Test
    void deterministicReceiptCommitsToBothOperatorProtocolsAndSourceReceipts() throws IOException {
        var first = CoreReceiptReplay.h2Matrix();
        for (int i = 0; i < 20; i++) assertEquals(first.receipt(), CoreReceiptReplay.h2Matrix().receipt());
        assertEquals("a505c02c7a56fce7b05b564c5bdccba36ac6e6693db9372d6d7360b5a4687a77", first.receipt().receiptHash());
        assertTrue(first.receipt().protocol().contains("bohr;hartree"));
        assertTrue(first.receipt().protocol().contains("T={" + KineticMatrix.PROTOCOL + "}"));
        assertTrue(first.receipt().protocol().contains("V={" + NuclearAttractionMatrix.PROTOCOL + "}"));
        assertEquals(KineticMatrix.IMPLEMENTATION, first.receipt().kineticReceipt().implementation());
        assertEquals(NuclearAttractionMatrix.IMPLEMENTATION, first.receipt().nuclearAttractionReceipt().implementation());
        assertEquals("649fda68be7db876214d5de2a962152db85fc79b4f271c7111e75f277ab3355f",
                first.receipt().nuclearAttractionReceipt().receiptHash());
    }

    @Test
    void allExistingSTVReceiptBytesRemainFrozen() throws IOException {
        var receipts = new StringBuilder();
        for (var entry : new TreeMap<>(NuclearTestCases.referenceCases()).entrySet()) {
            var v = entry.getValue();
            receipts.append(entry.getKey()).append('\n').append(OverlapMatrix.compute(v.functions()).receipt())
                    .append('\n').append(KineticMatrix.compute(v.functions()).receipt()).append('\n')
                    .append(v.receipt()).append('\n');
        }
        // Captured from the pre-Milestone-3.5 Java 21 implementation: 15 cases, 45 full receipts.
        assertEquals("e6210ed6aabf8e5458e34937c27174ff481e65df99797d57f1b255a76655bf4e",
                ContentHash.sha256(receipts.toString()));
    }

    @Test
    void independentFreshLibcintCoreReferences() throws IOException {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("reference/core.csv")) {
            assertNotNull(input); bytes = input.readAllBytes();
        }
        assertEquals("55df4f3edd42dfbb035971072e52686e109c69760e8b7efecb1f9e8a7780e0f0", ContentHash.sha256(bytes));
        var lines = new String(bytes, StandardCharsets.UTF_8).lines().toList();
        assertEquals("case,row,column,core_hartree", lines.getFirst());
        var nuclearCases = NuclearTestCases.referenceCases();
        var matrices = new TreeMap<String, CoreHamiltonianMatrix>();
        for (var entry : KineticTestCases.referenceCases().entrySet()) {
            var v = nuclearCases.get(entry.getKey());
            var calculator = new CoreHamiltonianCalculator(new QuantumSystem(v.nuclei(), 0, v.nuclei().size() % 2 + 1), v.functions());
            matrices.put(entry.getKey(), calculator.calculate(calculator.bind(entry.getValue()), calculator.bind(v)));
        }
        double maxError = 0;
        var seen = new HashSet<String>();
        for (String line : lines.subList(1, lines.size())) {
            var columns = line.split(",");
            assertTrue(seen.add(String.join(",", columns[0], columns[1], columns[2])));
            var h = matrices.get(columns[0]); assertNotNull(h);
            double actual = h.get(Integer.parseInt(columns[1]), Integer.parseInt(columns[2]));
            double expected = Double.parseDouble(columns[3]);
            maxError = StrictMath.max(maxError, StrictMath.abs(actual - expected));
            assertEquals(expected, actual, TOLERANCE, line);
        }
        assertEquals(22, seen.size());
        assertEquals(matrices.values().stream().mapToInt(m -> m.size() * m.size()).sum(), seen.size());
        var h = matrices.get("sto3g_h2");
        System.out.println("AETHER_CORE_EXTERNAL_REFERENCE_ENTRIES=" + seen.size() + " MAX_REFERENCE_ERROR=" + maxError);
        System.out.println("H2_HCORE_MATRIX_1_4_BOHR=[[" + h.get(0, 0) + ", " + h.get(0, 1)
                + "], [" + h.get(1, 0) + ", " + h.get(1, 1) + "]] hartree");
    }

    @Test
    void separateJava21ReceiptsAreByteIdentical(@TempDir Path directory) throws IOException, InterruptedException {
        byte[] first = replay(directory, "en", "US");
        byte[] second = replay(directory, "tr", "TR");
        assertArrayEquals(first, second);
        assertArrayEquals(CoreReceiptReplay.h2Matrix().receipt().toString().getBytes(StandardCharsets.UTF_8), first);
        assertEquals("b3642642d3bd49082cf4d280bd005ed2d488c88ce58a5c0a85d6cb2c29c15b33", ContentHash.sha256(first));
        System.out.println("AETHER_CORE_SEPARATE_JVM_RECEIPT=PASS; UTF8_SHA256=" + ContentHash.sha256(first));
        System.out.println("AETHER_CORE_RECEIPT_HASH=" + CoreReceiptReplay.h2Matrix().receipt().receiptHash());
    }

    private static byte[] replay(Path directory, String language, String country) throws IOException, InterruptedException {
        Path output = directory.resolve(language + ".receipt");
        Path error = directory.resolve(language + ".stderr");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Duser.language=" + language, "-Duser.country=" + country, "-cp", classpath,
                CoreReceiptReplay.class.getName()).redirectOutput(output.toFile()).redirectError(error.toFile()).start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Separate JVM timed out");
            assertEquals(0, process.exitValue(), Files.readString(error));
            return Files.readAllBytes(output);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
