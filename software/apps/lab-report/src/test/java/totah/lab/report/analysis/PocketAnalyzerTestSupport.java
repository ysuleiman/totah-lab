package totah.lab.report.analysis;

import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketMetric;
import totah.lab.gaia.pocket.PocketMetricType;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class PocketAnalyzerTestSupport {

    private PocketAnalyzerTestSupport() {
    }

    static Residue residue(
            String name,
            int number,
            Point3D... positions
    ) {
        List<Atom> atoms = java.util.stream.IntStream
                .range(0, positions.length)
                .mapToObj(index -> Atom.builder()
                        .name(index == 0 ? "CA" : "X" + index)
                        .position(positions[index])
                        .element(Element.C)
                        .build())
                .toList();
        return Residue.builder()
                .name(name)
                .number(number)
                .atoms(atoms)
                .build();
    }

    static Structure structure(Residue... residues) {
        return new Structure(List.of(
                new Chain("A", Arrays.asList(residues))));
    }

    static Pocket pocket(
            Map<String, Object> attributes,
            Residue... residues
    ) {
        List<ResidueId> references = Arrays.stream(residues)
                .map(residue -> new ResidueId(
                        "A",
                        residue.getNumber(),
                        null))
                .toList();
        List<PocketMetric> metrics = new ArrayList<>();
        Map<String, String> metadata = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            PocketMetricType type = metricType(key);
            if (type != null && value instanceof Number number) {
                metrics.add(new PocketMetric(
                        type, number.doubleValue()));
            } else {
                metadata.put(key, String.valueOf(value));
            }
        });
        return new Pocket(
                PocketId.of(1),
                "generic-pocket",
                PocketSource.FPOCKET,
                new Point3D(0, 0, 0),
                references,
                metrics,
                Optional.empty(),
                Optional.empty(),
                metadata
        );
    }

    private static PocketMetricType metricType(String key) {
        return switch (key) {
            case "volume" -> PocketMetricType.VOLUME;
            case "druggability_score" ->
                    PocketMetricType.FPOCKET_DRUGGABILITY;
            case "probability" -> PocketMetricType.P2RANK_PROBABILITY;
            default -> null;
        };
    }
}
