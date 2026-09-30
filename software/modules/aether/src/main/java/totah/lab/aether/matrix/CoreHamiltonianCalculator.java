package totah.lab.aether.matrix;

import java.util.List;
import java.util.Objects;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;

/**
 * Context-checked composition of existing one-electron integrals, in hartree.
 * Legacy T/V are system-independent with respect to electron count and spin. Binding explicitly
 * associates them with a system after checking all operator-relevant input provenance; it does
 * not claim that their original receipts contained electronic-state provenance.
 */
public final class CoreHamiltonianCalculator {
    private final QuantumSystem system;
    private final List<ContractedGaussian> functions;
    private final String basisGeometryHash;
    private final String nuclearCentersHash;
    private final String systemHash;

    public CoreHamiltonianCalculator(QuantumSystem system, List<ContractedGaussian> functions) {
        this.system = Objects.requireNonNull(system);
        this.functions = List.copyOf(functions);
        basisGeometryHash = IntegralMatrixData.basisGeometryHash(this.functions);
        nuclearCentersHash = IntegralMatrixData.nuclearCentersHash(system.nuclei());
        systemHash = IntegralMatrixData.systemHash(system);
    }

    /** Explicitly bind a reusable T matrix to this system and ordered basis. */
    public KineticInput bind(KineticMatrix kinetic) {
        Objects.requireNonNull(kinetic);
        requireEqual(basisGeometryHash, kinetic.receipt().basisGeometryHash(), "kinetic basis/geometry/order");
        requireEqual(KineticMatrix.IMPLEMENTATION, kinetic.receipt().implementation(), "kinetic implementation");
        requireEqual(IntegralMatrixData.protocol(KineticMatrix.PROTOCOL, functions), kinetic.receipt().protocol(), "kinetic units/numerical protocol");
        return new KineticInput(this, kinetic);
    }

    /** Explicitly bind V only after verifying its nuclei as well as its ordered basis. */
    public NuclearAttractionInput bind(NuclearAttractionMatrix attraction) {
        Objects.requireNonNull(attraction);
        requireEqual(basisGeometryHash, attraction.receipt().basisGeometryHash(), "attraction basis/geometry/order");
        requireEqual(nuclearCentersHash, attraction.receipt().nuclearCentersHash(), "nuclear system/geometry");
        requireEqual(NuclearAttractionMatrix.IMPLEMENTATION, attraction.receipt().implementation(), "attraction implementation");
        requireEqual(IntegralMatrixData.protocol(NuclearAttractionMatrix.PROTOCOL, functions), attraction.receipt().protocol(), "attraction units/numerical protocol");
        return new NuclearAttractionInput(this, attraction);
    }

    /** Compute T and V through their existing implementations, then perform checked addition. */
    public CoreHamiltonianMatrix calculate() {
        return calculate(bind(KineticMatrix.compute(functions)),
                bind(NuclearAttractionMatrix.compute(functions, system.nuclei())));
    }

    /** Add already-computed inputs only if both bindings have this exact canonical context. */
    public CoreHamiltonianMatrix calculate(KineticInput kinetic, NuclearAttractionInput attraction) {
        Objects.requireNonNull(kinetic);
        Objects.requireNonNull(attraction);
        requireContext(kinetic.context);
        requireContext(attraction.context);
        return new CoreHamiltonianMatrix(system, systemHash, kinetic.matrix, attraction.matrix);
    }

    private void requireContext(CoreHamiltonianCalculator other) {
        requireEqual(systemHash, other.systemHash, "QuantumSystem");
        requireEqual(basisGeometryHash, other.basisGeometryHash, "basis/geometry/order");
    }

    private static void requireEqual(String expected, String actual, String component) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("Incompatible " + component + " provenance");
    }

    /** Immutable system-bound kinetic input; only a checked binding can create one. */
    public static final class KineticInput {
        private final CoreHamiltonianCalculator context;
        private final KineticMatrix matrix;

        private KineticInput(CoreHamiltonianCalculator context, KineticMatrix matrix) {
            this.context = context;
            this.matrix = matrix;
        }
    }

    /** Immutable system-bound attraction input; only a checked binding can create one. */
    public static final class NuclearAttractionInput {
        private final CoreHamiltonianCalculator context;
        private final NuclearAttractionMatrix matrix;

        private NuclearAttractionInput(CoreHamiltonianCalculator context, NuclearAttractionMatrix matrix) {
            this.context = context;
            this.matrix = matrix;
        }
    }
}
