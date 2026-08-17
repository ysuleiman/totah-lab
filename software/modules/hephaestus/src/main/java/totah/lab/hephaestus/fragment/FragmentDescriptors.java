package totah.lab.hephaestus.fragment;

/** Intrinsic, pose-independent fragment descriptors. */
public record FragmentDescriptors(
        int heavyAtomCount,
        double molecularWeight,
        double calculatedLogP,
        int hydrogenBondDonors,
        int hydrogenBondAcceptors,
        int rotatableBonds,
        int formalCharge,
        int aromaticAtomCount
) {
    public FragmentDescriptors {
        if (heavyAtomCount < 1 || molecularWeight <= 0.0) {
            throw new IllegalArgumentException("A fragment must contain at least one heavy atom and have positive mass");
        }
        if (hydrogenBondDonors < 0 || hydrogenBondAcceptors < 0
                || rotatableBonds < 0 || aromaticAtomCount < 0
                || aromaticAtomCount > heavyAtomCount) {
            throw new IllegalArgumentException("Descriptor counts must be physically consistent");
        }
    }
}
