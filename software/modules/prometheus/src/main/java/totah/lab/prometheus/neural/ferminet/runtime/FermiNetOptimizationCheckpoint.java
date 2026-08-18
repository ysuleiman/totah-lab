package totah.lab.prometheus.neural.ferminet.runtime;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/** Immutable, exact continuation state for production FermiNet optimization. */
public final class FermiNetOptimizationCheckpoint {

    private static final String MAGIC = "PROMETHEUS_FERMINET_OPTIMIZATION_CHECKPOINT";
    private static final int VERSION = 1;

    private final int completedIterations;
    private final String rootParameterChecksum;
    private final String parameterChecksum;
    private final String walkerChecksum;
    private final String randomStateChecksum;
    private final String samplingConfigurationIdentity;
    private final String optimizerConfigurationIdentity;
    private final String geometryIdentity;
    private final FermiNetOptimizerType optimizerType;
    private final double[] parameters;
    private final List<QuantumCoordinates> walkers;
    private final byte[] serializedRandomState;

    FermiNetOptimizationCheckpoint(
            int completedIterations,
            String rootParameterChecksum,
            String samplingConfigurationIdentity,
            String optimizerConfigurationIdentity,
            String geometryIdentity,
            FermiNetOptimizerType optimizerType,
            double[] parameters,
            List<QuantumCoordinates> walkers,
            byte[] serializedRandomState) {
        if (completedIterations < 0) {
            throw new IllegalArgumentException("negative completed iteration count");
        }
        this.completedIterations = completedIterations;
        this.rootParameterChecksum = requireChecksum(
                rootParameterChecksum, "root parameter checksum");
        this.samplingConfigurationIdentity = requireChecksum(
                samplingConfigurationIdentity, "sampling configuration identity");
        this.optimizerConfigurationIdentity = requireChecksum(
                optimizerConfigurationIdentity, "optimizer configuration identity");
        this.geometryIdentity = requireChecksum(geometryIdentity, "geometry identity");
        this.optimizerType = Objects.requireNonNull(optimizerType, "optimizerType");
        this.parameters = Objects.requireNonNull(parameters, "parameters").clone();
        this.walkers = List.copyOf(Objects.requireNonNull(walkers, "walkers"));
        this.serializedRandomState = Objects.requireNonNull(
                serializedRandomState, "serializedRandomState").clone();
        if (this.parameters.length == 0 || this.walkers.isEmpty()
                || this.serializedRandomState.length == 0) {
            throw new IllegalArgumentException("empty optimization checkpoint payload");
        }
        for (double parameter : this.parameters) {
            if (!Double.isFinite(parameter)) {
                throw new IllegalArgumentException("non-finite checkpoint parameter");
            }
        }
        this.parameterChecksum = parameterChecksum(this.parameters);
        this.walkerChecksum = walkerChecksum(this.walkers);
        this.randomStateChecksum = byteChecksum(this.serializedRandomState);
    }

    public int completedIterations() { return completedIterations; }
    public String rootParameterChecksum() { return rootParameterChecksum; }
    public String parameterChecksum() { return parameterChecksum; }
    public String walkerChecksum() { return walkerChecksum; }
    public String randomStateChecksum() { return randomStateChecksum; }
    public String samplingConfigurationIdentity() { return samplingConfigurationIdentity; }
    public String optimizerConfigurationIdentity() { return optimizerConfigurationIdentity; }
    public String geometryIdentity() { return geometryIdentity; }
    public FermiNetOptimizerType optimizerType() { return optimizerType; }
    public double[] parameters() { return parameters.clone(); }
    public List<QuantumCoordinates> walkers() { return List.copyOf(walkers); }
    byte[] serializedRandomState() { return serializedRandomState.clone(); }

