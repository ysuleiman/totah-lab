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
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.GaussianTerm;
import totah.lab.aether.basis.PrimitiveGaussian;
import totah.lab.aether.basis.Sto3gHydrogen;
import totah.lab.aether.matrix.CoreHamiltonianCalculator;
import totah.lab.aether.matrix.DensityMatrix;
import totah.lab.aether.matrix.OneShotRhfCalculator;
import totah.lab.aether.matrix.OneShotRhfResult;
import totah.lab.aether.matrix.OverlapMatrix;
import totah.lab.aether.model.NuclearCenter;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.gaia.geometry.Point3D;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.aether.JkTestCases.densities;
import static totah.lab.aether.JkTestCases.rowMajor;
import static totah.lab.aether.OneShotReceiptReplay.solve;

class AetherOneShotRhfTest {
    private static final double TOLERANCE = 1e-10;

    @Test
    void fockAssemblyAndEveryEvidenceTypePreserveSuppliedStateProvenance() throws IOException {
        for (var p : densities().values()) {
            var s = OverlapMatrix.compute(p.functions());
            var core = new CoreHamiltonianCalculator(p.system(), p.functions()).calculate();
            var jk = JkTestCases.calculate(p);
            var result = OneShotRhfCalculator.solve(p,s,core,jk);
            assertSame(p, result.density());
            assertSame(s.receipt(), result.receipt().overlapReceipt());
            assertSame(core.receipt(), result.receipt().coreReceipt());
            assertSame(jk.receipt(), result.receipt().jkReceipt());
            assertEquals(p.densityHash(), result.receipt().densityHash());
            assertEquals(ScientificStatus.SCREENING_ONLY, result.receipt().status());
            for (var receipt : List.of(result.fock().receipt(), result.overlapEigenvalues().receipt(),
                    result.orthogonalization().receipt(), result.orthogonalFock().receipt(), result.coefficients().receipt(), result.energies().receipt())) {
                assertSame(result.receipt(), receipt);
            }
            for (int i = 0; i < p.size(); i++) for (int j = 0; j < p.size(); j++) {
                assertEquals((core.get(i,j) + jk.coulomb().get(i,j)) - 0.5 * jk.exchange().get(i,j), result.fock().get(i,j));
            }
        }
    }

    @Test
    void explicitIntermediateIdentitiesAndGeneralizedResiduals() throws IOException {
        double maxResidual = 0, maxNormalization = 0, maxXError = 0;
        for (var p : densities().values()) {
            var r = solve(p); var s = OverlapMatrix.compute(p.functions()); int n = p.size();
            for (int i = 0; i < n; i++) {
                if (i > 0) {
                    assertTrue(r.overlapEigenvalues().get(i) >= r.overlapEigenvalues().get(i-1));
                    assertTrue(r.energies().get(i) >= r.energies().get(i-1));
                }
                for (int j = 0; j < n; j++) {
                    double xsx = 0, xfx = 0, csc = 0, fc = 0, sce = 0;
                    for (int a = 0; a < n; a++) {
                        fc += r.fock().get(i,a) * r.coefficients().get(a,j);
                        sce += s.get(i,a) * r.coefficients().get(a,j) * r.energies().get(j);
                        for (int b = 0; b < n; b++) {
                            xsx += r.orthogonalization().get(a,i) * s.get(a,b) * r.orthogonalization().get(b,j);
                            xfx += r.orthogonalization().get(a,i) * r.fock().get(a,b) * r.orthogonalization().get(b,j);
                            csc += r.coefficients().get(a,i) * s.get(a,b) * r.coefficients().get(b,j);
                        }
                    }
                    double identity = i == j ? 1 : 0;
                    assertEquals(identity, xsx, TOLERANCE);
                    assertEquals(xfx, r.orthogonalFock().get(i,j), TOLERANCE);
                    assertEquals(identity, csc, TOLERANCE);
                    assertEquals(fc, sce, TOLERANCE);
                    assertEquals(r.orthogonalization().get(i,j), r.orthogonalization().get(j,i));
                    maxResidual = StrictMath.max(maxResidual, StrictMath.abs(fc-sce));
                    maxNormalization = StrictMath.max(maxNormalization, StrictMath.abs(csc-identity));
                    maxXError = StrictMath.max(maxXError, StrictMath.abs(xsx-identity));
                }
            }
            assertTrue(r.diagnostics().maxGeneralizedEigenResidual() <= TOLERANCE);
            assertTrue(r.diagnostics().maxOrthonormalityError() <= TOLERANCE);
        }
        System.out.println("AETHER_ONE_SHOT_MAX_GENERALIZED_EIGEN_RESIDUAL=" + maxResidual
                + " MAX_ORTHONORMALITY_ERROR=" + maxNormalization + " MAX_XTSX_ERROR=" + maxXError);
    }

