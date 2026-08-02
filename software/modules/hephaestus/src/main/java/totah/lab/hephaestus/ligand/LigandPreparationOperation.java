package totah.lab.hephaestus.ligand;

import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.preparation.OperationResult;

@FunctionalInterface
public interface LigandPreparationOperation {

    OperationResult<PreparedLigand> apply(
            PreparedLigand preparedLigand,
            LigandPreparationOptions options);
}
