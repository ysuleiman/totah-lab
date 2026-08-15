package totah.lab.prometheus.ingest.authoritative;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Comparator;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import totah.lab.prometheus.recovery.ArtifactChecksums;
import totah.lab.prometheus.recovery.FieldSourceProvenance;
import totah.lab.prometheus.recovery.RecoveredField;
import totah.lab.prometheus.recovery.RecoveryClassification;

/** Authoritative reader for the frozen 05O PySCF counterpoise probe calculations. */
public final class AuthoritativeProbeReader {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern SCF_ENERGY = Pattern.compile("converged SCF energy =\\s*([-+0-9.Ee]+)");
    private static final Pattern PYSCF_VERSION = Pattern.compile("PySCF version\\s+(.+)");
    private static final Pattern INPUT_INTEGER = Pattern.compile("\\[INPUT] (charge|spin \\(= nelec alpha-beta = 2S\\)) =\\s*(-?\\d+)");
    private static final Pattern BASIS = Pattern.compile("basis =\\s*(\\S+)");
    private static final Pattern XC = Pattern.compile("XC library .* version\\s+(\\S+)");
    private static final Pattern HARTREE_CONVERSION = Pattern.compile(".*H2K=([-+0-9.Ee]+).*");

    /**
     * Reads a complete sharded probe run. Point discovery is based on the
     * authoritative artifact set, while scientific identity comes from result JSON.
     */
    public List<AuthoritativeProbeRecord> readShardedDataset(Path shardsRoot, Path geometryAudit)
            throws IOException {
        Objects.requireNonNull(shardsRoot, "shardsRoot");
        try (Stream<Path> files = Files.walk(shardsRoot, 3)) {
            List<Path> pointDirectories = files.filter(path -> path.getFileName().toString().equals("result.json"))
                    .map(Path::getParent)
                    .filter(path -> Files.isRegularFile(path.resolve("dimer.log")))
                    .filter(path -> Files.isRegularFile(path.resolve("tsl_ghost_probe.log")))
                    .filter(path -> Files.isRegularFile(path.resolve("probe_ghost_tsl.log")))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            List<AuthoritativeProbeRecord> records = new ArrayList<>();
            for (Path point : pointDirectories) {
                Path environment = point.getParent().resolve("software_environment.json");
                records.add(read(point, environment, geometryAudit));
            }
            return List.copyOf(records);
        }
    }

