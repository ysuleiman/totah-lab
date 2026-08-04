package totah.lab.athena.pocket.compare.residue;

public record ResidueReference(
        String chainId,
        int residueNumber,
        char insertionCode,
        String residueName
) {
}