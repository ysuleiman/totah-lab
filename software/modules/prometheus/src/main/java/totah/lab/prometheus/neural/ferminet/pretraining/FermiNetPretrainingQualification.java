package totah.lab.prometheus.neural.ferminet.pretraining;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import totah.lab.prometheus.neural.ferminet.runtime.FermiNetStateAccess;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetRuntimeSampling;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;
import totah.lab.prometheus.molecular.LocalEnergyComponents;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** Read-only HF correspondence diagnostics for a frozen pretrained state. */
public final class FermiNetPretrainingQualification {

    public EnergyResult evaluateEnergy(
            FermiNetV1State state,
            List<QuantumCoordinates> initialWalkers,
            FermiNetRuntimeSampling.Request request,
            int parallelism,
            String expectedGeometryIdentity,
            double hfReferenceEnergyHartree) {
        Objects.requireNonNull(expectedGeometryIdentity, "expectedGeometryIdentity");
        String actualGeometryIdentity = geometryIdentity(state.molecule());
        if (!expectedGeometryIdentity.equals(actualGeometryIdentity)) {
            throw new IllegalArgumentException("canonical geometry identity mismatch");
        }
        FermiNetRuntimeSampling.Result sampled = FermiNetRuntimeSampling.sampleParallel(
                state, request, initialWalkers, parallelism);
        double[] energies = sampled.localEnergies().stream()
                .mapToDouble(LocalEnergyComponents::totalHartree).toArray();
        double mean = mean(energies);
        double standardDeviation = sampleStandardDeviation(energies, mean);
        return new EnergyResult(
                mean, standardDeviation / Math.sqrt(energies.length),
                standardDeviation, sampled.acceptance(), energies.length,
                parameterChecksum(state), actualGeometryIdentity,
                hfReferenceEnergyHartree, mean - hfReferenceEnergyHartree);
    }