    public AuthoritativeProbeRecord read(Path pointDirectory, Path softwareEnvironment, Path geometryAudit)
            throws IOException {
        Objects.requireNonNull(pointDirectory, "pointDirectory");
        Path result = required(pointDirectory.resolve("result.json"));
        Path geometry = required(pointDirectory.resolve("geometry.xyz"));
        Path dimerLog = required(pointDirectory.resolve("dimer.log"));
        Path tslLog = required(pointDirectory.resolve("tsl_ghost_probe.log"));
        Path probeLog = required(pointDirectory.resolve("probe_ghost_tsl.log"));
        required(softwareEnvironment);
        required(geometryAudit);

        JsonNode out = JSON.readTree(result.toFile());
        JsonNode environment = JSON.readTree(softwareEnvironment.toFile());
        String pointId = text(out, "point_id", result);
        Map<String, AuditRow> auditRows = readAudit(geometryAudit);
        AuditRow audit = auditRows.get(pointId);
        if (audit == null) {
            throw new IOException("point absent from geometry audit: " + pointId);
        }

        LogData dimer = readLog(dimerLog);
        LogData tsl = readLog(tslLog);
        LogData probe = readLog(probeLog);
        requireSameProtocol(dimer, tsl, probe, pointId);
        double dimerExact = number(out, "electronic_dimer_hartree", result);
        double tslExact = number(out, "electronic_tsl_in_dimer_basis_hartree", result);
        double probeExact = number(out, "electronic_probe_in_dimer_basis_hartree", result);
        verifyJsonEnergy(dimerExact, dimer.energy(), "electronic_dimer_hartree", result);
        verifyJsonEnergy(tslExact, tsl.energy(), "electronic_tsl_in_dimer_basis_hartree", result);
        verifyJsonEnergy(probeExact, probe.energy(), "electronic_probe_in_dimer_basis_hartree", result);

        double d3 = number(out, "d3_interaction_hartree", result);
        double reconstructed = (dimerExact - tslExact - probeExact + d3) * dimer.hartreeToKcalMol();
        double serialized = number(out, "qm_cp_pbe0_d3bj_def2tzvp_kcal_mol", result);
        if (Math.abs(reconstructed - serialized) > 1.0e-8) {
            throw new IOException("counterpoise interaction energy reconstruction mismatch: " + pointId);
        }
        String geometryHash = ArtifactChecksums.sha256(geometry);
        if (!geometryHash.equalsIgnoreCase(text(out, "geometry_sha256", result))
                || !geometryHash.equalsIgnoreCase(audit.geometrySha256())) {
            throw new IOException("geometry checksum mismatch across raw result/audit: " + pointId);
        }

        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("pyscf", text(environment, "pyscf", softwareEnvironment));
        versions.put("dftd3", text(environment, "dftd3", softwareEnvironment));
        versions.put("python", text(environment, "python", softwareEnvironment));
        versions.put("libxc", dimer.libxcVersion());
        if (!dimer.pyscfVersion().equals(versions.get("pyscf"))) {
            throw new IOException("PySCF version differs between environment and SCF log: " + pointId);
        }

        List<String> notes = List.of(
                "three counterpoise electronic energies independently parsed from converged SCF log lines",
                "reconstructed-vs-result interaction energy difference=" + Math.abs(reconstructed - serialized)
                        + " kcal/mol",
                "geometry SHA-256 agrees across geometry bytes, result JSON and geometry audit");
        List<RawValueDiscrepancy> discrepancies = new ArrayList<>();
        addRawDiscrepancy(discrepancies, "electronic_dimer_hartree", dimerExact, result,
                "/electronic_dimer_hartree", dimer.energy(), dimerLog, "line " + dimer.energyLine());
        addRawDiscrepancy(discrepancies, "electronic_tsl_in_dimer_basis_hartree", tslExact, result,
                "/electronic_tsl_in_dimer_basis_hartree", tsl.energy(), tslLog, "line " + tsl.energyLine());
        addRawDiscrepancy(discrepancies, "electronic_probe_in_dimer_basis_hartree", probeExact, result,
                "/electronic_probe_in_dimer_basis_hartree", probe.energy(), probeLog,
                "line " + probe.energyLine());

        return new AuthoritativeProbeRecord(
                raw("point_id", pointId, result, "/point_id"),
                raw("minimum_id", text(out, "minimum_id", result), result, "/minimum_id"),
                raw("interaction_class", text(out, "site", result), result, "/site"),
                raw("target_distance_angstrom", number(out, "distance_A", result), result, "/distance_A"),
                raw("formal_charge", dimer.charge(), dimerLog, "line " + dimer.chargeLine()),
                raw("multiplicity", dimer.spin() + 1, dimerLog, "line " + dimer.spinLine()),
                raw("electronic_structure_method",
                        "CP-PBE0-D3(BJ)/" + dimer.basis() + "; density-fitted RKS; grid level 3",
                        dimerLog, "input script lines and PySCF input block",
                        source(softwareEnvironment, "/method", "JSON structured field")),
                environment("software_versions", Map.copyOf(versions), softwareEnvironment, "entire JSON object",
                        source(dimerLog, "lines " + dimer.versionLine() + " and " + dimer.libxcLine(),
                                "PySCF log fields")),
                raw("electronic_dimer_hartree", dimerExact, result, "/electronic_dimer_hartree",
                        source(dimerLog, "line " + dimer.energyLine(), "rounded SCF output corroboration")),
                raw("electronic_tsl_in_dimer_basis_hartree", tslExact, result,
                        "/electronic_tsl_in_dimer_basis_hartree",
                        source(tslLog, "line " + tsl.energyLine(), "rounded SCF output corroboration")),
                raw("electronic_probe_in_dimer_basis_hartree", probeExact, result,
                        "/electronic_probe_in_dimer_basis_hartree",
                        source(probeLog, "line " + probe.energyLine(), "rounded SCF output corroboration")),
                raw("d3_interaction_hartree", d3, result, "/d3_interaction_hartree"),
                derived("interaction_energy_kcal_mol", reconstructed,
                        List.of(source(result, "/electronic_dimer_hartree", "JSON structured field"),
                                source(result, "/electronic_tsl_in_dimer_basis_hartree", "JSON structured field"),
                                source(result, "/electronic_probe_in_dimer_basis_hartree", "JSON structured field"),
                                source(result, "/d3_interaction_hartree", "JSON structured field"),
                                source(dimerLog, "line " + dimer.conversionLine(), "input-script constant")),
                        "CP electronic difference plus D3 interaction, converted with the archived H2K constant"),
                raw("scf_converged", true, dimerLog, "line " + dimer.energyLine(),
                        source(tslLog, "line " + tsl.energyLine(), "SCF converged-energy marker"),
                        source(probeLog, "line " + probe.energyLine(), "SCF converged-energy marker")),
                raw("geometry_sha256", geometryHash, geometry, "entire file",
                        source(result, "/geometry_sha256", "JSON structured field"),
                        source(geometryAudit, "row point_id=" + pointId + ", column geometry_sha256", "CSV field")),
                raw("geometry_classification", audit.classification(), geometryAudit,
                        "row point_id=" + pointId + ", column geometry_classification"),
                raw("validation_eligibility", audit.eligibility(), geometryAudit,
                        "row point_id=" + pointId + ", column force_field_validation_eligibility"),
                raw("closest_non_target_distance_angstrom", audit.closestDistance(), geometryAudit,
                        "row point_id=" + pointId + ", column closest_non_target_distance_A"),
                raw("closest_non_target_vdw_overlap_angstrom", audit.overlap(), geometryAudit,
                        "row point_id=" + pointId + ", column vdW_overlap_A"),
                discrepancies,
                notes);
    }

