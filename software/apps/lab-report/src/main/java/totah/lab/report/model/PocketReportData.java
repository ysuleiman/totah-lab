package totah.lab.report.model;

import totah.lab.gaia.pocket.PocketSource;

import java.util.Map;
import java.util.Objects;

public record PocketReportData(
        long pocketId,
        String pocketName,
        PocketSource source,
        Map<String, Object> geometry,
        Map<String, Object> residues,
        Map<String, Object> docking,
        Map<String, Object> hotspots
) {
    public PocketReportData {
        Objects.requireNonNull(pocketName, "pocketName");
        Objects.requireNonNull(source, "source");
        geometry = Map.copyOf(geometry);
        residues = Map.copyOf(residues);
        docking = Map.copyOf(docking);
        hotspots = Map.copyOf(hotspots);
    }
}
