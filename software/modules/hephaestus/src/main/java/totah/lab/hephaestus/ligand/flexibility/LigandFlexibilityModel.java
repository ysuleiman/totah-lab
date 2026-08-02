package totah.lab.hephaestus.ligand.flexibility;

import java.util.List;
import java.util.Objects;

public record LigandFlexibilityModel(
        int atomCount,
        String rootFragmentId,
        List<LigandFragment> fragments) {

    public static final String ATTRIBUTE_KEY =
            LigandFlexibilityModel.class.getName();

    public LigandFlexibilityModel {
        if (atomCount < 1) throw new IllegalArgumentException("atomCount must be positive.");
        Objects.requireNonNull(rootFragmentId, "rootFragmentId");
        fragments = List.copyOf(fragments);
        if (fragments.stream().noneMatch(fragment -> fragment.id().equals(rootFragmentId))) {
            throw new IllegalArgumentException("Root fragment is absent.");
        }
    }

    public int torsionalDegreesOfFreedom() {
        return fragments.size() - 1;
    }
}
