package totah.lab.prometheus.neural.ferminet.runtime;

import totah.lab.prometheus.neural.ferminet.pretraining.GaussianHartreeFockOrbitalTargetTest;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import totah.lab.prometheus.molecular.LocalEnergyComponents;

final class LockedFermiNetH2oEnergyQualificationTest {
    @Test
    void measureMatchedBeforeAndAfterNeuralVmc() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("ferminet.energyQualification"));
        Path output = Path.of(System.getProperty("ferminet.output"));
        var molecule = GaussianHartreeFockOrbitalTargetTest.water();
        var architecture = FermiNetV1Configuration.locked();
        var layout = new FermiNetParameterLayout(architecture, molecule);
        var before = new FermiNetV1State(molecule, architecture,
                FermiNetParameters.initialize(layout, 20260815L));
        var after = FermiNetStateAccess.replaceParameters(before, readParameters(
                output.resolve("pretrained-parameters.hex"), layout.parameterCount()));
        int walkers = Integer.getInteger("ferminet.energyWalkers", 16);
        int warmup = Integer.getInteger("ferminet.energyWarmup", 10);
        int retained = Integer.getInteger("ferminet.energyRetained", 5);
        double step = Double.parseDouble(System.getProperty("ferminet.energyStep", ".05"));
        String phase = System.getProperty("ferminet.energyPhase", "both");
        var configuration = new FermiNetRuntimeSampling.Request(
                walkers, warmup, retained, 1, step, 66791L);
        if (!phase.equals("after")) {
            var beforeResult = FermiNetRuntimeSampling.sampleSerial(before, configuration);
            System.out.println("LOCKED_ENERGY before " + summary(beforeResult));
            write(output.resolve("neural-vmc-before.csv"), beforeResult.localEnergies());
        }
        if (!phase.equals("before")) {
            var afterResult = FermiNetRuntimeSampling.sampleSerial(after, configuration);
            System.out.println("LOCKED_ENERGY after " + summary(afterResult));
            write(output.resolve("neural-vmc-after-qualified.csv"), afterResult.localEnergies());
            Files.writeString(output.resolve("neural-vmc-qualified-summary.txt"),
                    "sampling=DIRECT_NEURAL_ABS_PSI_SQUARED_RWM\nwalkers=" + walkers
                            + "\nwarmup_sweeps=" + warmup + "\nretained_per_walker=" + retained
                            + "\nstep_bohr=" + step + "\nafter=" + summary(afterResult) + "\n");
            assertTrue(afterResult.localEnergies().stream()
                    .allMatch(energy -> Double.isFinite(energy.totalHartree())));
        }
    }

    private static double[] readParameters(Path path, int count) throws IOException {
        double[] result = new double[count];
        try (var lines = Files.lines(path)) {
            var iterator = lines.iterator();
            int index = 0;
            while (iterator.hasNext()) {
                if (index == count) throw new IOException("too many persisted parameters");
                result[index++] = Double.longBitsToDouble(
                        Long.parseUnsignedLong(iterator.next(), 16));
            }
            if (index != count) throw new IOException("persisted parameter count mismatch");
        }
        return result;
    }

    private static String summary(FermiNetRuntimeSampling.Result result) {
        double kinetic = mean(result.localEnergies(), 0);
        double electronNuclear = mean(result.localEnergies(), 1);
        double electronElectron = mean(result.localEnergies(), 2);
        double nuclearNuclear = mean(result.localEnergies(), 3);
        double total = kinetic + electronNuclear + electronElectron + nuclearNuclear;
        double variance = result.localEnergies().stream()
                .mapToDouble(e -> square(e.totalHartree() - total)).sum()
                / (result.localEnergies().size() - 1);
        double standardError = Math.sqrt(variance / result.localEnergies().size());
        return "samples=" + result.localEnergies().size() + ",acceptance=" + result.acceptance()
                + ",kinetic=" + kinetic + ",electron_nuclear=" + electronNuclear
                + ",electron_electron=" + electronElectron + ",nuclear_nuclear=" + nuclearNuclear
                + ",total=" + total + ",naive_standard_error=" + standardError;
    }

    private static double mean(List<LocalEnergyComponents> energies, int component) {
        return energies.stream().mapToDouble(e -> switch (component) {
            case 0 -> e.kineticHartree();
            case 1 -> e.electronNuclearHartree();
            case 2 -> e.electronElectronHartree();
            default -> e.nuclearNuclearHartree();
        }).average().orElseThrow();
    }

    private static void write(Path path, List<LocalEnergyComponents> energies) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("sample,kinetic,electron_nuclear,electron_electron,nuclear_nuclear,total\n");
            for (int i = 0; i < energies.size(); i++) {
                var e = energies.get(i);
                writer.write(i + "," + e.kineticHartree() + "," + e.electronNuclearHartree()
                        + "," + e.electronElectronHartree() + "," + e.nuclearNuclearHartree()
                        + "," + e.totalHartree() + "\n");
            }
        }
    }

    private static double square(double value) {
        return value * value;
    }
}