    public void write(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent == null) throw new IOException("checkpoint path has no parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
        boolean completed = false;
        try {
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temporary)))) {
                output.writeUTF(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(completedIterations);
                output.writeUTF(rootParameterChecksum);
                output.writeUTF(parameterChecksum);
                output.writeUTF(walkerChecksum);
                output.writeUTF(randomStateChecksum);
                output.writeUTF(samplingConfigurationIdentity);
                output.writeUTF(optimizerConfigurationIdentity);
                output.writeUTF(geometryIdentity);
                output.writeUTF(optimizerType.name());
                output.writeInt(parameters.length);
                for (double parameter : parameters) output.writeLong(
                        Double.doubleToRawLongBits(parameter));
                output.writeInt(walkers.size());
                for (QuantumCoordinates walker : walkers) {
                    output.writeInt(walker.particles().size());
                    for (var particle : walker.particles()) {
                        output.writeInt(particle.particleIndex());
                        output.writeUTF(particle.spin().name());
                        output.writeLong(Double.doubleToRawLongBits(particle.xBohr()));
                        output.writeLong(Double.doubleToRawLongBits(particle.yBohr()));
                        output.writeLong(Double.doubleToRawLongBits(particle.zBohr()));
                    }
                }
                output.writeInt(serializedRandomState.length);
                output.write(serializedRandomState);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            completed = true;
        } finally {
            if (!completed) Files.deleteIfExists(temporary);
        }
    }

    public static FermiNetOptimizationCheckpoint read(Path path) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(path)))) {
            if (!MAGIC.equals(input.readUTF()) || input.readInt() != VERSION) {
                throw new IOException("unsupported FermiNet optimization checkpoint");
            }
            int completed = input.readInt();
            String root = input.readUTF();
            String expectedParameters = input.readUTF();
            String expectedWalkers = input.readUTF();
            String expectedRandom = input.readUTF();
            String sampling = input.readUTF();
            String optimizer = input.readUTF();
            String geometry = input.readUTF();
            FermiNetOptimizerType type = FermiNetOptimizerType.valueOf(input.readUTF());
            int parameterCount = positiveBounded(input.readInt(), 10_000_000, "parameters");
            double[] parameters = new double[parameterCount];
            for (int i = 0; i < parameters.length; i++) {
                parameters[i] = Double.longBitsToDouble(input.readLong());
            }
            int walkerCount = positiveBounded(input.readInt(), 1_000_000, "walkers");
            List<QuantumCoordinates> walkers = new ArrayList<>(walkerCount);
            for (int walker = 0; walker < walkerCount; walker++) {
                int particles = positiveBounded(input.readInt(), 10_000, "particles");
                List<QuantumCoordinates.ParticleCoordinate> coordinates =
                        new ArrayList<>(particles);
                for (int particle = 0; particle < particles; particle++) {
                    int particleIndex = input.readInt();
                    SpinProjection spin = SpinProjection.valueOf(input.readUTF());
                    coordinates.add(new QuantumCoordinates.ParticleCoordinate(
                            particleIndex,
                            Double.longBitsToDouble(input.readLong()),
                            Double.longBitsToDouble(input.readLong()),
                            Double.longBitsToDouble(input.readLong()),
                            spin));
                }
                walkers.add(new QuantumCoordinates(coordinates));
            }
            int randomLength = positiveBounded(input.readInt(), 1_000_000, "RNG state");
            byte[] random = input.readNBytes(randomLength);
            if (random.length != randomLength || input.read() != -1) {
                throw new IOException("truncated/trailing checkpoint data");
            }
            FermiNetOptimizationCheckpoint checkpoint = new FermiNetOptimizationCheckpoint(
                    completed, root, sampling, optimizer, geometry, type,
                    parameters, walkers, random);
            if (!expectedParameters.equals(checkpoint.parameterChecksum)
                    || !expectedWalkers.equals(checkpoint.walkerChecksum)
                    || !expectedRandom.equals(checkpoint.randomStateChecksum)) {
                throw new IOException("optimization checkpoint checksum mismatch");
            }
            return checkpoint;
        } catch (EOFException | IllegalArgumentException exception) {
            throw new IOException("invalid FermiNet optimization checkpoint", exception);
        }
    }

    static String samplingIdentity(FermiNetVariationalOptimizer.SamplingConfiguration value) {
        MessageDigest digest = sha256();
        update(digest, value.walkers());
        update(digest, value.warmupSweeps());
        update(digest, value.retainedPerWalker());
        update(digest, value.sweepsBetweenRetained());
        update(digest, Double.doubleToRawLongBits(value.stepSizeBohr()));
        update(digest, value.baseSeed());
        return HexFormat.of().formatHex(digest.digest());
    }

    static String optimizerIdentity(
            FermiNetVariationalOptimizer.OptimizationConfiguration value) {
        MessageDigest digest = sha256();
        update(digest, value.optimizerType().name());
        if (value.optimizerType() == FermiNetOptimizerType.EXACT_SR) {
            var exact = value.exactSrConfiguration();
            update(digest, Double.doubleToRawLongBits(exact.learningRate()));
            update(digest, Double.doubleToRawLongBits(exact.damping()));
            update(digest, Double.doubleToRawLongBits(exact.maxUpdateNorm()));
            update(digest, exact.observationParallelism());
        } else {
            update(digest, value.kfacConfiguration().toString());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String parameterChecksum(double[] parameters) {
        MessageDigest digest = sha256();
        for (double parameter : parameters) update(
                digest, Double.doubleToRawLongBits(parameter));
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String walkerChecksum(List<QuantumCoordinates> walkers) {
        MessageDigest digest = sha256();
        for (QuantumCoordinates walker : walkers) {
            for (var particle : walker.particles()) {
                update(digest, particle.particleIndex());
                update(digest, particle.spin().ordinal());
                update(digest, Double.doubleToRawLongBits(particle.xBohr()));
                update(digest, Double.doubleToRawLongBits(particle.yBohr()));
                update(digest, Double.doubleToRawLongBits(particle.zBohr()));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String byteChecksum(byte[] values) {
        MessageDigest digest = sha256();
        digest.update(values);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static int positiveBounded(int value, int maximum, String label) throws IOException {
        if (value < 1 || value > maximum) throw new IOException("invalid " + label + " count");
        return value;
    }

    private static String requireChecksum(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid " + label);
        }
        return value;
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        update(digest, bytes.length);
        digest.update(bytes);
    }

    private static void update(MessageDigest digest, long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            digest.update((byte) (value >>> shift));
        }
    }
}
