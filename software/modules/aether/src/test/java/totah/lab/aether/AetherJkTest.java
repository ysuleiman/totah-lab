package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.aether.matrix.DensityMatrix;
import totah.lab.aether.matrix.ElectronRepulsionCalculator;
import totah.lab.aether.matrix.JkCalculator;
import totah.lab.aether.matrix.OverlapMatrix;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.gaia.geometry.Point3D;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.aether.JkTestCases.*;
import static totah.lab.aether.KineticTestCases.*;
import static totah.lab.aether.NuclearTestCases.h2Nuclei;
import static totah.lab.aether.NuclearTestCases.hydrogen;

class AetherJkTest {
    private static final double TOLERANCE = 5e-13;

    @Test
    void zeroDensityGivesExactlyZeroJAndKWithoutSkippingContributions() throws IOException {
        for (var name : List.of("h2_zero", "h4_arbitrary")) {
            var source = densities().get(name);
            var zero = DensityMatrix.fromRowMajor(source.system(), source.functions(), Collections.nCopies(source.size() * source.size(), 0.0));
            var jk = calculate(zero);
            for (int i = 0; i < zero.size(); i++) for (int j = 0; j < zero.size(); j++) {
                assertEquals(0.0, jk.coulomb().get(i,j));
                assertEquals(0.0, jk.exchange().get(i,j));
            }
            long terms = (long) zero.size() * (zero.size() + 1) / 2 * zero.size() * zero.size();
            assertEquals(terms, jk.performanceCounters().jAccumulations());
            assertEquals(terms, jk.performanceCounters().kAccumulations());
            assertEquals(2 * terms, jk.performanceCounters().eriSlotsRead());
        }
    }

    @Test
    void suppliedArbitrarySymmetricDensitiesProduceSymmetricTypedMatrices() throws IOException {
        for (var density : densities().values()) {
            var jk = calculate(density);
            assertSame(jk.receipt(), jk.coulomb().receipt());
            assertSame(jk.receipt(), jk.exchange().receipt());
            assertSame(density.system(), jk.coulomb().system());
            assertSame(density.system(), jk.exchange().system());
            assertEquals(density.functions(), jk.coulomb().functions());
            assertEquals(density.functions(), jk.exchange().functions());
            assertEquals(ScientificStatus.SCREENING_ONLY, density.status());
            assertEquals(ScientificStatus.SCREENING_ONLY, jk.receipt().status());
            for (int i = 0; i < density.size(); i++) for (int j = 0; j < density.size(); j++) {
                assertEquals(jk.coulomb().get(i,j), jk.coulomb().get(j,i));
                assertEquals(jk.exchange().get(i,j), jk.exchange().get(j,i));
            }
        }
    }

