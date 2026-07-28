package totah.lab.ligand.torsion;

import java.util.List;
import java.util.Objects;

public record LigandRotatableBondReport(
        List<RotatableBondClassification> bondClassifications) {

    public LigandRotatableBondReport {
        bondClassifications = List.copyOf(
                Objects.requireNonNull(bondClassifications, "bondClassifications is null"));
    }

    public List<Integer> rotatableBondIndices() {
        return java.util.stream.IntStream.range(0, bondClassifications.size())
                .filter(index -> bondClassifications.get(index)
                        == RotatableBondClassification.ROTATABLE)
                .boxed()
                .toList();
    }

    public int rotatableBondCount() {
        return rotatableBondIndices().size();
    }
}
