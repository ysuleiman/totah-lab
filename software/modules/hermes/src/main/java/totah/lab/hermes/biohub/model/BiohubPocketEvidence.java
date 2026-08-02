package totah.lab.hermes.biohub.model;

import java.util.List;
import java.util.Objects;

public record BiohubPocketEvidence(
        String ligandCcd,
        String model,
        double shellCutoff,
        double directContactCutoff,
        Double ptm,
        Double interfacePtm,
        List<ResidueContact> residues
) {

    public BiohubPocketEvidence {
        ligandCcd = Objects.requireNonNull(ligandCcd, "ligandCcd");
        model = Objects.requireNonNull(model, "model");
        residues = List.copyOf(residues);
    }

    public record ResidueContact(
            String chain,
            int residueNumber,
            String residueName,
            double minimumDistance,
            int contactingAtomPairCount,
            boolean directContact
    ) {
    }
}