    @Test
    void basisPermutationCovarianceWithCoefficientColumnSigns() throws IOException {
        var p = densities().get("h4_arbitrary"); var original = solve(p);
        int[] old = {2,0,3,1}; var basis = p.functions();
        var reordered = List.of(basis.get(2),basis.get(0),basis.get(3),basis.get(1));
        var entries = new ArrayList<Double>();
        for (int i : old) for (int j : old) entries.add(p.get(i,j));
        var result = solve(DensityMatrix.fromRowMajor(p.system(), reordered, entries));
        for (int column = 0; column < 4; column++) {
            assertEquals(original.energies().get(column), result.energies().get(column), TOLERANCE);
            double dot = 0;
            for (int row = 0; row < 4; row++) dot += original.coefficients().get(old[row],column) * result.coefficients().get(row,column);
            double sign = dot < 0 ? -1 : 1;
            for (int row = 0; row < 4; row++) assertEquals(sign * original.coefficients().get(old[row],column), result.coefficients().get(row,column), TOLERANCE);
        }
        for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
            assertEquals(original.fock().get(old[i],old[j]), result.fock().get(i,j), TOLERANCE);
            assertEquals(original.orthogonalization().get(old[i],old[j]), result.orthogonalization().get(i,j), TOLERANCE);
            assertEquals(original.orthogonalFock().get(old[i],old[j]), result.orthogonalFock().get(i,j), TOLERANCE);
        }
        assertNotEquals(original.receipt().basisGeometryHash(), result.receipt().basisGeometryHash());
    }

    @Test
    void translationInvarianceOfAllOrbitalEnergies() throws IOException {
        checkTransform(p -> new Point3D(p.x()+2.25, p.y()-3.5, p.z()+0.75));
    }

    @Test
    void rotationInvarianceOfAllOrbitalEnergies() throws IOException {
        checkTransform(p -> new Point3D(p.z(),0.6*p.x()-0.8*p.y(),0.8*p.x()+0.6*p.y()));
    }

    private static void checkTransform(UnaryOperator<Point3D> transform) throws IOException {
        var p = densities().get("h4_arbitrary"); var original = solve(p);
        var basis = p.functions().stream().map(c -> new ContractedGaussian(c.terms().stream().map(t ->
                new GaussianTerm(new PrimitiveGaussian(transform.apply(t.primitive().centerBohr()), t.primitive().exponent()), t.coefficient())).toList())).toList();
        var nuclei = p.system().nuclei().stream().map(n -> new NuclearCenter(transform.apply(n.centerBohr()),n.charge())).toList();
        var moved = solve(DensityMatrix.fromRowMajor(new QuantumSystem(nuclei,0,1), basis, rowMajor(p)));
        for (int i = 0; i < p.size(); i++) assertEquals(original.energies().get(i), moved.energies().get(i), TOLERANCE);
        assertNotEquals(original.receipt().systemHash(), moved.receipt().systemHash());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 1e-6})
    void rankDeficientPhysicalOverlapFailsClosedWithoutRegularization(double distance) throws IOException {
        var h = Sto3gHydrogen.load();
        var points = List.of(new Point3D(0,0,0),new Point3D(distance,0,0));
        var basis = points.stream().map(h::atBohr).toList();
        var system = new QuantumSystem(points.stream().map(NuclearTestCases::hydrogen).toList(),0,1);
        var p = DensityMatrix.fromRowMajor(system,basis,Collections.nCopies(4,0.0));
        var failure = assertThrows(ArithmeticException.class, () -> solve(p));
        assertTrue(failure.getMessage().contains("rank deficient"));
    }

    @Test
    void incompatibleSystemBasisOrderAndDensityFailClosed() throws IOException {
        var p = densities().get("h2_rhf");
        var s = OverlapMatrix.compute(p.functions());
        var core = new CoreHamiltonianCalculator(p.system(),p.functions()).calculate();
        var jk = JkTestCases.calculate(p);
        var otherP = densities().get("h2_arbitrary");
        assertThrows(IllegalArgumentException.class, () -> OneShotRhfCalculator.solve(otherP,s,core,jk));
        assertThrows(IllegalArgumentException.class, () -> OneShotRhfCalculator.solve(p,s,core,JkTestCases.calculate(otherP)));
        var reversed = p.functions().reversed();
        assertThrows(IllegalArgumentException.class, () -> OneShotRhfCalculator.solve(p,OverlapMatrix.compute(reversed),core,jk));
        assertThrows(IllegalArgumentException.class, () -> OneShotRhfCalculator.solve(p,s,new CoreHamiltonianCalculator(p.system(),reversed).calculate(),jk));
        var otherSystem = new QuantumSystem(p.system().nuclei(),2,1);
        assertThrows(IllegalArgumentException.class, () -> OneShotRhfCalculator.solve(p,s,new CoreHamiltonianCalculator(otherSystem,p.functions()).calculate(),jk));
        assertThrows(NullPointerException.class, () -> OneShotRhfCalculator.solve(null,s,core,jk));
    }

    @Test
    void deterministicSignsReceiptsAndUnchangedInput() throws IOException {
        for (var p : densities().values()) {
            var first = solve(p);
            assertEquals(first.receipt(), solve(p).receipt());
            assertEquals(p.densityHash(), first.density().densityHash());
            for (int col = 0; col < p.size(); col++) {
                int pivot = 0;
                for (int row = 1; row < p.size(); row++) {
                    if (StrictMath.abs(first.coefficients().get(row,col)) > StrictMath.abs(first.coefficients().get(pivot,col))) pivot = row;
                }
                assertTrue(first.coefficients().get(pivot,col) > 0);
            }
            assertFalse(first.receipt().toString().contains("Nanos="));
            assertThrows(UnsupportedOperationException.class, () -> first.density().functions().clear());
            assertThrows(IndexOutOfBoundsException.class, () -> first.energies().get(p.size()));
        }
    }

    @Test
    void backendPerformanceCountersExcludePriorIntegralGeneration() throws IOException {
        for (String name : List.of("h2_rhf","h4_arbitrary")) {
            var result = solve(densities().get(name)); var c = result.performanceCounters();
            assertEquals(result.density().size(),c.matrixDimension());
            assertTrue(c.overlapEigendecompositionNanos() >= 0 && c.fockTransformNanos() >= 0 && c.fockEigendecompositionNanos() >= 0);
            assertTrue(c.totalOneShotNanos() >= c.overlapEigendecompositionNanos()+c.fockTransformNanos()+c.fockEigendecompositionNanos());
            System.out.println("AETHER_ONE_SHOT_PERFORMANCE_" + name + "=" + c);
        }
    }

    @Test
    void allExistingJkReferenceReceiptsRemainByteIdentical() throws IOException {
        var text = new StringBuilder();
        densities().forEach((name,p) -> text.append(name).append('\n').append(JkTestCases.calculate(p).receipt()).append('\n'));
        assertEquals("c712afcb6f2ff2e19fa3aff945fc067c57434545e010fedac6125c471758bde2",ContentHash.sha256(text.toString()));
        // Unchanged prior tests also freeze all 69 S/T/V/Hcore/ERI receipts.
    }

    @Test
    void freshPyScfReferencesIncludeEveryIntermediateAndSignEquivalentCoefficient() throws IOException {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("reference/one-shot.csv")) { assertNotNull(input); bytes = input.readAllBytes(); }
        assertEquals("e27f45a43af05abd46a2cf2ab1242ac28daa95f2175ac4b0c16aaacebbe19712",ContentHash.sha256(bytes));
        var results = new TreeMap<String,OneShotRhfResult>();
        densities().forEach((name,p) -> results.put(name,solve(p)));
        var lines = new String(bytes,StandardCharsets.UTF_8).lines().toList();
        assertEquals("case,evidence,row,column,value",lines.getFirst());
        var references = new TreeMap<String,Double>();
        var seen = new HashSet<String>();
        for (String line : lines.subList(1,lines.size())) {
            String[] c = line.split(","); String key = String.join(",",c[0],c[1],c[2],c[3]);
            assertTrue(seen.add(key)); references.put(key,Double.parseDouble(c[4]));
        }
        var errors = new TreeMap<String,Double>();
        for (String line : lines.subList(1,lines.size())) {
            String[] c = line.split(","); var r = results.get(c[0]); assertNotNull(r);
            int i = Integer.parseInt(c[2]), j = Integer.parseInt(c[3]); double expected = Double.parseDouble(c[4]);
            double actual = switch (c[1]) {
                case "fock" -> r.fock().get(i,j);
                case "overlap_eigenvalues" -> r.overlapEigenvalues().get(i);
                case "orthogonalization" -> r.orthogonalization().get(i,j);
                case "orthogonal_fock" -> r.orthogonalFock().get(i,j);
                case "orbital_energies" -> r.energies().get(i);
                case "coefficients" -> r.coefficients().get(i,j);
                default -> throw new AssertionError("Unknown evidence");
            };
            if (c[1].equals("coefficients")) {
                double dot = 0;
                for (int row = 0; row < r.density().size(); row++) dot += r.coefficients().get(row,j) * references.get(c[0]+",coefficients,"+row+","+j);
                if (dot < 0) expected = -expected;
            }
            errors.merge(c[1],StrictMath.abs(expected-actual),StrictMath::max);
            assertEquals(expected,actual,TOLERANCE,line);
        }
        assertEquals(264,seen.size());
        assertEquals(results.values().stream().mapToInt(r -> 4*r.density().size()*r.density().size()+2*r.density().size()).sum(),seen.size());
        System.out.println("AETHER_ONE_SHOT_EXTERNAL_REFERENCE_ENTRIES="+seen.size()+" MAX_ERRORS="+errors);
        var h = results.get("h2_rhf");
        System.out.println("H2_FOCK_MATRIX_1_4_BOHR=[["+h.fock().get(0,0)+", "+h.fock().get(0,1)+"], ["+h.fock().get(1,0)+", "+h.fock().get(1,1)+"]]");
        System.out.println("H2_OVERLAP_EIGENVALUES=["+h.overlapEigenvalues().get(0)+", "+h.overlapEigenvalues().get(1)+"]");
        System.out.println("H2_X=[["+h.orthogonalization().get(0,0)+", "+h.orthogonalization().get(0,1)+"], ["+h.orthogonalization().get(1,0)+", "+h.orthogonalization().get(1,1)+"]]");
        System.out.println("H2_ORBITAL_ENERGIES=["+h.energies().get(0)+", "+h.energies().get(1)+"]");
        System.out.println("H2_C=[["+h.coefficients().get(0,0)+", "+h.coefficients().get(0,1)+"], ["+h.coefficients().get(1,0)+", "+h.coefficients().get(1,1)+"]]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"h2_rhf", "h4_arbitrary"})
    void separateJava21ReceiptsAreByteIdentical(String name, @TempDir Path directory) throws IOException, InterruptedException {
        byte[] first = replay(directory,"en","US",name), second = replay(directory,"tr","TR",name);
        assertArrayEquals(first,second);
        var receipt = solve(densities().get(name)).receipt();
        assertArrayEquals(receipt.toString().getBytes(StandardCharsets.UTF_8),first);
        if (name.equals("h2_rhf")) {
            assertEquals("45f7c48692b251fa2c357e447def17bd6395a8f89197c0f01035d13c88d1bb78", ContentHash.sha256(first));
            assertEquals("3cdce70dd0af2631c18a1b62e72c865589af9a5c2298b8499731f179ed077a1b", receipt.receiptHash());
        }
        System.out.println("AETHER_ONE_SHOT_SEPARATE_JVM_"+name+"=PASS; UTF8_SHA256="+ContentHash.sha256(first));
        System.out.println("AETHER_ONE_SHOT_RECEIPT_HASH_"+name+"="+receipt.receiptHash());
    }

    private static byte[] replay(Path directory,String language,String country,String name) throws IOException,InterruptedException {
        Path output=directory.resolve(language+".receipt"), error=directory.resolve(language+".stderr");
        String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-Duser.language="+language,"-Duser.country="+country,"-cp",cp,OneShotReceiptReplay.class.getName(),name)
                .redirectOutput(output.toFile()).redirectError(error.toFile()).start();
        try {
            assertTrue(process.waitFor(15,TimeUnit.SECONDS)); assertEquals(0,process.exitValue(),Files.readString(error));
            return Files.readAllBytes(output);
        } finally { if(process.isAlive())process.destroyForcibly(); }
    }
}
