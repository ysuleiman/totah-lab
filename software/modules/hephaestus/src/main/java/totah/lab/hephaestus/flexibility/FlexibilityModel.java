package totah.lab.hephaestus.flexibility;

import java.util.List;

public record FlexibilityModel(List<FlexibleResidue> flexibleResidues) {
    public FlexibilityModel { flexibleResidues = List.copyOf(flexibleResidues); }
    public static FlexibilityModel empty() { return new FlexibilityModel(List.of()); }
    public boolean isEmpty() { return flexibleResidues.isEmpty(); }
}
