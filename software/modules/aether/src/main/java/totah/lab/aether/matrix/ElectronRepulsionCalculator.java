package totah.lab.aether.matrix;

import java.util.List;
import java.util.Objects;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;

/** Deterministic, unscreened Cartesian s/p ERIs in an explicit fixed-system and ordered-basis context. */
public final class ElectronRepulsionCalculator {
    private final QuantumSystem system;
    private final List<ContractedGaussian> functions;

    public ElectronRepulsionCalculator(QuantumSystem system, List<ContractedGaussian> functions) {
        this.system = Objects.requireNonNull(system);
        this.functions = List.copyOf(functions);
        if (this.functions.isEmpty()) throw new IllegalArgumentException("Basis must not be empty");
    }

    public ElectronRepulsionTensor calculate() {
        return new ElectronRepulsionTensor(system, functions);
    }
}
