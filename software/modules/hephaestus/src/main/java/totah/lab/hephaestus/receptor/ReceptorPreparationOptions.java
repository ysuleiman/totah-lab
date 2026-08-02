package totah.lab.hephaestus.receptor;


import totah.lab.hephaestus.export.ReceptorPdbqtExportOptions;
import totah.lab.hephaestus.flexibility.FlexibilityPreparationConfig;
import totah.lab.hephaestus.receptor.protonation.ProtonationConfig;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record ReceptorPreparationOptions(
        boolean removeWaters,
        boolean keepMetals,
        Set<String> allowedSpecialResidues,
        Double plddtCutoff,
        boolean addHydrogens,
        boolean optimizeHydrogens,
        boolean buildTopology,
        boolean assignCharges,
        boolean assignAtomTypes,
        ProtonationConfig protonationConfig,
        Map<String, String> residueProtonationOverrides,
        FlexibilityPreparationConfig flexibilityConfig,
        ReceptorPdbqtExportOptions pdbqtExportOptions) {

    public ReceptorPreparationOptions {
        allowedSpecialResidues =
                allowedSpecialResidues == null
                        ? Set.of("MSE", "TYS")
                        : allowedSpecialResidues.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(value ->
                                value.toUpperCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());

        protonationConfig = protonationConfig == null
                ? ProtonationConfig.defaults()
                : protonationConfig;

        residueProtonationOverrides =
                residueProtonationOverrides == null
                        ? Map.of()
                        : Map.copyOf(residueProtonationOverrides);

        if (plddtCutoff != null
                && (!Double.isFinite(plddtCutoff)
                || plddtCutoff < 0.0
                || plddtCutoff > 100.0)) {

            throw new IllegalArgumentException(
                    "pLDDT cutoff must be between 0 and 100.");
        }
    }

    public static ReceptorPreparationOptions defaults() {
        return new ReceptorPreparationOptions(
                true,
                false,
                Set.of("MSE", "TYS"),
                null,
                true,
                true,
                true,
                true,
                true,
                ProtonationConfig.defaults(),
                Map.of(),
                FlexibilityPreparationConfig.none(),
                null);
    }
}
