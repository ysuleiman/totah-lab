package totah.lab.docking.flex;

import totah.lab.protein.Residue;
import totah.lab.topology.SideChainRotamers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FlexResidueSelector {

    public Map<String, Residue> resolve(List<Residue> residues, List<String> entries) {
        Map<String, Residue> result = new LinkedHashMap<>();
        if (entries == null) return result;

        for (String entry : entries) {
            String trimmed = entry.trim();
            String[] parts = trimmed.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "flex_residues entry '" + entry + "' must have format \"A:123\"");
            }
            String chain = parts[0].trim();
            ResidueId residueId = parseResidueId(entry, parts[1].trim());

            Residue match = null;
            for (Residue residue : residues) {
                if (chain.equals(residue.getChain())
                        && residueId.matches(residue)) {
                    match = residue;
                    break;
                }
            }
            if (match == null) {
                throw new IllegalArgumentException(
                        "flex_residues entry '" + entry + "' does not match a loaded residue");
            }
            if (!SideChainRotamers.isStandardAminoAcid(match.getName())) {
                throw new IllegalArgumentException(
                        "flex_residues entry '" + entry + "' (" + match.getName()
                                + ") is not a standard amino acid - flexible HETATM is not supported");
            }
            result.put(trimmed, match);
        }
        return result;
    }

    private ResidueId parseResidueId(String entry, String value) {
        int split = 0;
        while (split < value.length() && Character.isDigit(value.charAt(split))) {
            split++;
        }
        if (split == 0) {
            throw new IllegalArgumentException(
                    "flex_residues entry '" + entry + "' has a non-numeric residue number");
        }
        String insertion = value.substring(split).trim();
        if (insertion.length() > 1) {
            throw new IllegalArgumentException(
                    "flex_residues entry '" + entry + "' has an invalid insertion code");
        }
        int number = Integer.parseInt(value.substring(0, split));
        return new ResidueId(number, insertion.isEmpty() ? ' ' : insertion.charAt(0));
    }

    private record ResidueId(int number, char insertionCode) {
        boolean matches(Residue residue) {
            char residueInsertion = residue.getInsertionCode() == null ? ' ' : residue.getInsertionCode();
            return number == residue.getNumber() && insertionCode == residueInsertion;
        }
    }
}
