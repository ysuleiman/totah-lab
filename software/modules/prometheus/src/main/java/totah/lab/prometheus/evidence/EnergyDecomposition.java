package totah.lab.prometheus.evidence;

import java.util.Objects;

/**
 * Decomposition of a classical energy into per-term components (kcal/mol).
 * Individual components may be {@code null} when the energy was not decomposed
 * into that term; the total is always required.
 */
public record EnergyDecomposition(
        Double totalKcalMol,
        Double bondKcalMol,
        Double angleKcalMol,
        Double torsionKcalMol,
        Double improperKcalMol,
        Double vdw14KcalMol,
        Double electrostaticKcalMol,
        Double ljKcalMol,
        Double interactionKcalMol) {

    public EnergyDecomposition {
        Objects.requireNonNull(totalKcalMol, "totalKcalMol");
    }
}
