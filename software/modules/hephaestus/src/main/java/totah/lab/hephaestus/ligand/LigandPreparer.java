package totah.lab.hephaestus.ligand;

@FunctionalInterface
public interface LigandPreparer {
    LigandPreparationResult prepare(
            LigandPreparationRequest request);
}