    @Test
    void offDiagonalDensityIncludesBothOrderedTermsWithNoExtraDensityFactor() throws IOException {
        var density = densities().get("h2_off_diagonal");
        var eri = new ElectronRepulsionCalculator(density.system(), density.functions()).calculate();
        var jk = JkCalculator.calculate(density, eri);
        for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) {
            assertEquals(1.5 * eri.get(i,j,0,1), jk.coulomb().get(i,j), TOLERANCE);
            assertEquals(0.75 * (eri.get(i,0,j,1) + eri.get(i,1,j,0)), jk.exchange().get(i,j), TOLERANCE);
        }
        assertNotEquals(jk.coulomb().get(0,1), jk.exchange().get(0,1));
    }

    @Test
    void suppliedH2RdmHasTwoElectronsAndFrozenDoubledConvention() throws IOException {
        var density = densities().get("h2_rhf");
        var overlap = OverlapMatrix.compute(density.functions());
        double electrons = 0;
        for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) electrons += density.get(i,j) * overlap.get(j,i);
        assertEquals(2, electrons, TOLERANCE); // Fixture-only validation, not a production density constraint.
        assertEquals("P_mu_nu=2*sum_occupied(C_mu_i*C_nu_i);real;symmetric;spin-summed", DensityMatrix.CONVENTION);
        assertEquals("F=Hcore+J-0.5*K", JkCalculator.FUTURE_FOCK_CONVENTION);
        assertTrue(calculate(density).receipt().protocol().contains(DensityMatrix.CONVENTION));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.25, 2, -3})
    void linearScalingForSeveralSuppliedDensities(double scale) throws IOException {
        var sources = densities();
        for (String name : List.of("h2_arbitrary", "h2_indefinite", "h2_rhf", "h4_arbitrary")) {
            var p = sources.get(name);
            var scaled = DensityMatrix.fromRowMajor(p.system(), p.functions(), rowMajor(p).stream().map(v -> scale * v).toList());
            var eri = new ElectronRepulsionCalculator(p.system(), p.functions()).calculate();
            var original = JkCalculator.calculate(p, eri);
            var result = JkCalculator.calculate(scaled, eri);
            for (int i = 0; i < p.size(); i++) for (int j = 0; j < p.size(); j++) {
                assertEquals(scale * original.coulomb().get(i,j), result.coulomb().get(i,j), TOLERANCE);
                assertEquals(scale * original.exchange().get(i,j), result.exchange().get(i,j), TOLERANCE);
            }
        }
    }

    @Test
    void additionOfSuppliedDensitiesIsLinear() throws IOException {
        var p = densities().get("h4_arbitrary");
        var q = densities().get("h4_off_diagonal");
        var sum = new ArrayList<Double>();
        for (int i = 0; i < p.size(); i++) for (int j = 0; j < p.size(); j++) sum.add(p.get(i,j) + q.get(i,j));
        var a = calculate(p); var b = calculate(q);
        var combined = calculate(DensityMatrix.fromRowMajor(p.system(), p.functions(), sum));
        for (int i = 0; i < p.size(); i++) for (int j = 0; j < p.size(); j++) {
            assertEquals(a.coulomb().get(i,j) + b.coulomb().get(i,j), combined.coulomb().get(i,j), TOLERANCE);
            assertEquals(a.exchange().get(i,j) + b.exchange().get(i,j), combined.exchange().get(i,j), TOLERANCE);
        }
    }

    @Test
    void basisPermutationCovarianceRemapsDensityAndPackedEriTogether() throws IOException {
        var density = densities().get("h4_arbitrary");
        int[] old = {2,0,3,1};
        var b = density.functions();
        var basis = List.of(b.get(2), b.get(0), b.get(3), b.get(1));
        var entries = new ArrayList<Double>();
        for (int i : old) for (int j : old) entries.add(density.get(i,j));
        var reordered = DensityMatrix.fromRowMajor(density.system(), basis, entries);
        var original = calculate(density);
        var remapped = calculate(reordered);
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
            assertEquals(original.coulomb().get(old[i],old[j]), remapped.coulomb().get(i,j), TOLERANCE);
            assertEquals(original.exchange().get(old[i],old[j]), remapped.exchange().get(i,j), TOLERANCE);
        }
        assertEquals(original.receipt().systemHash(), remapped.receipt().systemHash());
        assertNotEquals(original.receipt().basisGeometryHash(), remapped.receipt().basisGeometryHash());
        assertNotEquals(original.receipt().densityHash(), remapped.receipt().densityHash());
    }

    @Test
    void packedEriContractionMatchesIndependentFullTensorInValidationOnly() throws IOException {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("reference/eri.csv")) {
            assertNotNull(input); bytes = input.readAllBytes();
        }
        assertEquals("1abfe76bd096a5020fc3c37232683077f4732884cfb9a8da02dffbd0be515960", ContentHash.sha256(bytes));
        // A full tensor is permitted only here, as an independent validation oracle.
        double[][][][] reference = new double[4][4][4][4];
        var seen = new HashSet<String>();
        for (var line : new String(bytes, StandardCharsets.UTF_8).lines().skip(1).toList()) {
            String[] c = line.split(",");
            if (!c[0].equals("four_distinct")) continue;
            assertTrue(seen.add(String.join(",", c[1],c[2],c[3],c[4])));
            reference[Integer.parseInt(c[1])][Integer.parseInt(c[2])][Integer.parseInt(c[3])][Integer.parseInt(c[4])] = Double.parseDouble(c[5]);
        }
        assertEquals(256, seen.size());
        var eri = EriTestCases.calculate(EriTestCases.fourDistinct());
        var source = densities().get("h4_arbitrary");
        var p = DensityMatrix.fromRowMajor(eri.system(), eri.functions(), rowMajor(source));
        var jk = JkCalculator.calculate(p, eri);
        assertEquals(55, eri.uniqueQuartetCount());
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
            double expectedJ = 0; double expectedK = 0;
            for (int l = 0; l < 4; l++) for (int s = 0; s < 4; s++) {
                expectedJ += p.get(l,s) * reference[i][j][l][s];
                expectedK += p.get(l,s) * reference[i][l][j][s];
            }
            assertEquals(expectedJ, jk.coulomb().get(i,j), TOLERANCE);
            assertEquals(expectedK, jk.exchange().get(i,j), TOLERANCE);
        }
    }

    @Test
    void sameSizeSystemGeometryBasisAndOrderingMismatchesFailClosed() throws IOException {
        var p = densities().get("h2_arbitrary");
        var eri = new ElectronRepulsionCalculator(p.system(), p.functions()).calculate();
        var movedSystem = new QuantumSystem(List.of(hydrogen(ORIGIN), hydrogen(new Point3D(1.5,0,0))), 0, 1);
        for (var system : List.of(movedSystem, new QuantumSystem(h2Nuclei(), 2, 1), new QuantumSystem(h2Nuclei().reversed(), 0, 1))) {
            var changed = DensityMatrix.fromRowMajor(system, p.functions(), rowMajor(p));
            assertThrows(IllegalArgumentException.class, () -> JkCalculator.calculate(changed, eri));
        }
        var h = totah.lab.aether.basis.Sto3gHydrogen.load();
        for (var basis : List.of(p.functions().reversed(), List.of(single(1, ORIGIN), p.functions().get(1)),
                List.of(p.functions().getFirst(), h.atBohr(new Point3D(1.5,0,0))))) {
            var changed = DensityMatrix.fromRowMajor(p.system(), basis, rowMajor(p));
            assertThrows(IllegalArgumentException.class, () -> JkCalculator.calculate(changed, eri));
        }
    }

    @Test
    void rejectsInvalidDensityWithoutImplicitSymmetrization() throws IOException {
        var system = new QuantumSystem(h2Nuclei(), 0, 1);
        var basis = h2();
        assertThrows(IllegalArgumentException.class, () -> DensityMatrix.fromRowMajor(system, basis, List.of(1.0)));
        assertThrows(IllegalArgumentException.class, () -> DensityMatrix.fromRowMajor(system, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> DensityMatrix.fromRowMajor(system, basis, List.of(1.,0.2,0.3,1.)));
        assertThrows(IllegalArgumentException.class, () -> DensityMatrix.fromRowMajor(system, basis, List.of(1.,0.2,Math.nextUp(0.2),1.)));
        for (double value : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> DensityMatrix.fromRowMajor(system, basis, List.of(value,0.,0.,1.)));
        }
        assertThrows(NullPointerException.class, () -> DensityMatrix.fromRowMajor(null, basis, List.of(1.,0.,0.,1.)));
        assertThrows(NullPointerException.class, () -> DensityMatrix.fromRowMajor(system, basis, java.util.Arrays.asList(1.,null,0.,1.)));
        assertThrows(IllegalArgumentException.class, () -> DensityMatrix.fromRowMajor(new QuantumSystem(h2Nuclei(), 0, 3), basis, List.of(1.,0.,0.,1.)));
        assertThrows(NullPointerException.class, () -> JkCalculator.calculate(null, EriTestCases.calculate(basis)));
        assertThrows(NullPointerException.class, () -> JkCalculator.calculate(densities().get("h2_zero"), null));
    }

    @Test
    void numericalOverflowReturnsNoJkResult() throws IOException {
        var source = densities().get("h2_zero");
        var p = DensityMatrix.fromRowMajor(source.system(), source.functions(), Collections.nCopies(4, Double.MAX_VALUE));
        var failure = assertThrows(ArithmeticException.class, () -> calculate(p));
        assertTrue(failure.getMessage().contains("NUMERICAL_FAILURE"));
    }

    @Test
    void densityAndResultsAreImmutableAndHashesIncludeEveryDensityEntry() throws IOException {
        var source = densities().get("h2_arbitrary");
        var entries = new ArrayList<>(rowMajor(source));
        var basis = new ArrayList<>(source.functions());
        var p = DensityMatrix.fromRowMajor(source.system(), basis, entries);
        entries.clear(); basis.clear();
        assertEquals(source.densityHash(), p.densityHash());
        assertThrows(UnsupportedOperationException.class, () -> p.functions().clear());
        var jk = calculate(p);
        assertThrows(UnsupportedOperationException.class, () -> jk.coulomb().functions().clear());
        assertThrows(UnsupportedOperationException.class, () -> jk.exchange().functions().clear());
        assertThrows(IndexOutOfBoundsException.class, () -> p.get(2,0));
        assertThrows(IndexOutOfBoundsException.class, () -> jk.coulomb().get(-1,0));
        assertThrows(IndexOutOfBoundsException.class, () -> jk.exchange().get(0,2));
        var changed = rowMajor(p); changed.set(0, changed.getFirst() + 0.1);
        var q = DensityMatrix.fromRowMajor(p.system(), p.functions(), changed);
        var other = calculate(q);
        assertNotEquals(p.densityHash(), q.densityHash());
        assertNotEquals(jk.receipt().calculationHash(), other.receipt().calculationHash());
        assertNotEquals(jk.receipt().receiptHash(), other.receipt().receiptHash());
        assertEquals(jk.receipt().eriReceipt(), other.receipt().eriReceipt());
    }

    @Test
    void deterministicReplayAndSignedZeroCanonicalIdentity() throws IOException {
        var source = densities().get("h2_rhf");
        var eri = new ElectronRepulsionCalculator(source.system(), source.functions()).calculate();
        var first = JkCalculator.calculate(source, eri);
        for (int i = 0; i < 20; i++) assertEquals(first.receipt(), JkCalculator.calculate(source, eri).receipt());
        assertEquals("8bcab5ba1c2217de38b4d7fb2a048daf7e95cff58a23268e7c27c1e0ef0e2bad", first.receipt().receiptHash());
        assertSame(eri.receipt(), first.receipt().eriReceipt());
        assertEquals(source.densityHash(), first.receipt().densityHash());
        assertFalse(first.receipt().toString().contains("elapsed"));
        var zero = DensityMatrix.fromRowMajor(source.system(), source.functions(), Collections.nCopies(4, 0.0));
        var signedZero = DensityMatrix.fromRowMajor(source.system(), source.functions(), Collections.nCopies(4, -0.0));
        assertEquals(zero.densityHash(), signedZero.densityHash());
        assertEquals(calculate(zero).receipt(), calculate(signedZero).receipt());
    }

    @Test
    void performanceCountersCountEveryPackedReadAndAccumulation() throws IOException {
        for (String name : List.of("h2_rhf", "h4_arbitrary")) {
            var density = densities().get(name);
            var jk = calculate(density);
            var counts = jk.performanceCounters();
            long expected = (long) density.size() * (density.size() + 1) / 2 * density.size() * density.size();
            assertEquals(density.size(), counts.densityDimension());
            assertEquals(expected, counts.jAccumulations());
            assertEquals(expected, counts.kAccumulations());
            assertEquals(2 * expected, counts.eriSlotsRead());
            assertTrue(counts.elapsedNanos() >= 0);
            jk.coulomb().get(0,0); jk.exchange().get(0,0);
            assertSame(counts, jk.performanceCounters());
            System.out.println("AETHER_JK_PERFORMANCE_" + name + "=" + counts);
        }
    }

    @Test
    void allPriorEriReceiptBytesRemainFrozen() throws IOException {
        var receipts = new StringBuilder();
        EriTestCases.referenceCases().forEach((name, eri) -> receipts.append(name).append('\n').append(eri.receipt()).append('\n'));
        assertEquals("7939958507235754adf0fdc87ad545f2a7e9fb2e0c3703473f4d08b85396db23", ContentHash.sha256(receipts.toString()));
        // The unchanged AetherElectronRepulsionTest separately pins all 59 S/T/V/Hcore receipts.
    }

    @Test
    void independentPyScfJkReferencesCompareEveryMatrixElement() throws IOException {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("reference/jk.csv")) {
            assertNotNull(input); bytes = input.readAllBytes();
        }
        assertEquals("7e83483af1ebcdcac9c971108a48e098145797a0784e85839409c2f423c19587", ContentHash.sha256(bytes));
        var sources = densities();
        var results = new TreeMap<String, JkCalculator.Result>();
        sources.forEach((name, p) -> results.put(name, calculate(p)));
        var lines = new String(bytes, StandardCharsets.UTF_8).lines().toList();
        assertEquals("case,row,column,coulomb_hartree,exchange_hartree", lines.getFirst());
        double maxJ = 0; double maxK = 0;
        var seen = new HashSet<String>();
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            assertTrue(seen.add(String.join(",", c[0],c[1],c[2])));
            var jk = results.get(c[0]); assertNotNull(jk);
            int i = Integer.parseInt(c[1]); int j = Integer.parseInt(c[2]);
            double expectedJ = Double.parseDouble(c[3]); double expectedK = Double.parseDouble(c[4]);
            double actualJ = jk.coulomb().get(i,j); double actualK = jk.exchange().get(i,j);
            maxJ = StrictMath.max(maxJ, StrictMath.abs(expectedJ - actualJ));
            maxK = StrictMath.max(maxK, StrictMath.abs(expectedK - actualK));
            assertEquals(expectedJ, actualJ, TOLERANCE, line);
            assertEquals(expectedK, actualK, TOLERANCE, line);
        }
        assertEquals(8, sources.size());
        assertEquals(56, seen.size());
        assertEquals(sources.values().stream().mapToInt(p -> p.size() * p.size()).sum(), seen.size());
        var p = sources.get("h2_rhf"); var jk = results.get("h2_rhf");
        System.out.println("AETHER_JK_EXTERNAL_REFERENCE_ENTRIES=" + 2 * seen.size() + " MAX_J_REFERENCE_ERROR=" + maxJ + " MAX_K_REFERENCE_ERROR=" + maxK);
        System.out.println("H2_P_MATRIX_1_4_BOHR=[[" + p.get(0,0) + ", " + p.get(0,1) + "], [" + p.get(1,0) + ", " + p.get(1,1) + "]]");
        System.out.println("H2_J_MATRIX_1_4_BOHR=[[" + jk.coulomb().get(0,0) + ", " + jk.coulomb().get(0,1) + "], [" + jk.coulomb().get(1,0) + ", " + jk.coulomb().get(1,1) + "]] hartree");
        System.out.println("H2_K_MATRIX_1_4_BOHR=[[" + jk.exchange().get(0,0) + ", " + jk.exchange().get(0,1) + "], [" + jk.exchange().get(1,0) + ", " + jk.exchange().get(1,1) + "]] hartree");
    }

    @Test
    void separateJava21ReceiptsAreByteIdentical(@TempDir Path directory) throws IOException, InterruptedException {
        byte[] first = replay(directory, "en", "US");
        byte[] second = replay(directory, "tr", "TR");
        assertArrayEquals(first, second);
        assertArrayEquals(calculate(densities().get("h2_rhf")).receipt().toString().getBytes(StandardCharsets.UTF_8), first);
        assertEquals("7aacc337e544631a1c9497ec728f0a713dbf774c907dca2e7213424cf4749394", ContentHash.sha256(first));
        System.out.println("AETHER_JK_SEPARATE_JVM_RECEIPT=PASS; UTF8_SHA256=" + ContentHash.sha256(first));
        System.out.println("AETHER_JK_RECEIPT_HASH=" + calculate(densities().get("h2_rhf")).receipt().receiptHash());
    }

    private static byte[] replay(Path directory, String language, String country) throws IOException, InterruptedException {
        Path output = directory.resolve(language + ".receipt");
        Path error = directory.resolve(language + ".stderr");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Duser.language=" + language, "-Duser.country=" + country, "-cp", classpath,
                JkReceiptReplay.class.getName()).redirectOutput(output.toFile()).redirectError(error.toFile()).start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Separate JVM timed out");
            assertEquals(0, process.exitValue(), Files.readString(error));
            return Files.readAllBytes(output);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
