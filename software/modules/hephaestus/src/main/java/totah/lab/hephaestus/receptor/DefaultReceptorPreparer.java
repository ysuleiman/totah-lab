package totah.lab.hephaestus.receptor;

import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.preparation.OperationResult;
import totah.lab.hephaestus.model.PreparationIssue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DefaultReceptorPreparer
        implements ReceptorPreparer {

    private final List<ReceptorPreparationOperation> operations;

    public DefaultReceptorPreparer(
            List<ReceptorPreparationOperation> operations) {

        Objects.requireNonNull(operations, "operations");

        if (operations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "operations must not contain null elements.");
        }

        this.operations = List.copyOf(operations);
    }

    @Override
    public ReceptorPreparationResult prepare(
            ReceptorPreparationRequest request) {

        Objects.requireNonNull(request, "request");

        PreparedProtein current =
                PreparedProtein.of(request.protein());

        List<PreparationIssue> issues = new ArrayList<>();

        for (ReceptorPreparationOperation operation : operations) {
            OperationResult<PreparedProtein> result =
                    Objects.requireNonNull(
                            operation.apply(current, request.options()),
                            "operation returned null");

            current = result.value();
            issues.addAll(result.issues());

            if (result.hasFatalIssue()) {
                break;
            }
        }

        return new ReceptorPreparationResult(current, issues);
    }
}
