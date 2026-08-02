package totah.lab.hephaestus.receptor;

import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.preparation.OperationResult;

@FunctionalInterface
public interface ReceptorPreparationOperation {

    OperationResult<PreparedProtein> apply(
            PreparedProtein preparedProtein,
            ReceptorPreparationOptions options);
}
