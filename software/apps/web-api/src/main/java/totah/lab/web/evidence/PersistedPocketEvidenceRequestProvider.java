package totah.lab.web.evidence;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.athena.pocket.evidence.*;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.daedalus.evidence.PocketEvidenceAssemblyRequest;
import totah.lab.gaia.pocket.*;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.web.persistence.PocketDetailsProjection;
import totah.lab.web.persistence.PocketRepository;
import totah.lab.web.persistence.PocketShapeDescriptorEntity;
import totah.lab.web.persistence.PocketShapeDescriptorRepository;
import totah.lab.web.persistence.PocketResidueProjection;
import totah.lab.web.service.AlphaSphereView;
import totah.lab.web.service.PocketPointCloudLoader;
import totah.lab.web.service.StructureArtifactService;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

/** Builds an assembly request only from facts persisted by the web application. */
@Component
public final class PersistedPocketEvidenceRequestProvider {
    private static final EvidenceMethod FPOCKET =
            new EvidenceMethod("fpocket persisted output", "database-schema-v1");
    private static final EvidenceMethod EXTRACTION =
            new EvidenceMethod("web persisted evidence assembly", "1");

    private final PocketRepository pocketRepository;
    private final PocketShapeDescriptorRepository descriptorRepository;
    private final PocketPointCloudLoader pointCloudLoader;
    private final StructureArtifactService structureArtifactService;

    public PersistedPocketEvidenceRequestProvider(
            PocketRepository pocketRepository,
            PocketShapeDescriptorRepository descriptorRepository,
            PocketPointCloudLoader pointCloudLoader,
            StructureArtifactService structureArtifactService) {
        this.pocketRepository = pocketRepository;
        this.descriptorRepository = descriptorRepository;
        this.pointCloudLoader = pointCloudLoader;
        this.structureArtifactService = structureArtifactService;
    }

