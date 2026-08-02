package totah.lab.hephaestus.ligand;

public record LigandPreparationOptions(
        boolean addHydrogens,
        boolean generateProtonationStates,
        boolean generateTautomers,
        boolean assignCharges,
        boolean assignAtomTypes,
        boolean generateConformers,
        int maximumConformers) {

    public LigandPreparationOptions {
        if (maximumConformers < 1) {
            throw new IllegalArgumentException(
                    "maximumConformers must be at least 1.");
        }
    }

    public static LigandPreparationOptions defaults() {
        return new LigandPreparationOptions(
                true, false, false, true, true, false, 20);
    }
}
