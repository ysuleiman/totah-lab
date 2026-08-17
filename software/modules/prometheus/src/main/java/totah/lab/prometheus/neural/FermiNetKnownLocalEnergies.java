package totah.lab.prometheus.neural;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.molecular.LocalEnergyComponents;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** State- and coordinate-bound local energies eligible for exact SR reuse. */
final class FermiNetKnownLocalEnergies {

    private final String stateIdentity;
    private final List<Entry> entries;

    private FermiNetKnownLocalEnergies(String stateIdentity, List<Entry> entries) {
        this.stateIdentity = Objects.requireNonNull(stateIdentity, "stateIdentity");
        this.entries = List.copyOf(entries);
    }

    static FermiNetKnownLocalEnergies from(
            FermiNetV1State state,
            FermiNetVmc.Result result) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(result, "result");
        String identity = FermiNetStateIdentity.of(state);
        if (!identity.equals(result.stateIdentity())) {
            throw new IllegalArgumentException("VMC energy state identity mismatch");
        }
        if (result.samples().size() != result.localEnergies().size()) {
            throw new IllegalArgumentException("VMC sample/local-energy count mismatch");
        }
        List<Entry> entries = new ArrayList<>(result.samples().size());
        for (int i = 0; i < result.samples().size(); i++) {
            LocalEnergyComponents energy = result.localEnergies().get(i);
            requireFinite(energy);
            entries.add(new Entry(i, result.samples().get(i), energy));
        }
        return new FermiNetKnownLocalEnergies(identity, entries);
    }

    void validate(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples) {
        if (!stateIdentity.equals(FermiNetStateIdentity.of(state))) {
            throw new IllegalArgumentException("known local energies belong to a different FermiNet state");
        }
        if (entries.size() != samples.size()) {
            throw new IllegalArgumentException("known local-energy sample count mismatch");
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.sampleIndex() != i) {
                throw new IllegalArgumentException("missing or duplicate known local-energy sample index");
            }
            if (!sameRawCoordinates(entry.coordinates(), samples.get(i).coordinates())) {
                throw new IllegalArgumentException(
                        "known local-energy coordinate/order mismatch at sample " + i);
            }
            requireFinite(entry.localEnergy());
        }
    }

    LocalEnergyComponents energy(int sampleIndex) {
        Entry entry = entries.get(sampleIndex);
        if (entry.sampleIndex() != sampleIndex) {
            throw new IllegalArgumentException("known local-energy sample index mismatch");
        }
        return entry.localEnergy();
    }

    private static boolean sameRawCoordinates(
            QuantumCoordinates left,
            QuantumCoordinates right) {
        if (left.particles().size() != right.particles().size()) return false;
        for (int i = 0; i < left.particles().size(); i++) {
            var a = left.particles().get(i);
            var b = right.particles().get(i);
            if (a.particleIndex() != b.particleIndex()
                    || a.spin() != b.spin()
                    || Double.doubleToRawLongBits(a.xBohr())
                    != Double.doubleToRawLongBits(b.xBohr())
                    || Double.doubleToRawLongBits(a.yBohr())
                    != Double.doubleToRawLongBits(b.yBohr())
                    || Double.doubleToRawLongBits(a.zBohr())
                    != Double.doubleToRawLongBits(b.zBohr())) {
                return false;
            }
        }
        return true;
    }

    private static void requireFinite(LocalEnergyComponents energy) {
        Objects.requireNonNull(energy, "localEnergy");
        if (!Double.isFinite(energy.kineticHartree())
                || !Double.isFinite(energy.electronNuclearHartree())
                || !Double.isFinite(energy.electronElectronHartree())
                || !Double.isFinite(energy.nuclearNuclearHartree())
                || !Double.isFinite(energy.totalHartree())) {
            throw new IllegalArgumentException("non-finite known local-energy component");
        }
    }

    private record Entry(
            int sampleIndex,
            QuantumCoordinates coordinates,
            LocalEnergyComponents localEnergy) {
        private Entry {
            Objects.requireNonNull(coordinates, "coordinates");
            Objects.requireNonNull(localEnergy, "localEnergy");
        }
    }
}
