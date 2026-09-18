package totah.lab.aether.model;

import java.util.List;
import totah.lab.gaia.chemistry.Element;

/**
 * Immutable fixed H/C/N/O/P/S/Cl system, with ordered nuclei in bohr and an explicit electronic state.
 * Charge and multiplicity distinguish provenance; no wavefunction or energy is calculated.
 */
public record QuantumSystem(List<NuclearCenter> nuclei, int molecularCharge, int multiplicity) {
    public QuantumSystem {
        nuclei = List.copyOf(nuclei);
        if (nuclei.isEmpty()) throw new IllegalArgumentException("At least one nucleus is required");
        for (NuclearCenter nucleus : nuclei) {
            if (nucleus.charge() != Element.H.getAtomicNumber() && nucleus.charge() != Element.C.getAtomicNumber()
                    && nucleus.charge() != Element.N.getAtomicNumber() && nucleus.charge() != Element.O.getAtomicNumber()
                    && nucleus.charge() != Element.P.getAtomicNumber() && nucleus.charge() != Element.S.getAtomicNumber()
                    && nucleus.charge() != Element.CL.getAtomicNumber()) {
                throw new IllegalArgumentException("UNSUPPORTED_CHEMISTRY: QuantumSystem supports H/C/N/O/P/S/Cl only");
            }
        }
        long electrons = nuclei.stream().mapToLong(n -> (long) n.charge()).sum() - molecularCharge;
        long unpaired = (long) multiplicity - 1;
        if (electrons < 0 || unpaired < 0 || unpaired > electrons || (electrons - unpaired) % 2 != 0) {
            throw new IllegalArgumentException("Incompatible electron count and multiplicity");
        }
    }
}
