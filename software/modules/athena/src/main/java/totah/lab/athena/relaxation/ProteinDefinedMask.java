package totah.lab.athena.relaxation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Ligand-independent protein selection. Unlisted atoms and all nonprotein atoms stay fixed. */
public record ProteinDefinedMask(List<Rule> rules, String provenance) {
    public ProteinDefinedMask {
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        if (rules.isEmpty()) throw new IllegalArgumentException("explicit protein rules required");
        provenance = token(provenance);
    }

    /** Empty atomNames applies to every atom of the specified residue role. */
    public record Rule(String chainId, int residueNumber, char insertionCode,
            SystemAtomMapping.ComponentRole role, Set<String> atomNames,
            AtomSelectionRules.Mobility mobility) {
        public Rule {
            chainId = token(chainId);
            Objects.requireNonNull(role, "role");
            atomNames = Set.copyOf(Objects.requireNonNull(atomNames, "atomNames"));
            atomNames.forEach(ProteinDefinedMask::token);
            Objects.requireNonNull(mobility, "mobility");
            if (role != SystemAtomMapping.ComponentRole.PROTEIN_SIDE_CHAIN
                    && role != SystemAtomMapping.ComponentRole.PROTEIN_BACKBONE) {
                throw new IllegalArgumentException("protein role required");
            }
            if (mobility == AtomSelectionRules.Mobility.UNRESTRAINED
                    || (role == SystemAtomMapping.ComponentRole.PROTEIN_BACKBONE
                    && mobility != AtomSelectionRules.Mobility.FIXED)) {
                throw new IllegalArgumentException("protein mask requires fixed backbone and explicit local mobility");
            }
        }

        boolean matches(SystemAtomMapping.Entry entry) {
            var atom = entry.gaiaAtom();
            return entry.componentRole() == role && atom.chainId().equals(chainId)
                    && atom.residueNumber() == residueNumber && atom.insertionCode() == insertionCode
                    && (atomNames.isEmpty() || atomNames.contains(atom.atomName()));
        }

        String canonical() {
            return chainId + "|" + residueNumber + "|" + (int) insertionCode + "|" + role
                    + "|" + String.join(",", atomNames.stream().sorted().toList()) + "|" + mobility;
        }
    }

    String canonical() {
        return "PROTEIN_DEFINED_MASK_V1\n" + provenance + "\nDEFAULT=FIXED\n"
                + String.join("\n", rules.stream().map(Rule::canonical)
                .sorted(Comparator.naturalOrder()).toList());
    }

    private static String token(String value) {
        Objects.requireNonNull(value, "mask text");
        if (value.isBlank() || value.contains("\n") || value.contains("\r")
                || value.contains("|") || value.contains(",")) {
            throw new IllegalArgumentException("invalid mask token");
        }
        return value;
    }
}
