package totah.lab.hephaestus.receptor;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ReceptorPreparerBuilder {

    private final List<ReceptorPreparationOperation> operations =
            new ArrayList<>();

    public ReceptorPreparerBuilder add(
            ReceptorPreparationOperation operation) {

        operations.add(
                Objects.requireNonNull(
                        operation,
                        "operation"));

        return this;
    }

    public ReceptorPreparerBuilder addAll(
            List<? extends ReceptorPreparationOperation> operations) {

        Objects.requireNonNull(operations, "operations");

        operations.forEach(this::add);

        return this;
    }

    public ReceptorPreparer build() {
        return new DefaultReceptorPreparer(operations);
    }
}