    public PocketEvidenceAssemblyRequest load(long pocketId) throws IOException {
        PocketDetailsProjection details = pocketRepository.findPocketDetails(pocketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                        "Pocket not found: " + pocketId));
        if (!PocketSource.FPOCKET.name().equals(details.getSource())) {
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY,
                    "Pocket evidence currently requires an fpocket pocket: " + pocketId);
        }

        Structure structure = structureArtifactService.load(
                details.getStructureArtifactId(),
                details.getStructureArtifactStorageLocation());
        PocketPointCloudLoader.LoadedPointClouds loaded =
                pointCloudLoader.loadAllWithSpheres(List.of(pocketId));
        PocketPointCloud pointCloud = loaded.pointClouds().get(pocketId);
        if (pointCloud == null) {
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY,
                    "Pocket has no persisted geometry: " + pocketId);
        }

        List<PocketResidueProjection> persistedResidues =
                pocketRepository.findResiduesByPocketId(pocketId);
        Pocket pocket = pocket(details, persistedResidues, pointCloud,
                loaded.alphaSpheres().get(pocketId));
        StructureEvidence structureEvidence = structureEvidence(details);
        EvidenceChannel<Map<String, Double>> shape = shape(pocketId);
        Instant extractedAt = Instant.now();
        EvidenceProvenance provenance = new EvidenceProvenance(
                details.getStructureSource(), details.getStructureAccession(),
                "artifact:" + details.getStructureArtifactId(), EXTRACTION,
                extractedAt,
                Map.of("structureArtifactId",
                                Long.toString(details.getStructureArtifactId()),
                        "pocketArtifactId", Long.toString(details.getArtifactId()),
                        "pocketDatabaseId", Long.toString(pocketId)));

        return new PocketEvidenceAssemblyRequest(structureEvidence, structure, pocket,
                new EvidenceChannel.NotEvaluated<>(
                        "bound-component extraction was not persisted"),
                new EvidenceChannel.NotEvaluated<>("CCD chemistry was not loaded"),
                shape, FPOCKET, EXTRACTION, provenance);
    }

    private Pocket pocket(PocketDetailsProjection details,
            List<PocketResidueProjection> residues, PocketPointCloud pointCloud,
            List<AlphaSphereView> spheres) {
        List<PocketMetric> metrics = new ArrayList<>();
        addMetric(metrics, PocketMetricType.FPOCKET_SCORE, details.getScore());
        addMetric(metrics, PocketMetricType.VOLUME, details.getVolume());
        addMetric(metrics, PocketMetricType.FPOCKET_DRUGGABILITY,
                details.getDruggabilityScore());
        List<ResidueId> residueIds = residues.stream().map(value -> new ResidueId(
                value.getChain(), value.getResidueNumber(),
                insertionCode(value.getInsertionCode()))).toList();
        Optional<AlphaSphereSet> alphaSpheres = spheres == null || spheres.isEmpty()
                ? Optional.empty()
                : Optional.of(new AlphaSphereSet(spheres.stream().map(value ->
                        new AlphaSphere(value.index(), value.center(), value.radius()))
                        .toList()));
        return new Pocket(new PocketId("pocket" + details.getPocketNumber()),
                "fpocket " + details.getPocketNumber(), PocketSource.FPOCKET,
                pointCloud.centroid(), residueIds, metrics,
                Optional.of(pointCloud.bounds()), alphaSpheres,
                Map.of("databaseId", Long.toString(details.getId())));
    }

    private StructureEvidence structureEvidence(PocketDetailsProjection details) {
        StructureEvidence.StructureKind kind = structureKind(
                details.getStructureSource());
        boolean predicted = kind == StructureEvidence.StructureKind.PREDICTED;
        EvidenceChannel<String> method = predicted
                ? new EvidenceChannel.NotApplicable<>("predicted structure")
                : new EvidenceChannel.NotEvaluated<>(
                        "experimental method was not persisted");
        EvidenceChannel<Double> resolution = predicted
                ? new EvidenceChannel.NotApplicable<>("predicted structure")
                : new EvidenceChannel.NotEvaluated<>(
                        "experimental resolution was not persisted");
        EvidenceChannel<PredictedModelConfidence> confidence = predicted
                ? new EvidenceChannel.NotEvaluated<>(
                        "provider confidence was not persisted")
                : new EvidenceChannel.NotApplicable<>("experimental structure");
        return new StructureEvidence(details.getStructureAccession(),
                details.getStructureSource(), details.getChain(),
                details.getModelNumber() == null ? 1 : details.getModelNumber(),
                null, kind,
                null, method, resolution, confidence);
    }

    private EvidenceChannel<Map<String, Double>> shape(long pocketId) {
        Optional<PocketShapeDescriptorEntity> stored =
                descriptorRepository.findById(pocketId);
        if (stored.isEmpty()) {
            return new EvidenceChannel.NotEvaluated<>(
                    "shape descriptor was not persisted");
        }
        PocketShapeDescriptorEntity value = stored.get();
        Map<String, Double> representation = new LinkedHashMap<>();
        representation.put("point_count", (double) value.getPointCount());
        representation.put("radius_of_gyration", value.getRadiusOfGyration());
        representation.put("extent_major", value.getExtentMajor());
        representation.put("extent_middle", value.getExtentMiddle());
        representation.put("extent_minor", value.getExtentMinor());
        representation.put("elongation", value.getElongation());
        representation.put("flatness", value.getFlatness());
        double[] histogram = value.getRadialHistogram();
        for (int index = 0; index < histogram.length; index++) {
            representation.put("radial_histogram_" + index, histogram[index]);
        }
        return new EvidenceChannel.Present<>(representation,
                EvidenceOrigin.DERIVED,
                new EvidenceMethod("Athena pocket shape descriptor",
                        Integer.toString(value.getDescriptorVersion())));
    }

    private static void addMetric(List<PocketMetric> metrics,
            PocketMetricType type, Double value) {
        if (value != null) {
            metrics.add(new PocketMetric(type, value));
        }
    }

    private static Character insertionCode(String value) {
        return value == null || value.isBlank() ? null : value.charAt(0);
    }

    private static StructureEvidence.StructureKind structureKind(String source) {
        String normalized = source == null ? "" : source.trim().toUpperCase();
        return switch (normalized) {
            case "RCSB", "PDB" -> StructureEvidence.StructureKind.EXPERIMENTAL;
            case "ALPHAFOLD", "BIOHUB", "ESMFOLD", "ESMFOLD2" ->
                    StructureEvidence.StructureKind.PREDICTED;
            default -> throw new ResponseStatusException(UNPROCESSABLE_ENTITY,
                    "Unsupported structure source for evidence: " + source);
        };
    }
}
