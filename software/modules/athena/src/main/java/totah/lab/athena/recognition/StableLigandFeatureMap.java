package totah.lab.athena.recognition;

import totah.lab.athena.design.feature.LigandFeature;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Chemistry-derived ligand features keyed by stable, symmetry-aware atom identities. */
public record StableLigandFeatureMap(String ligandId, List<Feature> features,
        String atomIdentityProvenance, String featurePerceptionProvenance,
        EvidenceQuality quality, List<String> limitations) {
    public StableLigandFeatureMap {
        require(ligandId, "ligandId");
        features = List.copyOf(Objects.requireNonNull(features, "features"));
        require(atomIdentityProvenance, "atomIdentityProvenance");
        require(featurePerceptionProvenance, "featurePerceptionProvenance");
        Objects.requireNonNull(quality, "quality");
        limitations = List.copyOf(Objects.requireNonNull(limitations, "limitations"));
        if (features.stream().map(Feature::stableId).distinct().count() != features.size()) {
            throw new IllegalArgumentException("stable feature ids must be unique");
        }
    }

    public Optional<Feature> byStableId(String id) {
        return features.stream().filter(feature -> feature.stableId().equals(id)).findFirst();
    }

    public record Feature(String stableId, LigandFeature.Type type,
            List<String> canonicalAtomOrbits, List<String> sourceFeatureIds,
            EvidenceQuality quality, Map<String, String> provenance) {
        public Feature {
            require(stableId, "stableId");
            Objects.requireNonNull(type, "type");
            canonicalAtomOrbits = canonicalAtomOrbits.stream().sorted().toList();
            if (canonicalAtomOrbits.isEmpty()) throw new IllegalArgumentException("atom orbits required");
            sourceFeatureIds = sourceFeatureIds.stream().sorted().toList();
            if (sourceFeatureIds.isEmpty()) throw new IllegalArgumentException("source features required");
            Objects.requireNonNull(quality, "quality");
            provenance = Map.copyOf(Objects.requireNonNull(provenance, "provenance"));
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
    }
}