    public List<HistoricalValueComparison> compareHistoricalInteractionEnergies(
            List<AuthoritativeProbeRecord> records, Path historicalCsv, double tolerance) throws IOException {
        List<Map<String, String>> rows = readCsv(historicalCsv);
        Map<String, Map<String, String>> byId = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            byId.put(row.get("point_id"), row);
        }
        String checksum = ArtifactChecksums.sha256(historicalCsv);
        List<HistoricalValueComparison> comparisons = new ArrayList<>();
        for (AuthoritativeProbeRecord record : records) {
            String id = record.pointId().value().orElseThrow();
            Map<String, String> row = byId.get(id);
            if (row == null) {
                throw new IOException("historical table missing point " + id);
            }
            double historical = Double.parseDouble(row.get("qm_interaction_energy"));
            double recovered = record.interactionEnergyKcalMol().value().orElseThrow();
            double difference = Math.abs(recovered - historical);
            comparisons.add(new HistoricalValueComparison(id, "qm_interaction_energy_kcal_mol", recovered,
                    historical, difference, difference <= tolerance, historicalCsv.toString(), checksum,
                    "row point_id=" + id + ", column qm_interaction_energy"));
        }
        return List.copyOf(comparisons);
    }

    private static LogData readLog(Path log) throws IOException {
        List<String> lines = Files.readAllLines(log);
        Double energy = null;
        int energyLine = -1, charge = Integer.MIN_VALUE, chargeLine = -1, spin = Integer.MIN_VALUE, spinLine = -1;
        String version = null, basis = null, libxc = null;
        Double conversion = null;
        int conversionLine = -1;
        int versionLine = -1, libxcLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher energyMatcher = SCF_ENERGY.matcher(line);
            if (energyMatcher.find()) {
                energy = Double.parseDouble(energyMatcher.group(1));
                energyLine = i + 1;
            }
            Matcher versionMatcher = PYSCF_VERSION.matcher(line);
            if (versionMatcher.find()) {
                version = versionMatcher.group(1).trim();
                versionLine = i + 1;
            }
            Matcher integerMatcher = INPUT_INTEGER.matcher(line);
            if (integerMatcher.find()) {
                if (integerMatcher.group(1).equals("charge")) {
                    charge = Integer.parseInt(integerMatcher.group(2));
                    chargeLine = i + 1;
                } else {
                    spin = Integer.parseInt(integerMatcher.group(2));
                    spinLine = i + 1;
                }
            }
            Matcher basisMatcher = BASIS.matcher(line);
            if (basisMatcher.matches()) {
                basis = basisMatcher.group(1);
            }
            Matcher xcMatcher = XC.matcher(line);
            if (xcMatcher.find()) {
                libxc = xcMatcher.group(1);
                libxcLine = i + 1;
            }
            Matcher conversionMatcher = HARTREE_CONVERSION.matcher(line);
            if (conversionMatcher.matches()) {
                conversion = Double.parseDouble(conversionMatcher.group(1));
                conversionLine = i + 1;
            }
        }
        if (energy == null || version == null || basis == null || libxc == null
                || conversion == null || charge == Integer.MIN_VALUE || spin == Integer.MIN_VALUE) {
            throw new IOException("incomplete PySCF log: " + log);
        }
        return new LogData(energy, energyLine, charge, chargeLine, spin, spinLine, version, versionLine,
                basis, libxc, libxcLine, conversion, conversionLine);
    }

    private static void requireSameProtocol(LogData a, LogData b, LogData c, String id) throws IOException {
        if (a.charge() != b.charge() || a.charge() != c.charge()
                || a.spin() != b.spin() || a.spin() != c.spin()
                || !a.basis().equals(b.basis()) || !a.basis().equals(c.basis())
                || a.hartreeToKcalMol() != b.hartreeToKcalMol()
                || a.hartreeToKcalMol() != c.hartreeToKcalMol()
                || !a.pyscfVersion().equals(b.pyscfVersion()) || !a.pyscfVersion().equals(c.pyscfVersion())) {
            throw new IOException("counterpoise component protocols differ: " + id);
        }
    }

    private static void verifyJsonEnergy(double exactJson, double roundedLog, String field, Path result)
            throws IOException {
        if (Math.abs(exactJson - roundedLog) > 1.0e-10) {
            throw new IOException("SCF log/result mismatch for " + field + " in " + result);
        }
    }

    private static void addRawDiscrepancy(List<RawValueDiscrepancy> discrepancies, String field,
            double exact, Path exactPath, String exactLocator, double rounded, Path roundedPath,
            String roundedLocator) throws IOException {
        double difference = Math.abs(exact - rounded);
        if (difference > 0.0) {
            discrepancies.add(new RawValueDiscrepancy(field, exact, rounded, difference,
                    source(exactPath, exactLocator, "JSON structured field"),
                    source(roundedPath, roundedLocator, "rounded PySCF SCF output")));
        }
    }

    private static Map<String, AuditRow> readAudit(Path csv) throws IOException {
        Map<String, AuditRow> result = new LinkedHashMap<>();
        for (Map<String, String> row : readCsv(csv)) {
            String id = row.get("point_id");
            result.put(id, new AuditRow(row.get("geometry_classification"),
                    row.get("force_field_validation_eligibility"),
                    Double.parseDouble(row.get("closest_non_target_distance_A")),
                    Double.parseDouble(row.get("vdW_overlap_A")), row.get("geometry_sha256")));
        }
        return result;
    }

    private static List<Map<String, String>> readCsv(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv);
        if (lines.isEmpty()) {
            throw new IOException("empty CSV: " + csv);
        }
        String[] header = lines.getFirst().split(",", -1);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            String[] values = lines.get(i).split(",", -1);
            if (values.length != header.length) {
                throw new IOException("quoted or malformed CSV row unsupported at line " + (i + 1) + ": " + csv);
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < header.length; column++) {
                row.put(header[column], values[column]);
            }
            rows.add(row);
        }
        return rows;
    }

    private static String text(JsonNode node, String field, Path source) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isValueNode() || value.asText().isBlank()) {
            throw new IOException("missing field " + field + " in " + source);
        }
        return value.asText();
    }

    private static double number(JsonNode node, String field, Path source) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IOException("missing numeric field " + field + " in " + source);
        }
        return value.doubleValue();
    }

    private static Path required(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("required raw artifact missing: " + path);
        }
        return path;
    }

    private static <T> RecoveredField<T> raw(String name, T value, Path path, String locator,
            FieldSourceProvenance... extra) throws IOException {
        List<FieldSourceProvenance> provenance = new ArrayList<>();
        provenance.add(source(path, locator, "authoritative raw artifact field"));
        provenance.addAll(Arrays.asList(extra));
        return new RecoveredField<>(name, Optional.of(value), RecoveryClassification.RECOVERABLE_FROM_RAW_ARTIFACT,
                provenance, "Recovered from authoritative calculation or geometry-audit artifact");
    }

    private static <T> RecoveredField<T> environment(String name, T value, Path path, String locator,
            FieldSourceProvenance... extra) throws IOException {
        List<FieldSourceProvenance> provenance = new ArrayList<>();
        provenance.add(source(path, locator, "software environment structured field"));
        provenance.addAll(Arrays.asList(extra));
        return new RecoveredField<>(name, Optional.of(value),
                RecoveryClassification.RECOVERABLE_FROM_SOFTWARE_ENVIRONMENT_ARTIFACT,
                provenance, "Recovered from software/environment artifacts");
    }

    private static <T> RecoveredField<T> derived(String name, T value,
            List<FieldSourceProvenance> provenance, String rationale) {
        return new RecoveredField<>(name, Optional.of(value), RecoveryClassification.DERIVABLE,
                provenance, rationale);
    }

    private static FieldSourceProvenance source(Path path, String locator, String method) throws IOException {
        return new FieldSourceProvenance(path.toString(), ArtifactChecksums.sha256(path), locator, method);
    }

    private record LogData(double energy, int energyLine, int charge, int chargeLine, int spin, int spinLine,
            String pyscfVersion, int versionLine, String basis, String libxcVersion, int libxcLine,
            double hartreeToKcalMol, int conversionLine) {
    }

    private record AuditRow(String classification, String eligibility, double closestDistance, double overlap,
            String geometrySha256) {
    }
}