    public static String geometryIdentity(Molecule molecule) {
        MessageDigest digest = sha256();
        update(digest, molecule.moleculeId());
        update(digest, molecule.charge().elementaryCharges());
        update(digest, molecule.electrons().value());
        update(digest, molecule.spin().alphaElectrons());
        update(digest, molecule.spin().betaElectrons());
        for (var nucleus : molecule.nuclei()) {
            update(digest, nucleus.orderedIndex());
            update(digest, nucleus.element());
            update(digest, nucleus.charge().atomicNumber());
            var position = nucleus.position().inBohr();
            update(digest, Double.doubleToRawLongBits(position.x()));
            update(digest, Double.doubleToRawLongBits(position.y()));
            update(digest, Double.doubleToRawLongBits(position.z()));
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    public static String parameterChecksum(FermiNetV1State state) {
        MessageDigest digest = sha256();
        for (double value : FermiNetStateAccess.parameterSnapshot(state))
            update(digest, Double.doubleToRawLongBits(value));
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    public record EnergyResult(
            double meanEnergyHartree,
            double standardErrorHartree,
            double standardDeviationHartree,
            double acceptance,
            int sampleCount,
            String parameterChecksum,
            String geometryIdentity,
            double hfReferenceEnergyHartree,
            double energyErrorHartree) {}

    public Result evaluate(
            FermiNetV1State state,
            HartreeFockOrbitalTarget target,
            List<QuantumCoordinates> configurations) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(target, "target");
        configurations = List.copyOf(configurations);
        if (configurations.isEmpty()) {
            throw new IllegalArgumentException("qualification panel must not be empty");
        }
        double[] parametersBefore = FermiNetStateAccess.parameterSnapshot(state);
        int alpha = state.molecule().spin().alphaElectrons();
        int beta = state.molecule().spin().betaElectrons();
        int n = alpha + beta;
        List<Sample> samples = new ArrayList<>(configurations.size());
        int determinants = -1;
        double squaredError = 0.0;
        double leakageSquared = 0.0;
        long entryCount = 0L;
        long leakageCount = 0L;
        for (QuantumCoordinates coordinates : configurations) {
            var hf = target.evaluate(coordinates);
            var neural = FermiNetStateAccess.orbitals(state, coordinates);
            if (determinants < 0) determinants = neural.determinants().size();
            if (neural.determinants().size() != determinants) {
                throw new IllegalStateException("determinant count changed across panel");
            }
            for (var determinant : neural.determinants()) {
                double[][] matrix = determinant.orbitalMatrix();
                for (int row = 0; row < n; row++) {
                    for (int column = 0; column < n; column++) {
                        boolean sameSpin = (row < alpha) == (column < alpha);
                        double expected = sameSpin
                                ? (row < alpha
                                ? hf.alpha()[row][column]
                                : hf.beta()[row - alpha][column - alpha])
                                : 0.0;
                        double difference = matrix[row][column] - expected;
                        squaredError += difference * difference;
                        entryCount++;
                        if (!sameSpin) {
                            leakageSquared += matrix[row][column] * matrix[row][column];
                            leakageCount++;
                        }
                    }
                }
            }
            SignedLog alphaDet = signedLogDeterminant(hf.alpha());
            SignedLog betaDet = signedLogDeterminant(hf.beta());
            samples.add(new Sample(hf.alpha(), hf.beta(), neural,
                    alphaDet.sign() * betaDet.sign(), alphaDet.logAbsolute() + betaDet.logAbsolute()));
        }
        List<Subspace> alphaSubspaces = new ArrayList<>(determinants);
        List<Subspace> betaSubspaces = new ArrayList<>(determinants);
        for (int determinant = 0; determinant < determinants; determinant++) {
            alphaSubspaces.add(subspace(samples, true, alpha, determinant));
            betaSubspaces.add(subspace(samples, false, beta, determinant));
        }
        double[] deltas = new double[samples.size()];
        int signAgreement = 0;
        double[][] headRatios = new double[determinants][samples.size()];
        for (int sample = 0; sample < samples.size(); sample++) {
            Sample value = samples.get(sample);
            deltas[sample] = value.neural().logAbsoluteWavefunction() - value.hfLogAbsolute();
            if (value.neural().sign() == value.hfSign()) signAgreement++;
            for (var determinant : value.neural().determinants()) {
                headRatios[determinant.determinant()][sample] =
                        determinant.logMagnitude() - value.hfLogAbsolute();
            }
        }
        List<Double> headStandardDeviations = new ArrayList<>(determinants);
        for (double[] ratios : headRatios) headStandardDeviations.add(standardDeviation(ratios));
        if (!java.util.Arrays.equals(parametersBefore, FermiNetStateAccess.parameterSnapshot(state))) {
            throw new IllegalStateException("qualification modified parameters");
        }
        return new Result(
                squaredError / entryCount,
                Math.sqrt(leakageSquared / leakageCount),
                alphaSubspaces, betaSubspaces,
                mean(deltas), standardDeviation(deltas), range(deltas),
                signAgreement, samples.size(), headStandardDeviations);
    }

    public record Result(
            double orbitalMse,
            double crossSpinLeakageRms,
            List<Subspace> alphaSubspaces,
            List<Subspace> betaSubspaces,
            double psiLogRatioMean,
            double psiLogRatioStandardDeviation,
            double psiLogRatioRange,
            int signAgreement,
            int configurations,
            List<Double> determinantHeadLogRatioStandardDeviations) {
        public Result {
            alphaSubspaces = List.copyOf(alphaSubspaces);
            betaSubspaces = List.copyOf(betaSubspaces);
            determinantHeadLogRatioStandardDeviations =
                    List.copyOf(determinantHeadLogRatioStandardDeviations);
        }
    }

    public record Subspace(
            double relativeResidual,
            double conditionNumber,
            boolean nonsingular,
            int determinantSign,
            double logAbsoluteDeterminant) {}

    private record Sample(
            double[][] alpha,
            double[][] beta,
            FermiNetStateAccess.OrbitalSnapshot neural,
            int hfSign,
            double hfLogAbsolute) {}

    private record SignedLog(int sign, double logAbsolute) {}

    private static Subspace subspace(
            List<Sample> samples,
            boolean alphaSpin,
            int width,
            int determinantIndex) {
        double[][] hth = new double[width][width];
        double[][] htf = new double[width][width];
        for (Sample sample : samples) {
            double[][] h = alphaSpin ? sample.alpha() : sample.beta();
            int offset = alphaSpin ? 0 : sample.alpha().length;
            double[][] f = sample.neural().determinants()
                    .get(determinantIndex).orbitalMatrix();
            for (int row = 0; row < width; row++) {
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < width; y++) {
                        hth[x][y] += h[row][x] * h[row][y];
                        htf[x][y] += h[row][x] * f[offset + row][offset + y];
                    }
                }
            }
        }
        double[][] transform = solve(hth, htf);
        double error = 0.0;
        double norm = 0.0;
        for (Sample sample : samples) {
            double[][] h = alphaSpin ? sample.alpha() : sample.beta();
            int offset = alphaSpin ? 0 : sample.alpha().length;
            double[][] f = sample.neural().determinants()
                    .get(determinantIndex).orbitalMatrix();
            for (int row = 0; row < width; row++) {
                for (int column = 0; column < width; column++) {
                    double predicted = 0.0;
                    for (int k = 0; k < width; k++) predicted += h[row][k] * transform[k][column];
                    double difference = f[offset + row][offset + column] - predicted;
                    error += difference * difference;
                    norm += f[offset + row][offset + column] * f[offset + row][offset + column];
                }
            }
        }
        SignedLog determinant = signedLogDeterminant(transform);
        double condition = determinant.sign() == 0
                ? Double.POSITIVE_INFINITY
                : normOne(transform) * normOne(solve(transform, identity(width)));
        return new Subspace(Math.sqrt(error / norm), condition,
                determinant.sign() != 0, determinant.sign(), determinant.logAbsolute());
    }

