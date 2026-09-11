package totah.lab.athena.recognition;

import totah.lab.athena.design.feature.LigandFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Assigns stable feature ids from caller-supplied chemistry-canonical atom orbits. */
public final class StableLigandFeatureMapper {
    public StableLigandFeatureMap map(String ligandId, List<LigandFeature> perceived,
            Map<String, String> sourceAtomToCanonicalOrbit, String atomIdentityProvenance,
            String featurePerceptionProvenance, EvidenceQuality quality,
            Map<String, EvidenceQuality> sourceFeatureQuality,
            List<String> limitations) {
        Objects.requireNonNull(perceived, "perceived");
        Objects.requireNonNull(sourceAtomToCanonicalOrbit, "sourceAtomToCanonicalOrbit");
        Map<String, EvidenceQuality> explicitFeatureQuality = Map.copyOf(
                Objects.requireNonNull(sourceFeatureQuality, "sourceFeatureQuality"));
        Map<Key, List<LigandFeature>> grouped = new LinkedHashMap<>();
        for (LigandFeature feature : perceived) {
            List<String> orbits = feature.atomIds().stream().map(atom -> {
                String orbit = sourceAtomToCanonicalOrbit.get(atom);
                if (orbit == null || orbit.isBlank()) {
                    throw new IllegalArgumentException("no canonical atom orbit for " + atom);
                }
                return orbit;
            }).distinct().sorted().toList();
            grouped.computeIfAbsent(new Key(feature.type(), orbits), ignored -> new ArrayList<>()).add(feature);
        }
        List<StableLigandFeatureMap.Feature> stable = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).map(entry -> {
                    String canonical = entry.getKey().type().name() + ":" + String.join(",", entry.getKey().orbits());
                    String id = "LF-" + sha256(canonical).substring(0, 16);
                    EvidenceQuality featureQuality = entry.getValue().stream()
                            .map(feature -> explicitFeatureQuality.getOrDefault(feature.id(), quality))
                            .max(java.util.Comparator.comparingInt(Enum::ordinal)).orElse(quality);
                    return new StableLigandFeatureMap.Feature(id, entry.getKey().type(),
                            entry.getKey().orbits(), entry.getValue().stream().map(LigandFeature::id).toList(),
                            featureQuality, Map.of("canonical_key", canonical,
                                    "atom_identity", atomIdentityProvenance,
                                    "feature_perception", featurePerceptionProvenance));
                }).toList();
        return new StableLigandFeatureMap(ligandId, stable, atomIdentityProvenance,
                featurePerceptionProvenance, quality, limitations);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record Key(LigandFeature.Type type, List<String> orbits) implements Comparable<Key> {
        private Key { Objects.requireNonNull(type); orbits = List.copyOf(orbits); }
        @Override public int compareTo(Key other) {
            int typeOrder = type.compareTo(other.type);
            return typeOrder != 0 ? typeOrder : String.join("\u0000", orbits)
                    .compareTo(String.join("\u0000", other.orbits));
        }
    }
}
