package totah.lab.prometheus.neural.ferminet.reference;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/** Immutable, exactly replayable electron-configuration dataset for correlated FD. */
public final class FermiNetCorrelatedFdConfigurationFile {

    private static final String HEADER =
            "sample,chain,retained,electron,spin,x_bohr_hex,y_bohr_hex,z_bohr_hex";

    private FermiNetCorrelatedFdConfigurationFile() {}

    public static Identity write(
            Path path,
            List<QuantumCoordinates> configurations,
            int walkerCount) throws IOException {
        Objects.requireNonNull(path, "path");
        configurations = List.copyOf(Objects.requireNonNull(
                configurations, "configurations"));
        if (walkerCount < 1 || configurations.isEmpty()
                || configurations.size() % walkerCount != 0) {
            throw new IllegalArgumentException("invalid correlated-FD sample topology");
        }
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent == null) throw new IOException("configuration path has no parent");
        Files.createDirectories(parent);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW)) {
            writer.write(HEADER);
            writer.newLine();
            for (int sample = 0; sample < configurations.size(); sample++) {
                int chain = sample % walkerCount;
                int retained = sample / walkerCount;
                for (var electron : configurations.get(sample).particles()) {
                    writer.write(sample + "," + chain + "," + retained + ","
                            + electron.particleIndex() + "," + electron.spin() + ","
                            + Double.toHexString(electron.xBohr()) + ","
                            + Double.toHexString(electron.yBohr()) + ","
                            + Double.toHexString(electron.zBohr()));
                    writer.newLine();
                }
            }
        }
        Identity identity = inspect(path, walkerCount);
        if (identity.sampleCount() != configurations.size()) {
            throw new IOException("configuration persistence count mismatch");
        }
        return identity;
    }

    public static Identity inspect(Path path, int walkerCount) throws IOException {
        Counter counter = new Counter();
        forEach(path, walkerCount, (sample, chain, retained, coordinates) -> {
            if (sample != counter.samples || chain != sample % walkerCount
                    || retained != sample / walkerCount) {
                throw new IOException("noncanonical configuration ordering");
            }
            counter.samples++;
            counter.electrons = coordinates.particles().size();
        });
        if (counter.samples == 0) throw new IOException("empty configuration dataset");
        return new Identity(sha256(path), counter.samples, walkerCount,
                counter.samples / walkerCount, counter.electrons);
    }

    public static void forEach(
            Path path,
            int walkerCount,
            ConfigurationConsumer consumer) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(consumer, "consumer");
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            if (!HEADER.equals(reader.readLine())) {
                throw new IOException("invalid correlated-FD configuration header");
            }
            String line;
            int currentSample = -1;
            int currentChain = -1;
            int currentRetained = -1;
            List<QuantumCoordinates.ParticleCoordinate> particles = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",", -1);
                if (fields.length != 8) throw new IOException("invalid configuration row");
                int sample = parseInt(fields[0], "sample");
                int chain = parseInt(fields[1], "chain");
                int retained = parseInt(fields[2], "retained");
                if (currentSample >= 0 && sample != currentSample) {
                    consumer.accept(currentSample, currentChain, currentRetained,
                            new QuantumCoordinates(particles));
                    particles = new ArrayList<>();
                }
                if (sample != currentSample) {
                    if (sample != currentSample + 1 || chain != sample % walkerCount
                            || retained != sample / walkerCount) {
                        throw new IOException("invalid configuration provenance ordering");
                    }
                    currentSample = sample;
                    currentChain = chain;
                    currentRetained = retained;
                } else if (chain != currentChain || retained != currentRetained) {
                    throw new IOException("inconsistent configuration provenance");
                }
                int electron = parseInt(fields[3], "electron");
                if (electron != particles.size()) {
                    throw new IOException("noncanonical electron ordering");
                }
                try {
                    particles.add(new QuantumCoordinates.ParticleCoordinate(
                            electron, Double.valueOf(fields[5]), Double.valueOf(fields[6]),
                            Double.valueOf(fields[7]), SpinProjection.valueOf(fields[4])));
                } catch (IllegalArgumentException exception) {
                    throw new IOException("invalid configuration value", exception);
                }
            }
            if (currentSample >= 0) {
                consumer.accept(currentSample, currentChain, currentRetained,
                        new QuantumCoordinates(particles));
            }
        }
    }

    private static int parseInt(String value, String label) throws IOException {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) {
            throw new IOException("invalid " + label, exception);
        }
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) >= 0;) {
                if (count > 0) digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    @FunctionalInterface
    public interface ConfigurationConsumer {
        void accept(int sample, int chain, int retained,
                    QuantumCoordinates coordinates) throws IOException;
    }

    public record Identity(
            String sha256,
            int sampleCount,
            int walkerCount,
            int retainedPerWalker,
            int electronsPerConfiguration) {}

    private static final class Counter { int samples; int electrons; }
}
