package totah.lab.athena.design.feature;

import totah.lab.gaia.geometry.Point3D;

import java.util.Map;
import java.util.Set;

/** A target-neutral chemical feature perceived on a molecular graph/conformer. */
public record LigandFeature(String id, Type type, Set<String> atomIds,
                            Point3D point, Map<String, String> evidence) {
    public LigandFeature {
        atomIds = Set.copyOf(atomIds);
        evidence = Map.copyOf(evidence);
        if (id == null || id.isBlank() || type == null || atomIds.isEmpty()) {
            throw new IllegalArgumentException("feature identity, type and atoms are required");
        }
    }

    public enum Type {
        H_BOND_DONOR, H_BOND_ACCEPTOR, HYDROPHOBE, AROMATIC_RING,
        POSITIVE_CENTER, NEGATIVE_CENTER, PI_FEATURE, HALOGEN, CUSTOM
    }
}