    private static double[][] solve(double[][] left, double[][] right) {
        int n = left.length;
        double[][] a = copy(left);
        double[][] b = copy(right);
        for (int column = 0; column < n; column++) {
            int pivot = column;
            for (int row = column + 1; row < n; row++)
                if (Math.abs(a[row][column]) > Math.abs(a[pivot][column])) pivot = row;
            if (Math.abs(a[pivot][column]) < 1.0e-300)
                throw new IllegalStateException("singular occupied-subspace fit");
            double[] swap = a[column]; a[column] = a[pivot]; a[pivot] = swap;
            swap = b[column]; b[column] = b[pivot]; b[pivot] = swap;
            double diagonal = a[column][column];
            for (int j = column; j < n; j++) a[column][j] /= diagonal;
            for (int j = 0; j < b[column].length; j++) b[column][j] /= diagonal;
            for (int row = 0; row < n; row++) if (row != column) {
                double factor = a[row][column];
                for (int j = column; j < n; j++) a[row][j] -= factor * a[column][j];
                for (int j = 0; j < b[row].length; j++) b[row][j] -= factor * b[column][j];
            }
        }
        return b;
    }

    private static SignedLog signedLogDeterminant(double[][] input) {
        double[][] matrix = copy(input);
        int sign = 1;
        double log = 0.0;
        for (int column = 0; column < matrix.length; column++) {
            int pivot = column;
            for (int row = column + 1; row < matrix.length; row++)
                if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column])) pivot = row;
            if (Math.abs(matrix[pivot][column]) < 1.0e-300)
                return new SignedLog(0, Double.NEGATIVE_INFINITY);
            if (pivot != column) {
                double[] swap = matrix[column]; matrix[column] = matrix[pivot]; matrix[pivot] = swap;
                sign = -sign;
            }
            double diagonal = matrix[column][column];
            if (diagonal < 0.0) sign = -sign;
            log += Math.log(Math.abs(diagonal));
            for (int row = column + 1; row < matrix.length; row++) {
                double factor = matrix[row][column] / diagonal;
                for (int j = column + 1; j < matrix.length; j++)
                    matrix[row][j] -= factor * matrix[column][j];
            }
        }
        return new SignedLog(sign, log);
    }

    private static double[][] identity(int n) { double[][] r = new double[n][n]; for (int i=0;i<n;i++) r[i][i]=1.0; return r; }
    private static double[][] copy(double[][] v) { double[][] r=new double[v.length][]; for(int i=0;i<v.length;i++)r[i]=v[i].clone(); return r; }
    private static double normOne(double[][] v) { double best=0; for(int j=0;j<v[0].length;j++){double sum=0;for(double[] doubles:v)sum+=Math.abs(doubles[j]);best=Math.max(best,sum);}return best; }
    private static double mean(double[] v) { double sum=0; for(double x:v)sum+=x; return sum/v.length; }
    private static double standardDeviation(double[] v) { double m=mean(v),sum=0;for(double x:v)sum+=(x-m)*(x-m);return Math.sqrt(sum/v.length); }
    private static double sampleStandardDeviation(double[] v,double m) { if(v.length<2)return 0.0;double sum=0;for(double x:v)sum+=(x-m)*(x-m);return Math.sqrt(sum/(v.length-1)); }
    private static double range(double[] v) { double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;for(double x:v){min=Math.min(min,x);max=Math.max(max,x);}return max-min; }
    private static MessageDigest sha256() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private static void update(MessageDigest digest,String value) { byte[] bytes=value.getBytes(StandardCharsets.UTF_8);update(digest,bytes.length);digest.update(bytes); }
    private static void update(MessageDigest digest,int value) { digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array()); }
    private static void update(MessageDigest digest,long value) { digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array()); }
}
