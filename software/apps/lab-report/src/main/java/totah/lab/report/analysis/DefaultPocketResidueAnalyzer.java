package totah.lab.report.analysis;

import totah.lab.pocket.Pocket;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.report.config.PocketReportConfiguration;
import totah.lab.report.evidence.EvidenceCategory;
import totah.lab.report.evidence.ReportEvidence;
import totah.lab.report.model.ResidueCategory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DefaultPocketResidueAnalyzer
        implements PocketResidueAnalyzer {

    private static final Set<String> HYDROPHOBIC =
            Set.of("ALA", "VAL", "ILE", "LEU", "MET", "PHE", "TYR", "TRP");
    private static final Set<String> POLAR =
            Set.of("SER", "THR", "ASN", "GLN", "CYS");
    private static final Set<String> POSITIVE =
            Set.of("LYS", "ARG", "HIS");
    private static final Set<String> NEGATIVE = Set.of("ASP", "GLU");
    private static final Set<String> AROMATIC =
            Set.of("PHE", "TYR", "TRP", "HIS");

    @Override
    public PocketAnalysisResult analyze(
            Pocket pocket,
            Structure structure,
            PocketAnalysisResult geometry,
            PocketReportConfiguration configuration
    ) {
        List<PocketAnalysisSupport.ResolvedResidue> resolved =
                PocketAnalysisSupport.resolve(
                pocket,
                structure
        );
        Point3D centroid = centroid(geometry);
        Map<ResidueCategory, Integer> categoryCounts =
                new EnumMap<>(ResidueCategory.class);
        Map<String, Integer> aminoAcidCounts = new LinkedHashMap<>();
        List<Map<String, Object>> residueRows = new ArrayList<>();

        for (PocketAnalysisSupport.ResolvedResidue resolvedResidue
                : resolved) {
            Residue residue = resolvedResidue.residue();
            String name = residue.getName().toUpperCase(Locale.ROOT);
            Set<ResidueCategory> categories = categories(name);
            categories.forEach(category -> categoryCounts.merge(
                    category,
                    1,
                    Integer::sum
            ));
            aminoAcidCounts.merge(name, 1, Integer::sum);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("chain", resolvedResidue.chainId());
            row.put("residueNumber", residue.getNumber());
            row.put("residueName", name);
            row.put("categories", categories.stream()
                    .map(Enum::name)
                    .sorted()
                    .toList());
            residue.getAlphaCarbonPosition().ifPresent(alphaCarbon -> {
                row.put(
                        "alphaCarbonDistanceToPocketCentroidAngstrom",
                        distance(alphaCarbon, centroid)
                );
            });
            row.put("pocketMember", true);
            residueRows.add(Map.copyOf(row));
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("totalResidues", residueRows.size());
        values.put("categoryCounts", namedCategoryCounts(categoryCounts));
        values.put("aminoAcidCounts", Map.copyOf(aminoAcidCounts));
        values.put("residues", List.copyOf(residueRows));

        ReportEvidence inventory = new ReportEvidence(
                "R-001",
                EvidenceCategory.RESIDUE_COMPOSITION,
                "The source-defined pocket contains "
                        + residueRows.size() + " residues.",
                Map.of("totalResidues", (double) residueRows.size())
        );
        ReportEvidence composition = new ReportEvidence(
                "R-002",
                EvidenceCategory.RESIDUE_COMPOSITION,
                compositionStatement(categoryCounts),
                categoryMetrics(categoryCounts)
        );
        return new PocketAnalysisResult(
                values,
                List.of(inventory, composition)
        );
    }

    private Point3D centroid(PocketAnalysisResult geometry) {
        Object centroidValue = geometry.values().get("centroid");
        if (!(centroidValue instanceof Map<?, ?> centroid)) {
            throw new IllegalArgumentException(
                    "Geometry analysis has no centroid");
        }
        return new Point3D(
                number(centroid, "x"),
                number(centroid, "y"),
                number(centroid, "z")
        );
    }

    private double number(Map<?, ?> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Geometry centroid has no numeric " + key);
        }
        return number.doubleValue();
    }

    private Set<ResidueCategory> categories(String name) {
        EnumSet<ResidueCategory> categories =
                EnumSet.noneOf(ResidueCategory.class);
        addIf(categories, HYDROPHOBIC, name, ResidueCategory.HYDROPHOBIC);
        addIf(categories, POLAR, name, ResidueCategory.POLAR);
        addIf(categories, POSITIVE, name,
                ResidueCategory.POSITIVELY_CHARGED);
        addIf(categories, NEGATIVE, name,
                ResidueCategory.NEGATIVELY_CHARGED);
        addIf(categories, AROMATIC, name, ResidueCategory.AROMATIC);
        if ("CYS".equals(name)) {
            categories.add(ResidueCategory.CYSTEINE);
        }
        if ("GLY".equals(name)) {
            categories.add(ResidueCategory.GLYCINE);
        }
        if ("PRO".equals(name)) {
            categories.add(ResidueCategory.PROLINE);
        }
        if (categories.isEmpty()) {
            categories.add(ResidueCategory.OTHER);
        }
        return Set.copyOf(categories);
    }

    private void addIf(
            Set<ResidueCategory> categories,
            Set<String> names,
            String name,
            ResidueCategory category
    ) {
        if (names.contains(name)) {
            categories.add(category);
        }
    }

    private Map<String, Integer> namedCategoryCounts(
            Map<ResidueCategory, Integer> counts
    ) {
        Map<String, Integer> named = new LinkedHashMap<>();
        for (ResidueCategory category : ResidueCategory.values()) {
            named.put(category.name(), counts.getOrDefault(category, 0));
        }
        return Map.copyOf(named);
    }

    private Map<String, Double> categoryMetrics(
            Map<ResidueCategory, Integer> counts
    ) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        counts.forEach((category, count) ->
                metrics.put(
                        category.name().toLowerCase(Locale.ROOT) + "Count",
                        count.doubleValue()
                ));
        return Map.copyOf(metrics);
    }

    private String compositionStatement(
            Map<ResidueCategory, Integer> counts
    ) {
        return "The pocket residue inventory includes "
                + counts.getOrDefault(ResidueCategory.HYDROPHOBIC, 0)
                + " hydrophobic, "
                + counts.getOrDefault(ResidueCategory.AROMATIC, 0)
                + " aromatic, "
                + counts.getOrDefault(ResidueCategory.POLAR, 0)
                + " polar, and "
                + counts.getOrDefault(ResidueCategory.CYSTEINE, 0)
                + " cysteine residues.";
    }

    private double distance(Point3D first, Point3D second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
