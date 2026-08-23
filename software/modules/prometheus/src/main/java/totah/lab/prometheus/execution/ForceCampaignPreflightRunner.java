package totah.lab.prometheus.execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.EvidenceProvenance;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.identity.CanonicalAtomMap;
import totah.lab.prometheus.identity.GeometryIdentity;
import totah.lab.prometheus.ingest.LegacyCanonicalAtomLoader;
import totah.lab.prometheus.planning.CalculationSpecification;
import totah.lab.prometheus.planning.CostEstimate;
import totah.lab.prometheus.planning.DatasetRole;
import totah.lab.prometheus.recovery.ArtifactChecksums;
import totah.lab.prometheus.store.CanonicalEvidenceStore;
import totah.lab.prometheus.store.GeneratedEvidenceRegistry;
import totah.lab.prometheus.store.GeneratedEvidenceRole;

/** Builds and resolves the frozen 36-target force-campaign manifest without executing QM. */
public final class ForceCampaignPreflightRunner {

    private static final List<String> OUTPUTS = List.of(
            "absolute energy in hartree", "gradient in hartree/bohr", "forces in hartree/bohr");
    private ForceCampaignPreflightRunner() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("usage: <repository-root> <campaign-root>");
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path campaign = Path.of(args[1]).toAbsolutePath().normalize();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode geometryManifest = mapper.readTree(campaign.resolve("GEOMETRY_GENERATION_MANIFEST.json").toFile());
        CanonicalAtomMap atomMap = LegacyCanonicalAtomLoader.load(
                root.resolve("analysis/mettl7-phase2/execution-unit-02"));
        QmProtocol protocol = new QmProtocol("PBE", "def2-SVP", "D3(BJ)",
                "density-fitted gas phase", false, "PySCF", "2.14.0");
        var canonical = new CanonicalEvidenceStore().loadCurrent(
                root.resolve("analysis/prometheus/evidence-store")).index();
        GeneratedEvidenceRegistry generated = new GeneratedEvidenceRegistry(
                root.resolve("analysis/prometheus/generated-evidence-registry"));

        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("campaign_id", "PROMETHEUS_COMMON_PROTOCOL_FORCE_CAMPAIGN_36");
        manifest.put("created_at", Instant.now().toString());
        manifest.put("target_count", 36);
        manifest.put("holdout_sealed", true);
        manifest.put("execution_authorized", true);
        manifest.put("forcebalance_authorized", false);
        manifest.put("canonical_atom_map_hash", atomMap.canonicalHash());
        ArrayNode targets = manifest.putArray("targets");
        List<String> csv = new ArrayList<>();
        csv.add("target_id,parent_minimum,source_kind,geometry_path,geometry_file_sha256,geometry_identity,scientific_identity,specification_checksum,resolution,reuse_source");
        Set<String> identities = new HashSet<>();
        int reusable = 0;
        int generate = 0;
        int index = 0;
        for (JsonNode record : geometryManifest.path("records")) {
            index++;
            Path geometryPath = Path.of(record.path("path").asText()).toAbsolutePath().normalize();
            GeometryIdentity geometry = identity(atomMap, geometryPath);
            CalculationSpecification spec = specification(index, atomMap, geometry, protocol);
            EvidenceIdentity evidenceIdentity = new EvidenceIdentity(atomMap.molecule(), atomMap.canonicalHash(),
                    geometry, 0, 1, CalculationType.FORCE_EVALUATION, protocol, List.of(), OUTPUTS);
            if (!identities.add(evidenceIdentity.evidenceHash())) {
                throw new IOException("duplicate scientific target identity: " + evidenceIdentity.evidenceHash());
            }
            String resolution;
            String reuseSource = "";
            Optional<QuantumEvidence> existing = generated.reusable(evidenceIdentity.evidenceHash());
            if (existing.isEmpty()
                    && record.path("parent_minimum").asText().equals("MIN02")
                    && record.path("source_kind").asText().equals("VERIFIED_MINIMUM")) {
                Path sourceBase = root.resolve("analysis/mettl7-phase2/execution-unit-05O/qm-native-minima/MIN02");
                QuantumEvidence recovered = authoritativeEnergyGradientAlias(
                        mapper, sourceBase, evidenceIdentity);
                generated.register(spec.checksum(), recovered, GeneratedEvidenceRole.PRIMARY, sourceBase,
                        artifacts(sourceBase),
                        "authoritative MIN02 energy and full Cartesian gradient promoted for exact reuse");
                existing = generated.reusable(evidenceIdentity.evidenceHash());
            }
            if (existing.isEmpty()) {
                existing = compatibleCanonical(canonical.quantum(), evidenceIdentity);
                if (existing.isPresent()) {
                    QuantumEvidence alias = alias(existing.get(), evidenceIdentity);
                    Path base = Path.of(existing.get().provenance().sourcePath()).toAbsolutePath().getParent();
                    generated.register(spec.checksum(), alias, GeneratedEvidenceRole.PRIMARY, base,
                            artifacts(base), "scientifically equivalent accepted canonical result promoted for exact reuse");
                    existing = generated.reusable(evidenceIdentity.evidenceHash());
                }
            }
            if (existing.isPresent()) {
                resolution = "REUSE_EXISTING";
                reuseSource = existing.get().provenance().sourcePath();
                reusable++;
            } else {
                resolution = "GENERATE_NEW";
                generate++;
            }
            ObjectNode target = targets.addObject();
            target.put("target_id", spec.specificationId());
            target.put("parent_minimum", record.path("parent_minimum").asText());
            target.put("source_kind", record.path("source_kind").asText());
            target.put("geometry_path", geometryPath.toString());
            target.put("geometry_file_sha256", ArtifactChecksums.sha256(geometryPath));
            target.put("geometry_identity", geometry.sha256());
            target.put("scientific_identity", evidenceIdentity.evidenceHash());
            target.put("specification_checksum", spec.checksum());
            target.put("resolution", resolution);
            target.put("reuse_source", reuseSource);
            target.put("formal_charge", 0);
            target.put("multiplicity", 1);
            target.put("protocol", protocol.protocolKey());
            target.putPOJO("requested_outputs", OUTPUTS);
            csv.add(String.join(",", List.of(spec.specificationId(), record.path("parent_minimum").asText(),
                    record.path("source_kind").asText(), geometryPath.toString(),
                    ArtifactChecksums.sha256(geometryPath), geometry.sha256(), evidenceIdentity.evidenceHash(),
                    spec.checksum(), resolution, reuseSource)));
        }
        if (index != 36 || reusable + generate != 36) throw new IOException("preflight count invariant failed");
        manifest.put("reuse_existing_count", reusable);
        manifest.put("generate_new_count", generate);
        Path manifestPath = campaign.resolve("FORCE_CAMPAIGN_36_TARGET_MANIFEST.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);
        Files.write(campaign.resolve("FORCE_CAMPAIGN_36_TARGET_RESOLUTION.csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(campaign.resolve("PREFLIGHT_SHA256SUMS"),
                ArtifactChecksums.sha256(campaign.resolve("GEOMETRY_GENERATION_MANIFEST.json"))
                        + "  GEOMETRY_GENERATION_MANIFEST.json\n"
                        + ArtifactChecksums.sha256(manifestPath) + "  FORCE_CAMPAIGN_36_TARGET_MANIFEST.json\n"
                        + ArtifactChecksums.sha256(campaign.resolve("FORCE_CAMPAIGN_36_TARGET_RESOLUTION.csv"))
                        + "  FORCE_CAMPAIGN_36_TARGET_RESOLUTION.csv\n", StandardCharsets.UTF_8);
    }

    private static QuantumEvidence authoritativeEnergyGradientAlias(
            ObjectMapper mapper, Path sourceBase, EvidenceIdentity identity) throws IOException {
        Path resultPath = sourceBase.resolve("result.json");
        Path gradientPath = sourceBase.resolve("final_gradient_hartree_per_bohr.txt");
        JsonNode result = mapper.readTree(resultPath.toFile());
        double energy = requiredFiniteDouble(result, "energy_hartree");
        List<Double> gradient = new ArrayList<>();
        for (String line : Files.readAllLines(gradientPath)) {
            if (line.isBlank()) continue;
            for (String value : line.trim().split("\\s+")) gradient.add(Double.parseDouble(value));
        }
        if (gradient.size() != identity.geometry().atomCount() * 3) {
            throw new IOException("MIN02 authoritative gradient length mismatch");
        }
        EvidenceProvenance provenance = new EvidenceProvenance(resultPath.toString(),
                ArtifactChecksums.sha256(resultPath), Instant.now(), List.of(),
                "energy from result.json; full gradient from final_gradient_hartree_per_bohr.txt; "
                        + "promoted without QM recomputation");
        return new QuantumEvidence(identity, provenance,
                totah.lab.prometheus.evidence.ConvergenceStatus.CONVERGED,
                EvidenceAcceptanceState.ACCEPTED, Optional.of(energy),
                Optional.of(List.copyOf(gradient)), Optional.empty(), Optional.empty(), Optional.empty(),
                "authoritative MIN02 protocol-control result");
    }

    static double requiredFiniteDouble(JsonNode object, String field) throws IOException {
        JsonNode value = object.get(field);
        if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue())) {
            throw new IOException("missing, non-numeric, or non-finite " + field);
        }
        return value.doubleValue();
    }

    private static Optional<QuantumEvidence> compatibleCanonical(
            List<QuantumEvidence> candidates, EvidenceIdentity requested) {
        return candidates.stream().filter(e -> e.acceptance() == EvidenceAcceptanceState.ACCEPTED)
                .filter(e -> e.energyHartree().isPresent() && e.gradientHartreePerBohr().isPresent())
                .filter(e -> e.identity().molecule().equals(requested.molecule()))
                .filter(e -> e.identity().atomMapHash().equals(requested.atomMapHash()))
                .filter(e -> e.identity().geometry().equals(requested.geometry()))
                .filter(e -> e.identity().formalCharge() == requested.formalCharge()
                        && e.identity().multiplicity() == requested.multiplicity())
                .filter(e -> e.identity().protocol().protocolKey().equals(requested.protocol().protocolKey()))
                .findFirst();
    }

    private static QuantumEvidence alias(QuantumEvidence source, EvidenceIdentity identity) {
        EvidenceProvenance provenance = new EvidenceProvenance(source.provenance().sourcePath(),
                source.provenance().sha256(), Instant.now(),
                List.of(source.identity().evidenceHash()),
                "exact final-geometry energy/gradient reuse from accepted canonical evidence");
        return new QuantumEvidence(identity, provenance, source.convergence(), source.acceptance(),
                source.energyHartree(), source.gradientHartreePerBohr(), Optional.empty(),
                source.dipoleDebye(), source.interactionEnergyKcalMol(), source.convergenceNote());
    }

    private static List<RawArtifact> artifacts(Path base) throws IOException {
        List<RawArtifact> artifacts = new ArrayList<>();
        try (var paths = Files.walk(base)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                artifacts.add(new RawArtifact(base.relativize(path).toString(), ArtifactChecksums.sha256(path),
                        "accepted_canonical_reuse_source"));
            }
        }
        return List.copyOf(artifacts);
    }

    private static CalculationSpecification specification(int index, CanonicalAtomMap map,
            GeometryIdentity geometry, QmProtocol protocol) {
        return new CalculationSpecification(String.format("force-campaign-%02d", index),
                "prospectively frozen QM-native energy/force development target", map.molecule(), geometry,
                0, 1, protocol, List.of(), CalculationType.FORCE_EVALUATION, OUTPUTS,
                List.of("SCF converged", "finite energy and Cartesian gradient", "force equals negative gradient",
                        "atom order and geometry checksum preserved"), DatasetRole.DEVELOPMENT,
                new CostEstimate(1, 1.5, 1.5, 1.5, 0.0));
    }

    private static GeometryIdentity identity(CanonicalAtomMap map, Path xyz) throws IOException {
        List<String> lines = Files.readAllLines(xyz);
        int count = Integer.parseInt(lines.getFirst().trim());
        if (count != map.size()) throw new IOException("atom count mismatch: " + xyz);
        List<Point3D> coordinates = new ArrayList<>();
        for (int atom = 0; atom < count; atom++) {
            String[] fields = lines.get(atom + 2).trim().split("\\s+");
            if (!fields[0].equalsIgnoreCase(map.atoms().get(atom).elementSymbol())) {
                throw new IOException("atom-order mismatch in " + xyz + " at " + (atom + 1));
            }
            coordinates.add(new Point3D(Double.parseDouble(fields[1]), Double.parseDouble(fields[2]),
                    Double.parseDouble(fields[3])));
        }
        return GeometryIdentity.of(map, coordinates);
    }
}
